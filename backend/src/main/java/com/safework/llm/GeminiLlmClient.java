package com.safework.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Gemini 로 답변을 생성한다.
 *
 * 키가 없으면 아무것도 하지 않는다(available() == false). 팀이 다른 모델로 옮기기로 하면
 * LlmClient 를 구현한 다른 빈으로 바꾸면 되고, 호출부는 손대지 않아도 된다.
 */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(LlmProperties properties,
                           @Qualifier(LlmClientConfig.GEMINI_REST_CLIENT) RestClient restClient,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean available() {
        return properties.hasApiKey();
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    /**
     * 전송이 실패하면 한 번 더 부른다.
     *
     * 응답이 간헐적으로 끊기는 경우가 있어서, 그때 "AI 답변 없음"이 나오는 것보다
     * 한 번 다시 걸어 보는 편이 낫다. 다만 <b>4xx 는 재시도하지 않는다</b> —
     * 특히 429(쿼터 소진)에 재시도하면 남은 하루치 호출만 더 태운다.
     * (실제로 재시도를 무조건 걸었더니 성공률이 오히려 떨어졌다)
     */
    private static final int MAX_ATTEMPTS = 2;

    @Override
    public Optional<LlmAnswer> generate(String systemPrompt, String userPrompt) {
        if (!available()) {
            return Optional.empty();
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Optional<LlmAnswer> answer = callOnce(systemPrompt, userPrompt, attempt);
            // null 은 "호출 자체가 실패했다" — 다시 걸어 볼 만하다.
            // Optional.empty() 는 "모델이 답을 안 줬다"(안전 필터 등) — 다시 걸어도 같다.
            if (answer != null) {
                return answer;
            }
        }
        return Optional.empty();
    }

    /**
     * 한 번 호출한다.
     *
     * @return 답변, 또는 모델이 답을 주지 않았으면 {@link Optional#empty()}.
     *         호출 자체가 실패했으면 {@code null}(재시도 대상)
     */
    private Optional<LlmAnswer> callOnce(String systemPrompt, String userPrompt, int attempt) {
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt)))));

            // 본문을 바이트로 받아서 UTF-8 로 직접 읽는다.
            //
            // Gemini 가 가끔 Content-Type 을 application/octet-stream 으로 돌려준다.
            // 그때 Map 으로 바로 받으면 변환기를 못 찾아 통째로 실패하고(실제로 겪었다),
            // String 으로 받으면 charset 이 없어 ISO-8859-1 로 읽혀 한글이 깨진다.
            // 바이트는 Content-Type 과 무관하게 읽히므로 여기서만 인코딩을 정한다.
            byte[] raw = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.getModel())
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);

            Map<String, Object> response = raw == null || raw.length == 0
                    ? null
                    : objectMapper.readValue(new String(raw, StandardCharsets.UTF_8),
                            new TypeReference<Map<String, Object>>() {});

            int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            return extractText(response)
                    .map(text -> new LlmAnswer(text, extractTokenUsage(response), latencyMs));

        } catch (HttpClientErrorException e) {
            // 4xx 는 다시 걸어도 같다. 429 면 남은 쿼터만 더 태우므로 바로 포기한다.
            log.warn("LLM 호출 거부 ({}): {}", e.getStatusCode(), firstLine(e.getResponseBodyAsString()));
            return Optional.empty();

        } catch (Exception e) {
            // 답변 생성이 실패해도 검색 결과는 돌려줄 수 있으므로 예외를 올리지 않는다.
            // 근본 원인까지 남긴다. RestClient 가 "Error while extracting response ..." 로
            // 감싸 버려서 겉 메시지만 보면 원인을 알 수 없다.
            log.warn("LLM 답변 생성 실패 ({}/{}): {} (원인: {})",
                    attempt, MAX_ATTEMPTS, e.getMessage(), rootCauseOf(e));
            return null;
        }
    }

    /** 429 본문이 수십 줄이라 로그가 뒤덮인다. 핵심만 남긴다. */
    private String firstLine(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String message = body.replaceAll("\\s+", " ");
        return message.length() <= 300 ? message : message.substring(0, 300) + " ...";
    }

    private String rootCauseOf(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractText(Map<String, Object> response) {
        if (response == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            // 안전 필터에 걸리면 candidates 가 비어서 온다.
            log.warn("LLM 이 답변을 돌려주지 않았습니다: {}", response.get("promptFeedback"));
            return Optional.empty();
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) parts.get(0).get("text"));
    }

    @SuppressWarnings("unchecked")
    private Integer extractTokenUsage(Map<String, Object> response) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usageMetadata");
        if (usage == null) {
            return null;
        }
        Object total = usage.get("totalTokenCount");
        return total instanceof Number number ? number.intValue() : null;
    }
}
