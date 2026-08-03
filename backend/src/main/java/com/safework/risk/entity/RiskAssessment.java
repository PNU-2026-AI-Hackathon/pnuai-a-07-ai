package com.safework.risk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 위험도 진단 결과. 콜드스타트 진단은 fn_coldstart_assess 가 직접 INSERT 하므로
 * 백엔드에서는 조회 전용으로 사용한다.
 */
@Entity
@Table(name = "risk_assessment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {

    @Id
    @Column(name = "assessment_id")
    private Long id;

    @Column(name = "workplace_id", nullable = false)
    private Long workplaceId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private AssessMethod method;

    @Column(name = "risk_score", nullable = false)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_grade", nullable = false)
    private RiskGrade riskGrade;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "top_accident_type")
    private String topAccidentType;

    /** 동종·동규모·동지역 통계에서 산출한 기본 점수 (0~60) */
    @Column(name = "base_component")
    private BigDecimal baseComponent;

    /** 체크리스트 미비 항목에서 산출한 점수 (0~40) */
    @Column(name = "checklist_component")
    private BigDecimal checklistComponent;

    /** 베이스라인 매칭 수준 (EXACT/INDUSTRY_SIZE/INDUSTRY/NONE) */
    @Column(name = "match_level")
    private String matchLevel;

    /**
     * ML 예측 시 사용한 피처. 콜드스타트 진단은 점수 근거를 위 컬럼들에 넣으므로
     * 보통 비어 있다(SCHEMA_21 이전에는 여기에 jsonb 로 담았다).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_features")
    private Map<String, Object> rawFeatures;

    @Column(name = "assessed_at", insertable = false, updatable = false)
    private OffsetDateTime assessedAt;
}
