package com.safework;

import com.fasterxml.jackson.databind.JsonNode;
import com.safework.support.ApiClient;
import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("flow-" + System.nanoTime() + "@test.local");
    }

    @Test
    @DisplayName("내 정보를 수정하면 바뀐 값이 바로 돌아온다")
    void updatesProfile() throws Exception {
        String email = "prof-" + System.nanoTime() + "@test.local";
        String myToken = api.registerAndGetToken(email);

        var result = api.patchJson("/api/auth/me", myToken,
                Map.of("name", "구현서", "phone", "010-1234-5678"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("name").asText()).isEqualTo("구현서");
        assertThat(body.get("phone").asText()).isEqualTo("010-1234-5678");
        // 이메일은 로그인 아이디라 바뀌면 안 된다.
        assertThat(body.get("email").asText()).isEqualTo(email);

        // 다시 조회해도 남아 있어야 한다(응답만 바꿔 놓고 저장이 안 되는 실수 방지).
        assertThat(json(api.getWithToken("/api/auth/me", myToken)).get("name").asText())
                .isEqualTo("구현서");
    }

    @Test
    @DisplayName("보내지 않은 항목은 그대로 두고, 빈 연락처는 지운다")
    void updatesOnlyGivenFields() throws Exception {
        String myToken = api.registerAndGetToken("part-" + System.nanoTime() + "@test.local");
        api.patchJson("/api/auth/me", myToken, Map.of("name", "구현서", "phone", "010-1111-2222"));

        // 이름만 보낸다 → 연락처는 유지
        api.patchJson("/api/auth/me", myToken, Map.of("name", "곽동헌"));
        JsonNode kept = json(api.getWithToken("/api/auth/me", myToken));
        assertThat(kept.get("name").asText()).isEqualTo("곽동헌");
        assertThat(kept.get("phone").asText()).isEqualTo("010-1111-2222");

        // 빈 문자열을 보낸다 → 연락처 삭제
        api.patchJson("/api/auth/me", myToken, Map.of("phone", ""));
        assertThat(json(api.getWithToken("/api/auth/me", myToken)).get("phone").isNull()).isTrue();
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로만 로그인된다")
    void changesPassword() throws Exception {
        String email = "pw-" + System.nanoTime() + "@test.local";
        String myToken = api.registerAndGetToken(email);   // 가입 비밀번호는 test1234

        var changed = api.putJson("/api/auth/me/password", myToken,
                Map.of("currentPassword", "test1234", "newPassword", "NewPass1234!"));
        assertThat(changed.getResponse().getStatus()).isEqualTo(204);

        assertThat(api.login(email, "NewPass1234!").getResponse().getStatus()).isEqualTo(200);
        // 예전 비밀번호로는 못 들어가야 한다.
        assertThat(api.login(email, "test1234").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 바꾸지 않는다")
    void rejectsWrongCurrentPassword() throws Exception {
        String email = "pw2-" + System.nanoTime() + "@test.local";
        String myToken = api.registerAndGetToken(email);

        var result = api.putJson("/api/auth/me/password", myToken,
                Map.of("currentPassword", "틀린비밀번호", "newPassword", "NewPass1234!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("현재 비밀번호");
        // 원래 비밀번호가 그대로 살아 있어야 한다.
        assertThat(api.login(email, "test1234").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("새 비밀번호가 너무 짧으면 400")
    void rejectsShortPassword() throws Exception {
        String myToken = api.registerAndGetToken("pw3-" + System.nanoTime() + "@test.local");

        var result = api.putJson("/api/auth/me/password", myToken,
                Map.of("currentPassword", "test1234", "newPassword", "짧음"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("newPassword")).isTrue();
    }

    @Test
    @DisplayName("점수·등급이 NULL 이어도 조회와 PDF 가 죽지 않는다")
    void handlesNullScoreAndGrade() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);
        JsonNode items = json(api.getWithToken(
                "/api/workplaces/" + workplaceId + "/checklist-items", token));
        api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions", token,
                Map.of("responses", List.of(
                        Map.of("itemCode", items.get(0).get("itemCode").asText(), "answer", "NO"))));

        // SCHEMA_8 이 두 컬럼의 NOT NULL 을 풀었다(베이스라인 매칭이 NONE 인 경우).
        // 지금 데이터로는 NONE 이 안 생기지만, 생겼을 때 화면과 PDF 가 500 으로
        // 죽지 않는지는 확인해 둬야 한다.
        jdbcTemplate.update("UPDATE risk_assessment SET risk_score = NULL, risk_grade = NULL "
                + "WHERE workplace_id = ?", workplaceId);

        var latest = api.getWithToken(
                "/api/workplaces/" + workplaceId + "/risk-assessments/latest", token);
        assertThat(latest.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(latest);
        assertThat(body.get("riskScore").isNull()).isTrue();
        assertThat(body.get("riskGrade").isNull()).isTrue();
        // 점수가 없어도 나머지 정보는 그대로 나와야 한다.
        assertThat(body.get("assessmentId").asLong()).isPositive();

        var created = api.postJson("/api/workplaces/" + workplaceId + "/reports", token, Map.of());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(created).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("내 정보 조회는 토큰의 주인을 알려주고 비밀번호는 담지 않는다")
    void meReturnsOwnerOfToken() throws Exception {
        String email = "me-" + System.nanoTime() + "@test.local";
        String myToken = api.registerAndGetToken(email);

        var result = api.getWithToken("/api/auth/me", myToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("name").asText()).isEqualTo("테스트");
        assertThat(body.get("userId").asLong()).isPositive();
        assertThat(body.get("role").asText()).isEqualTo("OWNER");
        // 해시라도 내보내면 안 된다.
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
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
