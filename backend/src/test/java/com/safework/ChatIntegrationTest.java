package com.safework;

import com.fasterxml.jackson.databind.JsonNode;
import com.safework.chat.dto.ChatDtos;
import com.safework.chat.service.LawPromptBuilder;
import com.safework.law.dto.LawSearchResponse;
import com.safework.llm.LlmClient;
import com.safework.support.ApiClient;
import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.safework.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 법령 상담(RAG) 검증.
 *
 * 실제 LLM 을 부르면 결과가 매번 다르고 키·비용·네트워크에 묶이므로,
 * 프롬프트에 무엇이 들어가는지와 응답 구조를 가짜 LLM 으로 확인한다.
 * 키가 없을 때의 동작(조문만 반환)도 함께 지킨다.
 */
@DisplayName("법령 상담")
@Import(ChatIntegrationTest.StubLlmConfig.class)
class ChatIntegrationTest extends IntegrationTest {

    /** 마지막 호출의 프롬프트를 붙잡아 두어 무엇을 근거로 물었는지 확인한다. */
    static final AtomicReference<String> LAST_USER_PROMPT = new AtomicReference<>();
    static final AtomicReference<Boolean> ENABLED = new AtomicReference<>(false);

    @TestConfiguration
    static class StubLlmConfig {
        @Bean
        @Primary
        LlmClient stubLlmClient() {
            return new LlmClient() {
                @Override
                public boolean available() {
                    return ENABLED.get();
                }

                @Override
                public String modelName() {
                    return "stub-model";
                }

                @Override
                public Optional<LlmAnswer> generate(String systemPrompt, String userPrompt) {
                    LAST_USER_PROMPT.set(userPrompt);
                    if (!ENABLED.get()) {
                        return Optional.empty();
                    }
                    return Optional.of(new LlmAnswer(
                            "안전난간을 설치하셔야 합니다. (산업안전보건기준에 관한 규칙 제42조)", 123, 45));
                }
            };
        }
    }

    @Autowired
    private LawPromptBuilder promptBuilder;

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("chat-" + System.nanoTime() + "@test.local");
        ENABLED.set(false);
        LAST_USER_PROMPT.set(null);
    }

    private String createSession() throws Exception {
        var result = api.postJson("/api/chat/sessions", token, Map.of());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return json(result).get("sessionId").asText();
    }

    @Test
    @DisplayName("모델이 없으면 답변 대신 관련 조문만 돌려준다")
    void withoutLlmReturnsCitationsOnly() throws Exception {
        String sessionId = createSession();

        var result = api.postJson("/api/chat/sessions/" + sessionId + "/messages", token,
                Map.of("question", "사다리에서 떨어질 것 같은데 뭘 해야 하나요?"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);

        assertThat(body.get("mode").asText()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(body.get("answer").isNull()).isTrue();
        assertThat(body.get("note").asText()).isNotBlank();
        // 답변을 못 만들어도 근거 조문은 나와야 한다. 이게 없으면 화면이 빈다.
        assertThat(body.get("citedArticles")).isNotEmpty();
    }

    @Test
    @DisplayName("모델이 있으면 조문을 근거로 답변을 만들고 이력에 남긴다")
    void withLlmGeneratesAnswer() throws Exception {
        ENABLED.set(true);
        String sessionId = createSession();

        var result = api.postJson("/api/chat/sessions/" + sessionId + "/messages", token,
                Map.of("question", "사다리에서 떨어질 것 같은데 뭘 해야 하나요?"));

        JsonNode body = json(result);
        assertThat(body.get("mode").asText()).isEqualTo("GENERATED");
        assertThat(body.get("answer").asText()).contains("안전난간");
        assertThat(body.get("modelName").asText()).isEqualTo("stub-model");

        // 질문과 답변이 모두 이력에 남고, 답변에는 근거 조문이 붙어야 한다.
        JsonNode messages = json(api.getWithToken(
                "/api/chat/sessions/" + sessionId + "/messages", token));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("USER");

        JsonNode answer = messages.get(1);
        assertThat(answer.get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(answer.get("citedArticles")).isNotEmpty();
        assertThat(answer.get("modelName").asText()).isEqualTo("stub-model");
    }

    @Test
    @DisplayName("프롬프트에 검색된 조문이 실제로 들어간다")
    void promptCarriesRetrievedArticles() throws Exception {
        ENABLED.set(true);
        String sessionId = createSession();

        api.postJson("/api/chat/sessions/" + sessionId + "/messages", token,
                Map.of("question", "사다리에서 떨어질 것 같은데 뭘 해야 하나요?"));

        String prompt = LAST_USER_PROMPT.get();
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("[질문]").contains("[참고 조문]");
        // 근거 없이 지어내지 않도록, 검색된 조문 본문이 프롬프트에 담겨야 한다.
        assertThat(prompt).contains("추락");
    }

    @Test
    @DisplayName("시스템 프롬프트가 지어내지 말라고 못 박는다")
    void systemPromptForbidsHallucination() {
        String system = promptBuilder.systemPrompt();

        assertThat(system).contains("지어내지").contains("근거");
        // 법률 자문이 아님을 밝히도록 지시해야 한다.
        assertThat(system).contains("법률 자문");
    }

    @Test
    @DisplayName("조문을 못 찾았을 때도 프롬프트가 만들어진다")
    void promptWithoutArticles() {
        String prompt = promptBuilder.userPrompt("질문", List.<LawSearchResponse.LawArticleDto>of());

        assertThat(prompt).contains("검색된 조문이 없습니다");
    }

    @Test
    @DisplayName("남의 대화는 볼 수 없다")
    void cannotAccessOthersSession() throws Exception {
        String sessionId = createSession();

        String otherToken = api.registerAndGetToken("chat-other-" + System.nanoTime() + "@test.local");
        var result = api.getWithToken("/api/chat/sessions/" + sessionId + "/messages", otherToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("대화를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("빈 질문은 400")
    void blankQuestion() throws Exception {
        String sessionId = createSession();

        var result = api.postJson("/api/chat/sessions/" + sessionId + "/messages", token,
                Map.of("question", "   "));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("question")).isTrue();
    }

    @Test
    @DisplayName("답변 모드는 두 가지뿐이다")
    void answerModes() {
        assertThat(ChatDtos.AnswerMode.values())
                .containsExactly(ChatDtos.AnswerMode.GENERATED, ChatDtos.AnswerMode.RETRIEVAL_ONLY);
    }
}
