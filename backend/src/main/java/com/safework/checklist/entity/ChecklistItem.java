package com.safework.checklist.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 안전조치 점검 문항. DB팀이 SIF 사례에서 생성해 적재한 읽기 전용 마스터 데이터.
 */
@Entity
@Table(name = "checklist_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @Column(name = "item_id")
    private Long id;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String category;          // 재해유형 (끼임, 떨어짐 등)

    @Column(name = "work_type")
    private String workType;          // 작업 종류

    @Column(nullable = false)
    private String question;

    private String description;

    @Column(name = "target_industry")
    private String targetIndustry;

    @Column(name = "target_region")
    private String targetRegion;

    @Column(name = "risk_weight", nullable = false)
    private BigDecimal riskWeight;

    @Column(name = "is_critical")
    private Boolean critical;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public boolean isCritical() {
        return Boolean.TRUE.equals(critical);
    }
}
