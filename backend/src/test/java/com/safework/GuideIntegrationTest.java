package com.safework;

import com.fasterxml.jackson.databind.JsonNode;
import com.safework.support.ApiClient;
import com.safework.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.safework.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 함수를 감싸는 조회 API 들. 함수의 반환 형태가 바뀌면 여기서 잡힌다.
 * 실제로 개발 중 필드명(isCritical), 빈 체크리스트, 어휘 불일치 문제가 이 지점에서 생겼다.
 */
@DisplayName("가이드·검색 API")
class GuideIntegrationTest extends IntegrationTest {

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("guide-" + System.nanoTime() + "@test.local");
    }

    @Test
    @DisplayName("예방 가이드는 사고유형별로 묶인 체크리스트를 준다")
    void preventionGuide() throws Exception {
        var result = api.getWithParams("/api/prevention-guide", token, Map.of(
                "industry", "제조업", "sizeClass", "5인 미만", "region", "부산"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode predictions = json(result).get("predictions");
        assertThat(predictions).isNotEmpty();

        JsonNode top = predictions.get(0);
        assertThat(top.get("rank").asInt()).isEqualTo(1);
        // 픽스처에서 끼임을 가장 많이 넣었다.
        assertThat(top.get("accidentType").asText()).isEqualTo("끼임");
        assertThat(top.get("ratio").asDouble()).isBetween(0.0, 1.0);

        JsonNode item = top.get("checklist").get(0);
        // 프론트와 약속한 필드명. isCritical 이 critical 로 나가던 버그의 회귀 방지.
        assertThat(item.has("isCritical")).isTrue();
        assertThat(item.has("critical")).isFalse();
        assertThat(item.get("lawBasis").isArray()).isTrue();
    }

    @Test
    @DisplayName("점검 항목이 없는 사고유형도 rank 를 유지하며 빈 배열로 온다")
    void preventionGuideKeepsAccidentWithoutChecklist() throws Exception {
        var result = api.getWithParams("/api/prevention-guide", token, Map.of(
                "industry", "건설업", "sizeClass", "5인 미만", "region", "부산",
                "expectedAccidentCount", "3"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        for (JsonNode prediction : json(result).get("predictions")) {
            // 항목이 없어도 null 로 채운 가짜 항목이 섞이면 안 된다.
            for (JsonNode item : prediction.get("checklist")) {
                assertThat(item.get("itemCode").isNull()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("ML 서버가 없으면 키워드 검색으로 내려가 일상어를 법률용어로 넓혀 찾는다")
    void lawSearchFallsBackToKeyword() throws Exception {
        var result = api.getWithParams("/api/laws/search", token,
                Map.of("q", "사다리에서 떨어질 것 같아요"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);

        // ML 서버를 못 쓰면 키워드 검색만으로 응답해야 한다(서비스가 죽으면 안 된다).
        assertThat(body.get("mode").asText()).isEqualTo("KEYWORD");
        // '떨어' 를 '추락' 으로 확장하지 않으면 조문을 못 찾는다.
        assertThat(body.get("searchTerms").toString()).contains("추락");
        assertThat(body.get("results")).isNotEmpty();
        assertThat(body.get("results").get(0).get("title").asText()).contains("추락");
        assertThat(body.get("results").get(0).get("source").asText()).isEqualTo("KEYWORD");
    }

    @Test
    @DisplayName("키워드 검색은 맞는 조문이 없으면 빈 배열로 답한다")
    void lawSearchWithNoMatch() throws Exception {
        var result = api.getWithParams("/api/laws/search", token,
                Map.of("q", "존재하지않는단어zzz"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("totalCount").asInt()).isZero();
    }

    @Test
    @DisplayName("ML 서버가 없으면 유사 재해사례는 사유와 함께 빈 결과를 준다")
    void similarCasesUnavailableWithoutMlServer() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        var result = api.getWithToken("/api/workplaces/" + workplaceId + "/similar-cases", token);

        // ML 서버가 없다고 500 이 나가면 안 된다. 화면은 계속 그려져야 한다.
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("cases")).isEmpty();
        assertThat(body.get("note").asText()).isNotBlank();
    }

    @Test
    @DisplayName("사고 대처 가이드는 법적 근거가 붙은 조치 절차를 준다")
    void accidentResponseGuide() throws Exception {
        var result = api.getWithParams("/api/accident-response", token,
                Map.of("accidentType", "떨어짐", "industry", "건설업"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);

        // 법률 자문이 아니라는 안내는 항상 함께 나가야 한다.
        assertThat(body.get("disclaimer").asText()).isNotBlank();

        JsonNode actions = body.get("actions");
        assertThat(actions).isNotEmpty();
        assertThat(actions.get(0).get("legalBasis").asText()).contains("제54조");

        // 어휘 매핑(떨어짐 → 추락)이 동작해야 사례가 붙는다.
        assertThat(body.get("similarCases")).isNotEmpty();
        assertThat(body.get("similarCases").get(0).get("countermeasures").isArray()).isTrue();
    }

    @Test
    @DisplayName("제조업도 유사 사례가 나온다 (2026-08-04 덤프에서 재해유형이 채워짐)")
    void accidentResponseFindsManufacturingCases() throws Exception {
        // 오래 비어 있던 제조업등 2,573건의 accident_kind 가 모두 채워졌다.
        // 업종 어휘(제조업 → 제조업등) 매핑이 살아 있어야 사례가 붙는다.
        var result = api.getWithParams("/api/accident-response", token,
                Map.of("accidentType", "끼임", "industry", "제조업"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("similarCases")).isNotEmpty();
        assertThat(body.get("similarCaseNote").isNull()).isTrue();
    }

    @Test
    @DisplayName("사례가 없는 업종은 그 사유를 함께 알려준다")
    void accidentResponseExplainsMissingCases() throws Exception {
        // sif_case 에는 건설업·제조업등 두 업종만 있다.
        var result = api.getWithParams("/api/accident-response", token,
                Map.of("accidentType", "끼임", "industry", "도소매업"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("similarCases")).isEmpty();
        assertThat(body.get("similarCaseNote").asText()).contains("건설업·제조업");
        // 사례가 없어도 조치 절차는 제공돼야 한다.
        assertThat(body.get("actions")).isNotEmpty();
    }

    @Test
    @DisplayName("코드값 API 가 셀렉트박스에 필요한 값을 한 번에 준다")
    void referencesReturnAllCodeValues() throws Exception {
        var result = api.getWithToken("/api/references", token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);

        // 프론트가 값을 하드코딩하지 않도록 DB 의 v_ref_* 뷰를 그대로 내보낸다.
        assertThat(body.get("industries")).isNotEmpty();
        assertThat(body.get("sizeClasses")).isNotEmpty();
        assertThat(body.get("regions")).isNotEmpty();
        assertThat(body.get("accidentTypes")).isNotEmpty();

        // 사업장 등록에 쓰는 값이라, 실제로 등록에 통과하는 코드여야 한다.
        assertThat(body.get("industries").toString()).contains("제조업");
        assertThat(body.get("accidentTypes").toString()).contains("끼임");

        JsonNode sizeClass = body.get("sizeClasses").get(0);
        assertThat(sizeClass.has("code")).isTrue();
        // ML 모델은 규모 9종을 쓰는데 DB 는 10종이라 매핑값을 함께 준다.
        assertThat(sizeClass.has("modelSizeClass")).isTrue();
        // 셀렉트박스 순서를 프론트가 다시 정하지 않아도 되게 정렬해서 준다.
        assertThat(sizeClass.has("sortOrder")).isTrue();
    }

    @Test
    @DisplayName("점검 문항은 업종에 맞는 것만, 필터도 동작한다")
    void checklistItemsFiltered() throws Exception {
        long workplaceId = api.createManufacturingWorkplace(token);

        JsonNode all = json(api.getWithToken(
                "/api/workplaces/" + workplaceId + "/checklist-items", token));
        JsonNode criticalOnly = json(api.getWithToken(
                "/api/workplaces/" + workplaceId + "/checklist-items?criticalOnly=true", token));

        assertThat(all).isNotEmpty();
        assertThat(criticalOnly.size()).isLessThanOrEqualTo(all.size());
        for (JsonNode item : criticalOnly) {
            assertThat(item.get("isCritical").asBoolean()).isTrue();
        }
    }
}
