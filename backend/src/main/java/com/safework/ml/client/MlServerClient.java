package com.safework.ml.client;

import com.safework.ml.config.MlServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ML 서버(FastAPI) 호출.
 *
 * ML 서버는 로컬 임베딩 모델을 쓰기 때문에 기동 직후 인덱스를 만드는 동안(수 분)
 * 응답하지 못한다. 그 사이에도 서비스는 살아 있어야 하므로, 호출이 실패하면
 * 예외를 던지지 않고 Optional.empty() 를 돌려주어 호출한 쪽이 폴백을 쓰게 한다.
 */
@Slf4j
@Component
public class MlServerClient {

    private final RestClient restClient;
    private final MlServerProperties properties;

    public MlServerClient(MlServerProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** 법령 조문 임베딩 검색 */
    public record LawHit(Long chunkId, Long articleId, String lawName, String articleNo,
                         String title, String content, BigDecimal score) {
    }

    /** 유사 재해사례 임베딩 검색 */
    public record SimilarCase(Long sifId, String summary, String countermeasure, BigDecimal score) {
    }

    public record CaseSearchResult(List<String> topKeywords, List<SimilarCase> similarCases) {
    }

    public Optional<List<LawHit>> searchLaw(String query, int topK) {
        return call("/rag/search-law", Map.of("query", query, "top_k", topK),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(rows -> rows.stream().map(MlServerClient::toLawHit).toList());
    }

    public Optional<CaseSearchResult> analyzeCases(String industry, String subIndustry, int topN) {
        return call("/analyze/cases",
                Map.of("industry", industry,
                        "sub_industry", subIndustry == null ? "" : subIndustry,
                        "top_n", topN),
                new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(MlServerClient::toCaseResult);
    }

    private <T> Optional<T> call(String path, Object body, ParameterizedTypeReference<T> type) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type));
        } catch (Exception e) {
            // ML 서버가 꺼져 있거나 인덱스를 만드는 중일 수 있다. 서비스는 계속돼야 하므로
            // 실패를 삼키되, 폴백으로 동작 중이라는 사실은 남긴다.
            log.warn("ML 서버 호출 실패({}): {}. 폴백으로 처리합니다.", path, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static LawHit toLawHit(Map<String, Object> row) {
        return new LawHit(
                asLong(row.get("chunk_id")),
                asLong(row.get("article_id")),
                (String) row.get("law_name"),
                (String) row.get("article_no"),
                (String) row.get("title"),
                (String) row.get("content"),
                asDecimal(row.get("score")));
    }

    @SuppressWarnings("unchecked")
    private static CaseSearchResult toCaseResult(Map<String, Object> body) {
        List<String> keywords = (List<String>) body.getOrDefault("top_keywords", List.of());
        List<Map<String, Object>> cases =
                (List<Map<String, Object>>) body.getOrDefault("similar_cases", List.of());

        return new CaseSearchResult(keywords, cases.stream()
                .map(row -> new SimilarCase(
                        asLong(row.get("sif_id")),
                        (String) row.get("summary"),
                        (String) row.get("countermeasure"),
                        asDecimal(row.get("score"))))
                .toList());
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static BigDecimal asDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }
}
