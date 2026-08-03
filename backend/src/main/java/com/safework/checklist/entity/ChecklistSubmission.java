package com.safework.checklist.entity;

import com.safework.auth.entity.Member;
import com.safework.workplace.entity.Workplace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사업장의 체크리스트 1회 제출분. 위험도 진단(fn_coldstart_score)은
 * 사업장의 가장 최근 제출을 기준으로 계산한다.
 */
@Entity
@Table(name = "checklist_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workplace_id", nullable = false)
    private Workplace workplace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private Member submittedBy;

    /** 제출에 포함된 문항 수 */
    @Column(name = "total_items", nullable = false)
    private int totalItems;

    /** 그 중 YES/NO 로 실제 답한 문항 수 (NA 제외 — 위험도 계산 대상) */
    @Column(name = "answered_items", nullable = false)
    private int answeredItems;

    @Column(name = "submitted_at", insertable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Builder
    public ChecklistSubmission(Workplace workplace, Member submittedBy,
                                int totalItems, int answeredItems) {
        this.workplace = workplace;
        this.submittedBy = submittedBy;
        this.totalItems = totalItems;
        this.answeredItems = answeredItems;
    }
}
