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
    @DisplayName("법령 검색은 일상어를 법률용어로 넓혀 찾는다")
    void lawSearchExpandsColloquialTerms() throws Exception {
        var result = api.getWithParams("/api/laws/search", token,
                Map.of("q", "사다리에서 떨어질 것 같아요"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);

        // '떨어' 를 '추락' 으로 확장하지 않으면 조문을 못 찾는다.
        assertThat(body.get("searchTerms").toString()).contains("추락");
        assertThat(body.get("results")).isNotEmpty();
        assertThat(body.get("results").get(0).get("title").asText()).contains("추락");
    }

    @Test
    @DisplayName("검색 결과가 없어도 200 과 빈 배열로 답한다")
    void lawSearchWithNoMatch() throws Exception {
        var result = api.getWithParams("/api/laws/search", token,
                Map.of("q", "존재하지않는단어zzz"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("totalCount").asInt()).isZero();
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
    @DisplayName("사례가 없으면 그 사유를 함께 알려준다")
    void accidentResponseExplainsMissingCases() throws Exception {
        // 픽스처에 제조업 SIF 사례를 넣지 않았다(실제 DB 도 분류가 비어 있음).
        var result = api.getWithParams("/api/accident-response", token,
                Map.of("accidentType", "끼임", "industry", "제조업"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.get("similarCases")).isEmpty();
        assertThat(body.get("similarCaseNote").asText()).isNotBlank();
        // 사례가 없어도 조치 절차와 근거 법령은 제공돼야 한다.
        assertThat(body.get("actions")).isNotEmpty();
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
