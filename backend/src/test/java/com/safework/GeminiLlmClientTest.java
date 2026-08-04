package com.safework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.llm.GeminiLlmClient;
import com.safework.llm.LlmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Gemini 응답 파싱.
 *
 * 실제 호출은 키·비용·네트워크에 묶이므로 서버를 흉내 내서 확인한다.
 * 여기서 지키는 건 "이상한 응답이 와도 서비스가 죽지 않는다" 하나다.
 */
@DisplayName("Gemini 클라이언트")
class GeminiLlmClientTest {

    private static final String ANSWER_JSON = """
            {
              "candidates": [
                { "content": { "parts": [ { "text": "안전난간을 설치하셔야 합니다." } ] } }
              ],
              "usageMetadata": { "totalTokenCount": 321 }
            }
            """;

    private static final String URL =
            "https://llm.test/v1beta/models/gemini-flash-lite-latest:generateContent";

    private MockRestServiceServer server;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        // 타임아웃 설정은 LlmClientConfig 가 하고, 여기서는 가짜 서버에 연결된
        // RestClient 를 넣어 응답 파싱만 확인한다.
        RestClient.Builder builder = RestClient.builder().baseUrl("https://llm.test");
        server = MockRestServiceServer.bindTo(builder).build();

        LlmProperties properties = new LlmProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://llm.test");
        properties.setModel("gemini-flash-lite-latest");

        client = new GeminiLlmClient(properties, builder.build(), new ObjectMapper());
    }

    private void expectResponse(MediaType contentType, String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, contentType));
    }

    @Test
    @DisplayName("정상 JSON 응답에서 답변과 토큰 사용량을 뽑는다")
    void parsesJsonResponse() {
        expectResponse(MediaType.APPLICATION_JSON, ANSWER_JSON);

        var answer = client.generate("system", "user");

        assertThat(answer).isPresent();
        assertThat(answer.get().content()).contains("안전난간");
        assertThat(answer.get().tokenUsage()).isEqualTo(321);
    }

    @Test
    @DisplayName("Content-Type 이 octet-stream 으로 와도 한글이 깨지지 않고 읽힌다")
    void parsesResponseWithUnexpectedContentType() {
        // 실제로 겪은 상황이다. Map 으로 바로 받으면 변환기를 못 찾아 통째로 실패했고,
        // String 으로 받으면 charset 이 없어 ISO-8859-1 로 읽혀 한글이 깨졌다.
        expectResponse(MediaType.APPLICATION_OCTET_STREAM, ANSWER_JSON);

        var answer = client.generate("system", "user");

        assertThat(answer).isPresent();
        assertThat(answer.get().content()).contains("안전난간");
    }

    @Test
    @DisplayName("안전 필터에 걸리면 재시도하지 않는다")
    void emptyWhenBlockedBySafetyFilter() {
        // 모델이 정상 응답으로 "답을 못 준다"고 한 것이므로 다시 걸어도 같다.
        // 전송 실패와 달리 여기서 재시도하면 호출만 낭비한다.
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("{\"candidates\":[],\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.generate("system", "user")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("서버 오류나 깨진 응답에도 예외를 올리지 않는다")
    void degradesOnFailure() {
        // 5xx 는 전송 실패로 보고 재시도까지 두 번 부른다.
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withServerError());

        // 답변이 없어도 검색 결과는 돌려줄 수 있으므로 예외 대신 빈 결과를 준다.
        assertThat(client.generate("system", "user")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("한 번 실패해도 다시 불러서 답변을 받아 낸다")
    void retriesOnceOnTransientFailure() {
        // 응답이 간헐적으로 끊기는 경우가 있다. "AI 답변 없음"이 제일 나쁘다.
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(ANSWER_JSON, MediaType.APPLICATION_JSON));

        var answer = client.generate("system", "user");

        assertThat(answer).isPresent();
        assertThat(answer.get().content()).contains("안전난간");
        server.verify();
    }

    @Test
    @DisplayName("쿼터가 떨어지면(429) 재시도하지 않는다")
    void doesNotRetryOnQuotaExhausted() {
        // 무료 티어는 모델별 하루 호출 수가 정해져 있다.
        // 여기서 다시 걸면 남은 하루치만 더 태운다(실제로 성공률이 떨어졌다).
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withTooManyRequests());

        assertThat(client.generate("system", "user")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 호출조차 하지 않는다")
    void skipsWithoutApiKey() {
        LlmProperties noKey = new LlmProperties();
        noKey.setBaseUrl("https://llm.test");
        noKey.setModel("gemini-flash-lite-latest");
        var offline = new GeminiLlmClient(noKey, RestClient.builder().build(), new ObjectMapper());

        assertThat(offline.available()).isFalse();
        assertThat(offline.generate("system", "user")).isEmpty();
    }
}
