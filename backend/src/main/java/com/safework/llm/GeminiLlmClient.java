package com.safework.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public GeminiLlmClient(LlmProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean available() {
        return properties.hasApiKey();
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public Optional<LlmAnswer> generate(String systemPrompt, String userPrompt) {
        if (!available()) {
            return Optional.empty();
        }

        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userPrompt)))));

            Map<String, Object> response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.getModel())
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            return extractText(response)
                    .map(text -> new LlmAnswer(text, extractTokenUsage(response), latencyMs));

        } catch (Exception e) {
            // 답변 생성이 실패해도 검색 결과는 돌려줄 수 있으므로 예외를 올리지 않는다.
            log.warn("LLM 답변 생성 실패: {}. 관련 조문만 반환합니다.", e.getMessage());
            return Optional.empty();
        }
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
