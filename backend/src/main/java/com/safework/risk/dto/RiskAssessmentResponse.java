package com.safework.risk.dto;

import com.safework.risk.entity.RiskAssessment;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * ML 예측 — 어떤 재해가 날 가능성이 높은지, 얼마나 심각할지.
     * ML 서버를 못 쓴 경우 빈 배열이며, 그때 method 는 COLDSTART 로 남는다.
     */
    private final List<Map<String, Object>> topRisks;
    private final List<Map<String, Object>> severityPrediction;

    @SuppressWarnings("unchecked")
    public RiskAssessmentResponse(RiskAssessment assessment) {
        this.assessmentId = assessment.getId();
        this.workplaceId = assessment.getWorkplaceId();
        this.submissionId = assessment.getSubmissionId();
        this.method = assessment.getMethod().name();
        this.riskScore = assessment.getRiskScore();
        // SCHEMA_8 이후 두 컬럼은 NULL 을 허용한다(베이스라인 매칭이 NONE 인 경우).
        // 지금 데이터로는 NONE 이 안 생기지만, 생기면 화면이 500 으로 죽는 대신
        // 점수 없이 나머지 정보라도 보여줄 수 있어야 한다.
        this.riskGrade = assessment.getRiskGrade() == null ? null : assessment.getRiskGrade().name();
        this.topAccidentType = assessment.getTopAccidentType();
        this.baseComponent = assessment.getBaseComponent();
        this.checklistComponent = assessment.getChecklistComponent();
        this.matchLevel = assessment.getMatchLevel();
        this.modelVersion = assessment.getModelVersion();
        this.assessedAt = assessment.getAssessedAt();

        Map<String, Object> raw = assessment.getRawFeatures();
        this.topRisks = raw == null ? List.of()
                : (List<Map<String, Object>>) raw.getOrDefault("top_risks", List.of());
        this.severityPrediction = raw == null ? List.of()
                : (List<Map<String, Object>>) raw.getOrDefault("severity_prediction", List.of());
    }
}
