package com.safework.risk.dto;

import com.safework.risk.entity.RiskAssessment;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
public class RiskAssessmentResponse {

    private final Long assessmentId;
    private final Long workplaceId;
    private final Long submissionId;
    private final String method;
    private final BigDecimal riskScore;
    private final String riskGrade;
    private final String topAccidentType;

    /** 점수 근거 — 기본(통계) + 체크리스트(미비 항목) */
    private final BigDecimal baseComponent;
    private final BigDecimal checklistComponent;
    private final String matchLevel;

    private final String modelVersion;
    private final OffsetDateTime assessedAt;

    public RiskAssessmentResponse(RiskAssessment assessment) {
        this.assessmentId = assessment.getId();
        this.workplaceId = assessment.getWorkplaceId();
        this.submissionId = assessment.getSubmissionId();
        this.method = assessment.getMethod().name();
        this.riskScore = assessment.getRiskScore();
        this.riskGrade = assessment.getRiskGrade().name();
        this.topAccidentType = assessment.getTopAccidentType();
        this.baseComponent = assessment.getBaseComponent();
        this.checklistComponent = assessment.getChecklistComponent();
        this.matchLevel = assessment.getMatchLevel();
        this.modelVersion = assessment.getModelVersion();
        this.assessedAt = assessment.getAssessedAt();
    }
}
