package com.safework.ml.client;

import com.safework.ml.config.MlServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

import com.safework.checklist.repository.ChecklistResponseRepository;
import com.safework.workplace.entity.Workplace;

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

    private final ObjectMapper objectMapper;

    public MlServerClient(MlServerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

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

    /**
     * LightGBM 위험유형 예측.
     * 점수(risk_score 등)는 DB 함수가 정본이라 여기서 받지 않는다 — ML 서버도 2026-08-03 에
     * 콜드스타트 계산을 걷어내고 예측만 반환하도록 정리됐다.
     */
    public record RiskPrediction(List<Map<String, Object>> topRisks,
                                 List<Map<String, Object>> severityPrediction,
                                 String modelVersion) {
    }

    public Optional<RiskPrediction> predictRisk(String industry, String subIndustry,
                                                String sizeClass, String region) {
        return call("/predict/risk",
                Map.of("industry", industry,
                        "sub_industry", subIndustry == null ? "" : subIndustry,
                        "size_class", sizeClass,
                        "region", region),
                new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(MlServerClient::toRiskPrediction);
    }

    /** 현장 객관정보와 체크리스트 미비 신호를 함께 전달하는 진단용 예측. */
    public Optional<RiskPrediction> predictRisk(
            Workplace workplace,
            List<ChecklistResponseRepository.RiskSignal> riskSignals) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("industry", workplace.getIndustry());
        body.put("sub_industry", workplace.getSubIndustry() == null ? "" : workplace.getSubIndustry());
        body.put("size_class", workplace.getSizeClass());
        body.put("region", workplace.getRegion());
        putIfPresent(body, "machine_type", workplace.getMachineType());
        putIfPresent(body, "machine_count", workplace.getMachineCount());
        putIfPresent(body, "safety_device_status", workplace.getSafetyDeviceStatus());
        putIfPresent(body, "storage_location", workplace.getStorageLocation());
        putIfPresent(body, "storage_method", workplace.getStorageMethod());
        body.put("risk_signals", riskSignals.stream().map(signal -> Map.of(
                "category", signal.category(),
                "weight", signal.weight(),
                "deficient_count", signal.deficientCount())).toList());

        return call("/predict/risk", body,
                new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(MlServerClient::toRiskPrediction);
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

    public Optional<CaseSearchResult> analyzeCases(String industry, String subIndustry,
                                                   String diagnosisContext, int topN) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("industry", industry);
        body.put("sub_industry", subIndustry == null ? "" : subIndustry);
        body.put("top_n", topN);
        putIfPresent(body, "query_context", diagnosisContext);
        return call("/analyze/cases", body,
                new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(MlServerClient::toCaseResult);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }

    private <T> Optional<T> call(String path, Object body, ParameterizedTypeReference<T> type) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            // 본문을 바이트로 받아 UTF-8 로 직접 읽는다.
            // Map 으로 바로 받으면 응답 Content-Type 이 조금만 달라도(octet-stream 등)
            // 변환기를 못 찾아 통째로 실패한다. 실제로 /predict/risk 가 그렇게 깨졌다.
            byte[] raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);

            if (raw == null || raw.length == 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(new String(raw, StandardCharsets.UTF_8),
                    objectMapper.getTypeFactory().constructType(type.getType())));

        } catch (Exception e) {
            // ML 서버가 꺼져 있거나 인덱스를 만드는 중일 수 있다. 서비스는 계속돼야 하므로
            // 실패를 삼키되, 폴백으로 동작 중이라는 사실은 남긴다.
            // 근본 원인까지 남긴다 — RestClient 가 "Error while extracting response ..." 로
            // 감싸 버려서 겉 메시지만으로는 무엇이 문제인지 알 수 없다.
            log.warn("ML 서버 호출 실패({}): {} (원인: {}). 폴백으로 처리합니다.",
                    path, e.getMessage(), rootCauseOf(e));
            return Optional.empty();
        }
    }

    private String rootCauseOf(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
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

    @SuppressWarnings("unchecked")
    private static RiskPrediction toRiskPrediction(Map<String, Object> body) {
        return new RiskPrediction(
                (List<Map<String, Object>>) body.getOrDefault("top_risks", List.of()),
                (List<Map<String, Object>>) body.getOrDefault("severity_prediction", List.of()),
                (String) body.get("model_version"));
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static BigDecimal asDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }
}
