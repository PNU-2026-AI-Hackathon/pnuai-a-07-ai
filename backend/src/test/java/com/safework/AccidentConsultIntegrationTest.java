package com.safework;

import com.fasterxml.jackson.databind.JsonNode;
import com.safework.llm.LlmClient;
import com.safework.response.service.AccidentClassifier;
import com.safework.response.service.AccidentConsultPromptBuilder;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.safework.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사고 상황을 글로 적어 대처 방법을 받는 API.
 *
 * 이 화면은 사고 직후에 쓰이므로 "빈 화면"이 제일 나쁘다. 그래서 모델이 없거나
 * 형식을 어겨도 법정 의무 목록과 근거 조문은 반드시 나가야 한다는 점을 집중적으로 지킨다.
 */
@DisplayName("사고 상황 대처 안내")
@Import(AccidentConsultIntegrationTest.StubLlmConfig.class)
class AccidentConsultIntegrationTest extends IntegrationTest {

    static final AtomicReference<String> LAST_USER_PROMPT = new AtomicReference<>();
    static final AtomicReference<String> ANSWER = new AtomicReference<>(null);

    @TestConfiguration
    static class StubLlmConfig {
        @Bean
        @Primary
        LlmClient stubLlmClient() {
            return new LlmClient() {
                @Override
                public boolean available() {
                    return ANSWER.get() != null;
                }

                @Override
                public String modelName() {
                    return "stub-model";
                }

                @Override
                public Optional<LlmAnswer> generate(String systemPrompt, String userPrompt) {
                    LAST_USER_PROMPT.set(userPrompt);
                    String answer = ANSWER.get();
                    return answer == null
                            ? Optional.empty()
                            : Optional.of(new LlmAnswer(answer, 100, 10));
                }
            };
        }
    }

    private static final String WELL_FORMED_ANSWER = """
            [법적의무]
            즉시 작업을 중지하고 근로자를 대피시켜야 합니다. (산업안전보건법 제54조 제1항)

            [행정처리]
            산업재해조사표를 1개월 이내에 제출하세요. (산업안전보건법 시행규칙 제73조 제1항)

            [처벌위험]
            은폐하면 벌칙 대상입니다. (산업안전보건법 제170조 제3호)
            """;

    @Autowired
    private AccidentConsultPromptBuilder promptBuilder;

    @Autowired
    private AccidentClassifier classifier;

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        token = api.registerAndGetToken("consult-" + System.nanoTime() + "@test.local");
        ANSWER.set(null);
        LAST_USER_PROMPT.set(null);
    }

    private JsonNode consult(String situation) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("situation", situation);
        var result = api.postJson("/api/accident-response/consult", token, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json(result);
    }

    @Test
    @DisplayName("모델이 없어도 법정 의무 · 행정 절차 · 처벌 목록은 그대로 나간다")
    void worksWithoutLlm() throws Exception {
        JsonNode body = consult("어제 오후에 직원이 지게차에 다리가 끼여서 병원에 실려 갔습니다.");

        assertThat(body.get("mode").asText()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(body.get("note").asText()).isNotBlank();

        // 설명은 못 만들어도 목록은 비면 안 된다. 이게 이 기능의 핵심이다.
        assertThat(body.get("legalObligations").get("guidance").isNull()).isTrue();
        assertThat(body.get("legalObligations").get("items")).isNotEmpty();
        assertThat(body.get("administrativeSteps").get("items")).isNotEmpty();
        assertThat(body.get("penaltyRisk").get("items")).isNotEmpty();
        assertThat(body.get("immediateActions")).isNotEmpty();
        assertThat(body.get("disclaimer").asText()).isNotBlank();
    }

    @Test
    @DisplayName("서술에서 재해유형을 찾아내고 유사 사례까지 붙인다")
    void inferesAccidentType() throws Exception {
        JsonNode body = consult("작업자가 사다리에서 떨어졌습니다. 추락한 뒤 의식이 없어 구급차로 이송했습니다.");

        assertThat(body.get("accidentType").asText()).isEqualTo("떨어짐");
        // '떨어졌' 과 '추락' 둘 다 걸렸으므로 확정으로 본다.
        assertThat(body.get("accidentTypeCertain").asBoolean()).isTrue();
        assertThat(body.get("severity").get("level").asText()).isEqualTo("SEVERE");
        // 어휘 매핑(떨어짐 → 추락)이 살아 있어야 사례가 붙는다.
        assertThat(body.get("similarCases")).isNotEmpty();
    }

    @Test
    @DisplayName("사고 방식과 부상 결과가 둘 다 나오면 먼저 서술된 사고 방식을 택한다")
    void prefersMechanismOverInjuryResult() throws Exception {
        // '끼여'(사고 방식)와 '절단'(부상 결과)이 한 번씩 걸린다. 사고 자체는 끼임이다.
        JsonNode body = consult("직원이 프레스에 손이 끼여서 손가락이 절단됐습니다.");

        assertThat(body.get("accidentType").asText()).isEqualTo("끼임");
        // 유형이 여럿 걸렸으므로 확정으로 보지 않고 프론트가 확인을 받게 한다.
        assertThat(body.get("accidentTypeCertain").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("사용자가 고른 재해유형이 추정보다 우선한다")
    void explicitTypeWins() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("situation", "직원이 다쳤습니다.");
        body.put("accidentType", "떨어짐");

        JsonNode result = json(api.postJson("/api/accident-response/consult", token, body));

        assertThat(result.get("accidentType").asText()).isEqualTo("떨어짐");
        assertThat(result.get("accidentTypeCertain").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("피해 정도를 모르면 중대재해 기준을 함께 보여주고 안내를 켠다")
    void unknownSeverityStillWarnsAboutSeriousAccident() throws Exception {
        JsonNode severity = consult("기계에 손이 끼었습니다.").get("severity");

        assertThat(severity.get("level").asText()).isEqualTo("UNKNOWN");
        // 모를 때 안내를 끄면 중대재해를 놓친다. 확실히 경미할 때만 끈다.
        assertThat(severity.get("seriousAccidentLikely").asBoolean()).isTrue();
        assertThat(severity.get("criteria")).hasSize(3);
        assertThat(severity.get("criteriaBasis").asText()).contains("시행규칙 제3조");
    }

    @Test
    @DisplayName("경미한 사고면 중대재해처벌법 항목을 빼고 안내한다")
    void minorAccidentDropsSeriousProvisions() throws Exception {
        JsonNode body = consult("직원이 넘어져서 무릎에 찰과상을 입었습니다. 연고만 발랐습니다.");

        assertThat(body.get("severity").get("level").asText()).isEqualTo("MINOR");
        assertThat(body.get("severity").get("seriousAccidentLikely").asBoolean()).isFalse();

        // 중대산업재해가 아닌데 10억원 벌금을 보여주면 과잉 경고가 된다.
        assertThat(body.get("penaltyRisk").get("items").toString()).doesNotContain("중대재해 처벌");
        // 그래도 은폐 금지 같은 공통 의무는 남아야 한다.
        assertThat(body.get("legalObligations").get("items").toString()).contains("은폐");
    }

    @Test
    @DisplayName("사망 표현이 있으면 작업중지 · 보고 의무를 맨 앞에 세운다")
    void fatalAccidentPutsStopWorkFirst() throws Exception {
        JsonNode body = consult("오늘 아침 근로자가 크레인 자재에 깔려 사망했습니다.");

        assertThat(body.get("severity").get("level").asText()).isEqualTo("FATAL");
        JsonNode items = body.get("legalObligations").get("items");
        assertThat(items.get(0).get("legalBasis").asText()).contains("제54조 제1항");
        assertThat(items.get(1).get("deadline").asText()).isEqualTo("지체 없이");
        assertThat(body.get("penaltyRisk").get("items").toString()).contains("10억원");
    }

    @Test
    @DisplayName("행정 절차는 서식 링크 · 담당 기관 · 과태료 금액까지 준다")
    void administrativeStepsCarryFormAndPenalty() throws Exception {
        JsonNode section = consult("직원이 프레스에 손이 끼여 절단됐습니다. 입원했습니다.")
                .get("administrativeSteps");

        String all = section.get("items").toString();
        // 손으로 적어 둔 목록에는 없던 것들이다. admin_procedure 에서 온다.
        assertThat(all).contains("산업재해조사표");
        assertThat(all).contains("발생일부터 1개월 이내");
        assertThat(all).contains("관할 지방고용노동관서");
        assertThat(all).contains("moel.go.kr");          // 서식 다운로드 링크
        assertThat(all).contains("과태료");              // 위반 시 금액

        // 기관이 '-' 인 행은 화면에 그대로 찍히면 안 된다.
        for (JsonNode item : section.get("items")) {
            if (!item.get("agency").isNull()) {
                assertThat(item.get("agency").asText()).isNotEqualTo("-");
            }
        }
    }

    @Test
    @DisplayName("중대재해가 아니면 중대재해 전용 절차는 빠진다")
    void minorAccidentDropsSevereOnlyProcedures() throws Exception {
        String severe = consult("직원이 지게차에 깔려 사망했습니다.")
                .get("administrativeSteps").get("items").toString();
        String minor = consult("직원이 넘어져 무릎에 찰과상을 입었습니다. 연고만 발랐습니다.")
                .get("administrativeSteps").get("items").toString();

        assertThat(severe).contains("작업 중지");
        // 경미한 사고에 "즉시 작업을 중지하라"가 나오면 과잉 안내가 된다.
        assertThat(minor).doesNotContain("작업 중지");
        // 조사표 제출은 중대재해가 아니어도 해당될 수 있으므로 남아야 한다.
        assertThat(minor).contains("산업재해조사표");
    }

    @Test
    @DisplayName("이 사고와 닮은 판례와 신청 가능한 지원사업을 함께 준다")
    void givesPrecedentsAndSupportPrograms() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("situation", "프레스에 손이 끼여서 근로자가 사망했습니다.");
        body.put("industry", "제조업");
        JsonNode result = json(api.postJson("/api/accident-response/consult", token, body));

        JsonNode precedents = result.get("relatedPrecedents");
        assertThat(precedents).isNotEmpty();
        assertThat(precedents.get(0).get("court").asText()).isNotBlank();
        assertThat(precedents.get(0).get("url").asText()).startsWith("http");

        JsonNode programs = result.get("supportPrograms");
        assertThat(programs).isNotEmpty();
        assertThat(programs.get(0).get("agency").asText()).isNotBlank();
        assertThat(programs.get(0).get("url").asText()).startsWith("http");

        // 제목에 '예방' 이 들어가는 농업 지원사업이 공장 사망사고 안내에 섞이면 안 된다.
        // (실제 데이터에 "사과 기상 재해예방"이 있어서 분야로 걸러 낸다)
        assertThat(programs.toString()).doesNotContain("사과");
    }

    @Test
    @DisplayName("프롬프트에 과태료 금액이 들어가 모델이 지어내지 않아도 된다")
    void promptCarriesPenaltyAmounts() throws Exception {
        ANSWER.set(WELL_FORMED_ANSWER);

        consult("직원이 지게차에 깔려 사망했습니다.");

        // 금액은 법령 본문이 아니라 admin_procedure 에만 있다.
        // 프롬프트에 넣어 주지 않으면 모델이 "자료에 없다"고 하거나 지어낸다.
        assertThat(LAST_USER_PROMPT.get()).contains("위반 시:").contains("과태료");
    }

    @Test
    @DisplayName("검색으로는 안 걸리는 보고 · 조사표 조문을 번호로 집어 온다")
    void anchorsReportingArticles() throws Exception {
        JsonNode cited = consult("직원이 프레스에 손이 끼여 절단됐습니다.").get("citedArticles");

        String all = cited.toString();
        // 서술에 '조사표'라는 말이 없어도 이 조문들이 근거로 붙어야 한다.
        assertThat(all).contains("제73조").contains("산업재해조사표");
        assertThat(all).contains("제57조");
        // 번호로 가져온 조문은 검색 결과와 구분되게 표시한다.
        assertThat(all).contains("STATUTE");
    }

    @Test
    @DisplayName("프롬프트에 확인된 의무와 조문이 함께 들어간다")
    void promptCarriesDutiesAndArticles() throws Exception {
        ANSWER.set(WELL_FORMED_ANSWER);

        consult("직원이 지게차에 끼였습니다. 병원에 입원했습니다.");

        String prompt = LAST_USER_PROMPT.get();
        assertThat(prompt).contains("[사고 상황]").contains("[확인된 법정 의무]").contains("[참고 조문]");
        // 모델이 기한을 지어내지 않도록 확인된 기한을 프롬프트에 넣어 준다.
        assertThat(prompt).contains("1개월 이내");
    }

    @Test
    @DisplayName("모델이 있으면 세 덩어리에 상황에 맞는 설명이 채워진다")
    void generatesGuidancePerSection() throws Exception {
        ANSWER.set(WELL_FORMED_ANSWER);

        JsonNode body = consult("직원이 지게차에 끼였습니다. 병원에 입원했습니다.");

        assertThat(body.get("mode").asText()).isEqualTo("GENERATED");
        assertThat(body.get("model").asText()).isEqualTo("stub-model");
        assertThat(body.get("legalObligations").get("guidance").asText()).contains("작업을 중지");
        assertThat(body.get("administrativeSteps").get("guidance").asText()).contains("산업재해조사표");
        assertThat(body.get("penaltyRisk").get("guidance").asText()).contains("은폐");
        // 설명이 붙어도 목록은 그대로 남아야 한다.
        assertThat(body.get("legalObligations").get("items")).isNotEmpty();
    }

    @Test
    @DisplayName("모델이 형식을 어겨도 답변을 버리지 않는다")
    void keepsAnswerWhenFormatIsBroken() throws Exception {
        ANSWER.set("작업을 중지하고 관할 노동관서에 보고하세요. 형식을 지키지 않은 답변입니다.");

        JsonNode body = consult("직원이 지게차에 끼였습니다.");

        assertThat(body.get("mode").asText()).isEqualTo("GENERATED");
        assertThat(body.get("legalObligations").get("guidance").asText()).contains("작업을 중지");
        assertThat(body.get("administrativeSteps").get("guidance").isNull()).isTrue();
    }

    @Test
    @DisplayName("제목만 뽑아내고 본문에서 같은 단어가 나와도 헷갈리지 않는다")
    void parsesHeadingsOnly() {
        Map<String, String> parsed = promptBuilder.parse("""
                [법적의무]
                행정처리 절차보다 먼저 작업을 중지해야 합니다.

                **처벌위험**
                은폐하면 처벌됩니다.
                """);

        assertThat(parsed).containsOnlyKeys(AccidentConsultPromptBuilder.LEGAL,
                AccidentConsultPromptBuilder.PENALTY);
        // 본문에 나온 '행정처리' 를 제목으로 오인해 자르면 안 된다.
        assertThat(parsed.get(AccidentConsultPromptBuilder.LEGAL)).contains("행정처리 절차보다");
    }

    @Test
    @DisplayName("시스템 프롬프트가 금액 · 기한을 지어내지 말라고 못 박는다")
    void systemPromptForbidsInventingAmounts() {
        String system = promptBuilder.systemPrompt();

        assertThat(system).contains("지어내지");
        // 우리 데이터에 산안법 벌칙 형량이 빠져 있어 특히 위험한 지점이다.
        assertThat(system).contains("금액").contains("기한");
    }

    @Test
    @DisplayName("검색어를 못 뽑는 짧은 서술도 500 이 아니라 안내를 준다")
    void shortSituationStillAnswers() throws Exception {
        JsonNode body = consult("사고");

        assertThat(body.get("legalObligations").get("items")).isNotEmpty();
        assertThat(body.get("accidentType").asText()).isEqualTo("기타");
        assertThat(body.get("accidentTypeCertain").asBoolean()).isFalse();
        // 유형이 틀렸을 때 사용자가 고를 수 있어야 한다.
        assertThat(body.get("selectableTypes")).isNotEmpty();
    }

    @Test
    @DisplayName("빈 서술은 400")
    void blankSituation() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("situation", "   ");

        var result = api.postJson("/api/accident-response/consult", token, body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).get("fields").has("situation")).isTrue();
    }

    @Test
    @DisplayName("분류 가능한 재해유형은 코드 마스터 어휘를 그대로 쓴다")
    void classifierUsesCodeVocabulary() {
        assertThat(classifier.knownTypes()).contains("끼임", "떨어짐", "깔림.뒤집힘", "절단베임찔림");
    }
}
