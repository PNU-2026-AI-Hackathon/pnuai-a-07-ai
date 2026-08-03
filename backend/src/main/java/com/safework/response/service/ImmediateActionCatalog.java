package com.safework.response.service;

import com.safework.response.dto.ImmediateActionDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사고 직후 밟아야 할 절차.
 *
 * 산업안전보건법이 정한 사업주 의무(작업중지·대피, 현장 보존, 지체 없는 보고)를 순서대로
 * 세우고, 그 사이에 인명 구조를 위한 실무 단계(119 신고, 2차 재해 방지)를 넣었다.
 * 법에서 온 단계는 근거 조문을 함께 내려보내 사장님이 출처를 확인할 수 있게 한다.
 *
 * 주의: 여기 있는 문구는 법정 의무의 요약이지 법률 자문이 아니다.
 * 응답에 면책 안내(DISCLAIMER)를 항상 함께 내보낸다.
 */
@Component
public class ImmediateActionCatalog {

    public static final String DISCLAIMER =
            "아래 절차는 산업안전보건법상 사업주 의무를 정리한 참고 자료입니다. "
                    + "실제 사고 상황에서는 인명 구조가 최우선이며, 구체적인 신고·처리 절차는 "
                    + "관할 지방고용노동관서 및 안전보건 전문가의 안내를 따르시기 바랍니다.";

    private static final List<ImmediateActionDto> ACTIONS = List.of(
            new ImmediateActionDto(1, "작업 중지 · 근로자 대피",
                    "즉시 해당 작업을 멈추고 주변 근로자를 안전한 곳으로 대피시킵니다. "
                            + "추가 피해를 막는 것이 가장 먼저입니다.",
                    "산업안전보건법 제54조 제1항", true),

            new ImmediateActionDto(2, "119 신고 · 응급처치",
                    "119에 신고하고 구급대가 올 때까지 가능한 범위에서 응급처치를 합니다. "
                            + "부상자를 함부로 옮기면 상태가 나빠질 수 있으니, 추가 위험이 없다면 "
                            + "구급대의 안내를 따르는 편이 안전합니다.",
                    null, true),

            new ImmediateActionDto(3, "2차 재해 방지 조치",
                    "전원 차단, 가스·유해물질 차단, 붕괴 우려 구간 출입 통제 등 같은 사고가 "
                            + "다시 일어나지 않도록 위험 요인을 제거하고 접근을 막습니다.",
                    "산업안전보건법 제54조 제1항", true),

            new ImmediateActionDto(4, "사고 현장 보존",
                    "구조와 2차 재해 방지에 필요한 경우가 아니라면 현장을 그대로 두어야 합니다. "
                            + "현장을 훼손하거나 원인조사를 방해하는 행위는 법으로 금지되어 있습니다. "
                            + "정리하기 전에 사진·영상으로 현장을 기록해 두면 좋습니다.",
                    "산업안전보건법 제56조 제5항", true),

            new ImmediateActionDto(5, "관할 지방고용노동관서 보고",
                    "중대재해가 발생한 사실을 알게 되면 지체 없이 관할 지방고용노동관서에 "
                            + "보고해야 합니다. 사업장 소재지 관할 관서로 연락하시면 됩니다.",
                    "산업안전보건법 제54조 제2항", true),

            new ImmediateActionDto(6, "산업재해조사표 제출 · 산재 신청 안내",
                    "이후 산업재해조사표를 제출하고, 재해자가 산재보험 급여를 신청할 수 있도록 "
                            + "근로복지공단 절차를 안내합니다.",
                    null, false),

            new ImmediateActionDto(7, "재발 방지 대책 수립",
                    "같은 사고가 반복되지 않도록 원인을 확인하고 설비·작업방법·교육을 고칩니다. "
                            + "아래 유사 재해 사례의 대책을 참고하세요.",
                    null, false)
    );

    /** 중대재해 여부와 무관하게 절차 자체는 같으므로 재해유형에 관계없이 동일한 목록을 준다. */
    public List<ImmediateActionDto> actions() {
        return ACTIONS;
    }
}
