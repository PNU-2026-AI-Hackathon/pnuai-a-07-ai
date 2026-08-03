package com.safework.risk.dto;

import com.safework.risk.entity.RiskAssessment;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
public class RiskAssessmentResponse {

    private final Long assessmentId;
    private final Long workplaceId;
    private final String method;
    private final BigDecimal riskScore;
    private final String riskGrade;
    private final String topAccidentType;
    private final String modelVersion;
    private final Map<String, Object> rawFeatures;
    private final OffsetDateTime assessedAt;

    public RiskAssessmentResponse(RiskAssessment assessment) {
        this.assessmentId = assessment.getId();
        this.workplaceId = assessment.getWorkplaceId();
        this.method = assessment.getMethod().name();
        this.riskScore = assessment.getRiskScore();
        this.riskGrade = assessment.getRiskGrade().name();
        this.topAccidentType = assessment.getTopAccidentType();
        this.modelVersion = assessment.getModelVersion();
        this.rawFeatures = assessment.getRawFeatures();
        this.assessedAt = assessment.getAssessedAt();
    }
}
