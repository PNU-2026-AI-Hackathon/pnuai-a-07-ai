package com.safework.checklist.repository;

import com.safework.checklist.entity.Answer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.math.BigDecimal;

/**
 * checklist_response 는 복합 PK(submission_id, item_id) + PostgreSQL enum(answer_t) 이고
 * 한 번에 수백 건이 들어가므로, JPA 엔티티 대신 배치 INSERT 로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class ChecklistResponseRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT = """
            INSERT INTO checklist_response (submission_id, item_id, answer, note)
            VALUES (?, ?, ?::answer_t, ?)
            """;

    public record ResponseRow(Long itemId, Answer answer, String note) {
    }

    public void saveAll(Long submissionId, List<ResponseRow> rows) {
        jdbcTemplate.batchUpdate(INSERT, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, submissionId);
            ps.setLong(2, row.itemId());
            ps.setString(3, row.answer().name());
            ps.setString(4, row.note());
        });
    }

    public record RiskSignal(String category, BigDecimal weight, int deficientCount) {
    }

    public record DeficientItem(String category, String workType, String question,
                                BigDecimal riskWeight) {
    }

    /** 최신 제출의 미비 응답을 재해유형별 위험 신호로 집계한다. */
    public List<RiskSignal> findRiskSignals(Long submissionId) {
        return jdbcTemplate.query("""
                SELECT ci.category,
                       COALESCE(SUM(ci.risk_weight), 0) AS weight,
                       COUNT(*)::int AS deficient_count
                FROM checklist_response cr
                JOIN checklist_item ci ON ci.item_id = cr.item_id
                WHERE cr.submission_id = ? AND cr.answer = 'NO'::answer_t
                GROUP BY ci.category
                ORDER BY weight DESC, ci.category
                """, (rs, rowNum) -> new RiskSignal(
                rs.getString("category"),
                rs.getBigDecimal("weight"),
                rs.getInt("deficient_count")), submissionId);
    }

    /** 사례 검색과 예방가이드 설명에 사용할 구체적인 미비 항목을 조회한다. */
    public List<DeficientItem> findDeficientItems(Long submissionId) {
        return jdbcTemplate.query("""
                SELECT ci.category, ci.work_type, ci.question, ci.risk_weight
                FROM checklist_response cr
                JOIN checklist_item ci ON ci.item_id = cr.item_id
                WHERE cr.submission_id = ? AND cr.answer = 'NO'::answer_t
                ORDER BY ci.is_critical DESC, ci.risk_weight DESC, ci.display_order
                """, (rs, rowNum) -> new DeficientItem(
                rs.getString("category"),
                rs.getString("work_type"),
                rs.getString("question"),
                rs.getBigDecimal("risk_weight")), submissionId);
    }
}
