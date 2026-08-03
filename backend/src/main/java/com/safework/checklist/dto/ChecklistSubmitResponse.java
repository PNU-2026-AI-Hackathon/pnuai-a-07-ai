package com.safework.checklist.dto;

import com.safework.risk.dto.RiskAssessmentResponse;
import lombok.Getter;

@Getter
public class ChecklistSubmitResponse {

    private final Long submissionId;
    private final int totalItems;
    private final int answeredItems;

    /** 제출 직후 계산된 위험도 진단 결과 */
    private final RiskAssessmentResponse riskAssessment;

    public ChecklistSubmitResponse(Long submissionId, int totalItems, int answeredItems,
                                    RiskAssessmentResponse riskAssessment) {
        this.submissionId = submissionId;
        this.totalItems = totalItems;
        this.answeredItems = answeredItems;
        this.riskAssessment = riskAssessment;
    }
}
