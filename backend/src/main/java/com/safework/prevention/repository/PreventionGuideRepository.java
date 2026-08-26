package com.safework.prevention.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PreventionGuideRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String CALL_FUNCTION =
            "SELECT * FROM fn_prevention_guide(?, ?, ?, ?, ?)";

    public List<PreventionGuideRow> fetch(String industry, String sizeClass, String region,
                                           int expectedAccidentCount, int itemsPerAccident) {
        return jdbcTemplate.query(CALL_FUNCTION,
                (rs, rowNum) -> new PreventionGuideRow(
                        rs.getInt("accident_rank"),
                        rs.getString("accident_type"),
                        rs.getBigDecimal("accident_ratio"),
                        rs.getBigDecimal("death_ratio"),
                        rs.getString("item_code"),
                        rs.getString("work_type"),
                        rs.getString("question"),
                        rs.getBigDecimal("risk_weight"),
                        rs.getBoolean("is_critical"),
                        toLawBasisList(rs.getObject("law_basis"))
                ),
                industry, sizeClass, region, expectedAccidentCount, itemsPerAccident);
    }

    /** 최신 체크리스트의 NO 응답을 위험 가중치 순으로 묶은 맞춤 예방가이드. */
    public List<PreventionGuideRow> fetchForLatestDiagnosis(Long workplaceId,
                                                            int accidentCount,
                                                            int itemsPerAccident) {
        String sql = """
                WITH latest AS (
                    SELECT submission_id
                    FROM checklist_submission
                    WHERE workplace_id = ?
                    ORDER BY submitted_at DESC
                    LIMIT 1
                ), deficient AS (
                    SELECT ci.*
                    FROM latest l
                    JOIN checklist_response cr ON cr.submission_id = l.submission_id
                    JOIN checklist_item ci ON ci.item_id = cr.item_id
                    WHERE cr.answer = 'NO'::answer_t
                ), category_score AS (
                    SELECT category, SUM(risk_weight) AS category_weight
                    FROM deficient
                    GROUP BY category
                ), ranked_category AS (
                    SELECT category, category_weight,
                           DENSE_RANK() OVER (ORDER BY category_weight DESC, category) AS accident_rank,
                           category_weight / NULLIF(SUM(category_weight) OVER (), 0) AS accident_ratio
                    FROM category_score
                ), ranked_item AS (
                    SELECT d.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY d.category
                               ORDER BY d.is_critical DESC, d.risk_weight DESC, d.display_order
                           ) AS item_rank
                    FROM deficient d
                )
                SELECT rc.accident_rank::int,
                       rc.category AS accident_type,
                       rc.accident_ratio,
                       0::numeric AS death_ratio,
                       ri.item_code,
                       ri.work_type,
                       ri.question,
                       ri.risk_weight,
                       ri.is_critical,
                       ARRAY(
                           SELECT la.law_name || ' ' || la.article_no
                           FROM unnest(string_to_array(ri.law_ref, ',')) AS x(article_id)
                           JOIN law_article la ON la.article_id =
                               CASE WHEN trim(x.article_id) ~ '^[0-9]+$' THEN trim(x.article_id)::bigint END
                           ORDER BY la.article_id
                       ) AS law_basis
                FROM ranked_category rc
                JOIN ranked_item ri ON ri.category = rc.category
                WHERE rc.accident_rank <= ? AND ri.item_rank <= ?
                ORDER BY rc.accident_rank, ri.item_rank
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new PreventionGuideRow(
                        rs.getInt("accident_rank"),
                        rs.getString("accident_type"),
                        rs.getBigDecimal("accident_ratio"),
                        rs.getBigDecimal("death_ratio"),
                        rs.getString("item_code"),
                        rs.getString("work_type"),
                        rs.getString("question"),
                        rs.getBigDecimal("risk_weight"),
                        rs.getBoolean("is_critical"),
                        toLawBasisList(rs.getObject("law_basis"))
                ), workplaceId, accidentCount, itemsPerAccident);
    }

    private List<String> toLawBasisList(Object lawBasis) {
        if (lawBasis == null) {
            return List.of();
        }
        if (lawBasis instanceof Array sqlArray) {
            try {
                return Arrays.stream((Object[]) sqlArray.getArray())
                        .map(String::valueOf)
                        .toList();
            } catch (SQLException e) {
                throw new IllegalStateException("law_basis 배열을 읽는 중 오류가 발생했습니다.", e);
            }
        }
        return List.of(String.valueOf(lawBasis));
    }
}
