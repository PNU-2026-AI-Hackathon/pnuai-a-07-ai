package com.safework.response.service;

import com.safework.response.dto.AccidentConsultDtos.DutyDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 산업재해가 났을 때 사업주가 지켜야 할 의무·행정 절차·처벌을 조문에서 뽑아 정리한 목록.
 *
 * <p>여기 있는 항목은 전부 우리 DB 의 law_article 본문에서 확인한 것만 담았다.
 * LLM 이 없어도 이 목록은 항상 내려가므로, 키가 없거나 무료 쿼터가 떨어져도
 * 사장님은 최소한 "무엇을 언제 어디에 내야 하는지"를 볼 수 있다.
 * LLM 이 붙으면 이 목록을 근거로 상황에 맞는 설명을 덧붙인다.
 *
 * <p><b>금액을 쓰지 않은 이유</b> — 산업안전보건법 제168조·제170조는 우리 데이터에
 * "1. …에 해당하는 자" 항목만 들어 있고 "5년 이하의 징역 또는 …" 처럼 형량이 적힌
 * 첫 문장이 빠져 있다. 그래서 산안법 벌칙은 <b>어느 조문에 걸리는지</b>만 밝히고
 * 금액은 쓰지 않는다. 중대재해 처벌 등에 관한 법률 제6조·제7조는 본문에 형량이
 * 그대로 있어 그 조문만 액수를 적는다. 데이터가 채워지면 여기도 함께 채우면 된다.
 */
@Component
public class StatutoryDutyCatalog {

    /** 중대재해 판단 기준 (산업안전보건법 시행규칙 제3조) — 서술만으로는 못 정하므로 그대로 보여 준다. */
    public static final List<String> SERIOUS_ACCIDENT_CRITERIA = List.of(
            "사망자가 1명 이상 발생한 재해",
            "3개월 이상의 요양이 필요한 부상자가 동시에 2명 이상 발생한 재해",
            "부상자 또는 직업성 질병자가 동시에 10명 이상 발생한 재해");

    public static final String SERIOUS_ACCIDENT_CRITERIA_BASIS = "산업안전보건법 시행규칙 제3조";

    /** 모든 산업재해에 공통으로 적용되는 의무 */
    private static final List<DutyDto> COMMON_DUTIES = List.of(
            new DutyDto("사고 현장 보존 · 원인조사 협조",
                    "중대재해등이 발생한 현장을 훼손하거나 원인조사를 방해해서는 안 됩니다. "
                            + "구조와 2차 재해 방지에 필요한 경우가 아니라면 현장을 그대로 두고, "
                            + "정리하기 전에 사진과 영상으로 남겨 두세요.",
                    null, "산업안전보건법 제56조 제5항"),

            new DutyDto("산업재해 발생 사실 은폐 금지",
                    "산업재해가 발생한 사실을 숨겨서는 안 됩니다. 숨기도록 시키거나 함께 모의하는 것도 "
                            + "금지되어 있습니다.",
                    null, "산업안전보건법 제57조 제1항"),

            new DutyDto("재해 발생 원인 기록 · 보존",
                    "재해 발생 원인 등을 기록해 보존해야 합니다. 기록에는 사업장 개요와 근로자 인적사항, "
                            + "재해 발생 일시·장소, 발생 원인과 과정, 재발방지 계획이 들어갑니다.",
                    null, "산업안전보건법 제57조 제2항 · 시행규칙 제72조"));

    /** 중대재해로 보이는 경우에만 추가되는 의무 */
    private static final List<DutyDto> SERIOUS_DUTIES = List.of(
            new DutyDto("즉시 작업 중지 · 근로자 대피",
                    "중대재해가 발생했을 때에는 즉시 해당 작업을 중지시키고 근로자를 작업장소에서 "
                            + "대피시키는 등 필요한 안전보건 조치를 해야 합니다.",
                    "즉시", "산업안전보건법 제54조 제1항"),

            new DutyDto("지체 없이 고용노동부 보고",
                    "중대재해가 발생한 사실을 알게 되면 지체 없이 관할 지방고용노동관서에 보고해야 합니다. "
                            + "천재지변 등 부득이한 사유가 있으면 그 사유가 없어진 때부터입니다.",
                    "지체 없이", "산업안전보건법 제54조 제2항"),

            new DutyDto("안전 및 보건 확보의무 (중대재해처벌법)",
                    "사업주 또는 경영책임자는 사업장에서 종사자의 안전·보건상 유해 또는 위험을 방지하기 위한 "
                            + "조치를 해야 합니다. 이 의무를 위반해 중대산업재해에 이르면 형사처벌 대상이 됩니다. "
                            + "적용 대상 여부는 사업장 규모에 따라 다르므로 관할 지방고용노동관서에 확인하세요.",
                    null, "중대재해 처벌 등에 관한 법률 제4조 제1항"));

    /** 사고 이후 밟아야 하는 서류·신고 절차 */
    private static final List<DutyDto> ADMINISTRATIVE_STEPS = List.of(
            new DutyDto("산업재해조사표 작성 · 제출",
                    "사망자가 발생했거나 3일 이상의 휴업이 필요한 부상·질병이 발생한 경우, "
                            + "별지 제30호서식의 산업재해조사표를 작성해 관할 지방고용노동관서에 제출해야 합니다.",
                    "재해가 발생한 날부터 1개월 이내", "산업안전보건법 시행규칙 제73조 제1항"),

            new DutyDto("산업재해조사표에 근로자대표 확인 받기",
                    "산업재해조사표는 근로자대표의 확인을 받아야 합니다. 기재 내용에 근로자대표의 이견이 있으면 "
                            + "그 내용을 첨부합니다. 근로자대표가 없으면 재해자 본인의 확인을 받아 제출할 수 있습니다.",
                    "제출 전", "산업안전보건법 시행규칙 제73조 제3항"),

            new DutyDto("산재보험 요양급여 신청 안내",
                    "재해자가 치료비 등을 받을 수 있도록 근로복지공단에 요양급여를 신청하도록 안내합니다. "
                            + "산재보험 급여 절차는 산업안전보건법이 아니라 산업재해보상보험법 소관이므로, "
                            + "구체적인 서식과 기한은 근로복지공단(1588-0075)에 확인하세요.",
                    null, null),

            new DutyDto("원인조사 · 감독 대응",
                    "고용노동부장관이 원인 규명과 예방대책 수립을 위해 원인조사를 할 수 있고, "
                            + "안전보건개선계획의 수립·시행 등 필요한 조치를 명할 수 있습니다. "
                            + "조사관이 사업장에 출입해 관계자에게 질문할 수 있습니다.",
                    null, "산업안전보건법 제56조 제1항 · 제2항 · 제4항"),

            new DutyDto("작업중지 명령을 받은 경우 해제 요청",
                    "고용노동부장관이 작업중지를 명한 경우, 사업주는 작업중지 해제를 요청할 수 있습니다. "
                            + "요청을 받으면 전문가 등으로 구성된 심의위원회의 심의를 거쳐 해제 여부가 결정됩니다.",
                    null, "산업안전보건법 제55조 제1항 · 제3항"));

    /** 의무를 지키지 않았을 때의 처벌 */
    private static final List<DutyDto> PENALTIES = List.of(
            new DutyDto("사고 현장 훼손 · 원인조사 방해",
                    "중대재해등의 발생 현장을 훼손하거나 고용노동부장관·공단·관계전문가의 원인조사를 방해한 자는 "
                            + "산업안전보건법 제170조의 벌칙 대상입니다.",
                    null, "산업안전보건법 제170조 제2호 (제56조 제5항 위반)"),

            new DutyDto("산업재해 발생 사실 은폐",
                    "산업재해 발생 사실을 은폐한 자, 은폐하도록 교사하거나 공모한 자는 "
                            + "산업안전보건법 제170조의 벌칙 대상입니다.",
                    null, "산업안전보건법 제170조 제3호 (제57조 제1항 위반)"),

            new DutyDto("중대재해 시 작업중지 · 대피 조치를 하지 않음",
                    "중대재해가 발생했는데 즉시 작업을 중지시키고 근로자를 대피시키는 조치를 하지 않으면 "
                            + "산업안전보건법 제168조의 벌칙 대상입니다.",
                    null, "산업안전보건법 제168조 제1호 (제54조 제1항 위반)"),

            new DutyDto("중대산업재해 발생 — 사업주 · 경영책임자 처벌",
                    "안전 및 보건 확보의무를 위반해 사망자가 1명 이상 발생한 중대산업재해에 이르게 하면 "
                            + "1년 이상의 징역 또는 10억원 이하의 벌금에 처하며, 징역과 벌금을 함께 부과할 수 있습니다. "
                            + "그 밖의 중대산업재해는 7년 이하의 징역 또는 1억원 이하의 벌금입니다.",
                    null, "중대재해 처벌 등에 관한 법률 제6조"),

            new DutyDto("법인에 대한 양벌규정",
                    "중대산업재해로 사업주나 경영책임자가 처벌되는 경우 법인 또는 기관에도 벌금이 부과됩니다. "
                            + "사망 사고는 50억원 이하, 그 밖의 중대산업재해는 10억원 이하의 벌금입니다.",
                    null, "중대재해 처벌 등에 관한 법률 제7조"));

    /**
     * 법적 의무. 중대재해로 보이면 작업중지·보고 의무를 앞에 붙인다.
     * 사망 사고에서 "현장 보존"이 첫 줄에 오면 순서가 이상하기 때문이다.
     */
    public List<DutyDto> legalDuties(boolean seriousLikely) {
        if (!seriousLikely) {
            return COMMON_DUTIES;
        }
        List<DutyDto> duties = new ArrayList<>(SERIOUS_DUTIES);
        duties.addAll(COMMON_DUTIES);
        return List.copyOf(duties);
    }

    public List<DutyDto> administrativeSteps() {
        return ADMINISTRATIVE_STEPS;
    }

    /**
     * 처벌. 중대재해처벌법은 중대산업재해에만 적용되므로 그렇게 보이지 않으면 빼고,
     * 산업안전보건법 제168조(중대재해 시 작업중지)도 마찬가지다.
     */
    public List<DutyDto> penalties(boolean seriousLikely) {
        if (seriousLikely) {
            return PENALTIES;
        }
        return PENALTIES.stream()
                .filter(penalty -> penalty.getLegalBasis() != null
                        && !penalty.getLegalBasis().startsWith("중대재해")
                        && !penalty.getLegalBasis().startsWith("산업안전보건법 제168조"))
                .toList();
    }

    /**
     * 이 안내의 근거가 되는 조문들. 검색으로는 잘 안 나오지만 사고 대처에서는 반드시
     * 보여야 하는 조문이라 조문번호로 직접 가져온다.
     */
    public List<ArticleRef> anchorArticles(boolean seriousLikely) {
        List<ArticleRef> refs = new ArrayList<>(List.of(
                new ArticleRef("산업안전보건법", "제57조"),
                new ArticleRef("산업안전보건법 시행규칙", "제73조"),
                new ArticleRef("산업안전보건법 시행규칙", "제72조"),
                new ArticleRef("산업안전보건법", "제56조"),
                new ArticleRef("산업안전보건법", "제170조")));
        if (seriousLikely) {
            refs.add(0, new ArticleRef("산업안전보건법", "제54조"));
            refs.add(new ArticleRef("산업안전보건법 시행규칙", "제3조"));
            refs.add(new ArticleRef("산업안전보건법", "제55조"));
            refs.add(new ArticleRef("산업안전보건법", "제168조"));
            refs.add(new ArticleRef("중대재해 처벌 등에 관한 법률", "제4조"));
            refs.add(new ArticleRef("중대재해 처벌 등에 관한 법률", "제6조"));
            refs.add(new ArticleRef("중대재해 처벌 등에 관한 법률", "제7조"));
        }
        return refs;
    }

    public record ArticleRef(String lawName, String articleNo) {
        /** law_article 조회용 키. (법령명, 조문번호) 짝을 한 문자열로 만든다. */
        public String key() {
            return lawName + "|" + articleNo;
        }
    }
}
