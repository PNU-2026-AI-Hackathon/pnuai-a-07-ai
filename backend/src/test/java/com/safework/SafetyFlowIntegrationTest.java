package com.safework;

import com.fasterxml.jackson.databind.JsonNode;
import com.safework.support.ApiClient;
import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.safework.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서비스의 핵심 흐름을 실제 DB 위에서 확인한다.
 * 사업장 등록 → 점검 문항 조회 → 체크리스트 제출 → 위험도 진단 → PDF 리포트
 */
@DisplayName("안전관리 핵심 흐름")
class SafetyFlowIntegrationTest extends IntegrationTest {

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("flow-" + System.nanoTime() + "@test.local");
    }

    @Test
    @DisplayName("체크리스트를 제출하면 위험도 진단까지 한 번에 끝난다")
    void submitChecklistThenAssessRisk() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        JsonNode items = json(api.getWithToken(
                "/api/workplaces/" + workplaceId + "/checklist-items", token));
        assertThat(items).isNotEmpty();

        // 일부러 미비(NO)로 답해 체크리스트 점수가 붙는지 본다.
        List<Map<String, String>> responses = List.of(
                Map.of("itemCode", items.get(0).get("itemCode").asText(), "answer", "NO"),
                Map.of("itemCode", items.get(1).get("itemCode").asText(), "answer", "YES"),
                Map.of("itemCode", items.get(2).get("itemCode").asText(), "answer", "NA"));

        var result = api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions",
                token, Map.of("responses", responses));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode body = json(result);
        assertThat(body.get("totalItems").asInt()).isEqualTo(3);
        // NA 는 답변 수에서 빠진다.
        assertThat(body.get("answeredItems").asInt()).isEqualTo(2);

        JsonNode risk = body.get("riskAssessment");
        assertThat(risk.get("riskScore").asDouble()).isBetween(0.0, 100.0);
        assertThat(risk.get("riskGrade").asText()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(risk.get("method").asText()).isEqualTo("COLDSTART");
        // 진단이 어느 제출에서 나왔는지 추적 가능해야 한다(SCHEMA_21/22 회귀 방지).
        assertThat(risk.get("submissionId").asLong()).isEqualTo(body.get("submissionId").asLong());
        assertThat(risk.get("baseComponent").isNull()).isFalse();
        assertThat(risk.get("checklistComponent").isNull()).isFalse();
    }

    @Test
    @DisplayName("제출 전에는 위험도 조회가 안내와 함께 거절된다")
    void riskAssessmentRequiresSubmission() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.getWithToken(
                "/api/workplaces/" + workplaceId + "/risk-assessments/latest", token);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("체크리스트를 먼저 제출");
    }

    @Test
    @DisplayName("진단 결과로 PDF 리포트를 만들고 내려받을 수 있다")
    void generateAndDownloadReport() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);
        JsonNode items = json(api.getWithToken(
                "/api/workplaces/" + workplaceId + "/checklist-items", token));

        api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions", token,
                Map.of("responses", List.of(
                        Map.of("itemCode", items.get(0).get("itemCode").asText(), "answer", "NO"))));

        var created = api.postJson("/api/workplaces/" + workplaceId + "/reports", token, Map.of());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);

        JsonNode report = json(created);
        assertThat(report.get("status").asText()).isEqualTo("DONE");
        assertThat(report.get("fileSize").asInt()).isPositive();

        var download = api.getWithToken(
                "/api/reports/" + report.get("reportId").asLong() + "/download", token);
        assertThat(download.getResponse().getStatus()).isEqualTo(200);

        byte[] pdf = download.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("리포트는 진단이 없으면 만들 수 없다")
    void reportRequiresAssessment() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.postJson("/api/workplaces/" + workplaceId + "/reports", token, Map.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("체크리스트를 먼저 제출");
    }
}
