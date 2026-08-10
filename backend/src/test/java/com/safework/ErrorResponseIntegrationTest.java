package com.safework;

import com.safework.support.ApiClient;
import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static com.safework.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 잘못된 요청이 500 이 아니라 400 으로 나가는지 지킨다.
 *
 * 개발 중 같은 실수가 세 번 반복됐다. 검증 실패, enum 허용값 밖의 값, 파라미터 누락이
 * 각각 다른 예외로 올라오는데 핸들러가 없으면 조용히 500 이 된다.
 * 클라이언트 오류인데 서버 오류로 응답하면 프론트가 원인을 알 수 없다.
 */
@DisplayName("에러 응답 규약")
class ErrorResponseIntegrationTest extends IntegrationTest {

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("err-" + System.nanoTime() + "@test.local");
    }

    @Test
    @DisplayName("본문 필드 검증 실패는 400 과 필드별 사유를 준다")
    void bodyValidationFailure() throws Exception {
        // 필수값(industry, sizeClass, region) 누락
        var result = api.postJson("/api/workplaces", token, Map.of("name", "이름만 있음"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        var body = json(result);
        assertThat(body.get("error").asText()).contains("입력값");
        assertThat(body.get("fields").fieldNames()).hasNext();
    }

    @Test
    @DisplayName("answer 에 허용값 밖의 값을 보내면 400")
    void invalidEnumValue() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions",
                token, Map.of("responses",
                        List.of(Map.of("itemCode", "TEST-MFG-0001", "answer", "MAYBE"))));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("요청 본문");
    }

    @Test
    @DisplayName("필수 쿼리 파라미터를 빠뜨리면 400")
    void missingQueryParameter() throws Exception {
        // accidentType 만 주고 industry 를 뺀다
        var result = mockMvc.perform(get("/api/accident-response")
                        .param("accidentType", "끼임")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("industry")).isTrue();
    }

    @Test
    @DisplayName("쿼리 파라미터 제약조건 위반도 400")
    void queryParameterConstraintViolation() throws Exception {
        var blank = mockMvc.perform(get("/api/laws/search")
                        .param("q", "  ")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);

        var tooLarge = mockMvc.perform(get("/api/laws/search")
                        .param("q", "안전난간").param("size", "999")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(tooLarge.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("존재하지 않는 문항 코드는 어느 코드가 문제인지 알려준다")
    void unknownItemCode() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions",
                token, Map.of("responses",
                        List.of(Map.of("itemCode", "NOT-EXIST-9999", "answer", "YES"))));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("NOT-EXIST-9999");
    }

    @Test
    @DisplayName("같은 문항을 두 번 답하면 400")
    void duplicateItemCode() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.postJson("/api/workplaces/" + workplaceId + "/checklist-submissions",
                token, Map.of("responses", List.of(
                        Map.of("itemCode", "TEST-MFG-0001", "answer", "YES"),
                        Map.of("itemCode", "TEST-MFG-0001", "answer", "NO"))));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("중복");
    }

    @Test
    @DisplayName("토큰 없이는 접근할 수 없다")
    void requiresAuthentication() throws Exception {
        var result = mockMvc.perform(get("/api/workplaces")).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    @DisplayName("다른 주소의 프론트가 부를 수 있게 CORS 를 허용한다")
    void allowsConfiguredOrigin() throws Exception {
        // 프론트를 GitHub Pages 등에 올리면 주소가 달라진다. 이게 없으면 브라우저가
        // 로그인부터 막아 버린다(개발 중에는 Vite 프록시 덕에 안 드러난다).
        var preflight = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://pnu-2026-ai-hackathon.github.io")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andReturn();

        assertThat(preflight.getResponse().getStatus()).isEqualTo(200);
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("https://pnu-2026-ai-hackathon.github.io");
        // PDF 다운로드에서 파일 이름을 읽으려면 이 헤더가 노출돼야 한다.
        assertThat(preflight.getResponse().getHeader("Access-Control-Expose-Headers"))
                .contains("Content-Disposition");
    }

    @Test
    @DisplayName("허용하지 않은 주소는 CORS 를 열어주지 않는다")
    void rejectsUnknownOrigin() throws Exception {
        var preflight = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();

        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    @DisplayName("/api/auth/me 도 토큰이 있어야 한다")
    void meRequiresAuthentication() throws Exception {
        // 공개 경로를 /api/auth/** 로 열어 두면 /me 까지 뚫려 principal 이 null 이 된다.
        // register·login 만 열려 있어야 한다.
        var result = mockMvc.perform(get("/api/auth/me")).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    @DisplayName("남의 사업장에는 접근할 수 없다")
    void cannotAccessOthersWorkplace() throws Exception {
        long mine = api.createManufacturingWorkplace(token);

        String otherToken = api.registerAndGetToken("other-" + System.nanoTime() + "@test.local");
        var result = api.getWithToken("/api/workplaces/" + mine, otherToken);

        // 존재 여부를 노출하지 않도록 403 이 아니라 400 으로 막는다.
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("사업장을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("같은 이메일로 두 번 가입할 수 없다")
    void duplicateEmail() throws Exception {
        String email = "dup-" + System.nanoTime() + "@test.local";
        api.registerAndGetToken(email);

        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"test1234\",\"name\":\"중복\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("error").asText()).contains("이미 가입");
    }
}
