package com.safework.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 최신 제출에서 'NO' 로 답한 미비 항목과 그 근거 법령.
 *
 * fn_diagnosis_law_basis 는 checklist_item.law_ref 를 unnest 하므로,
 * law_ref 에 같은 조문 id 가 중복돼 있으면 같은 행이 여러 번 나온다.
 * 리포트에 중복 표시되지 않도록 SQL 단계에서 DISTINCT 로 정리한다.
 */
@Repository
@RequiredArgsConstructor
public class DiagnosisLawBasisRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String QUERY = """
            SELECT DISTINCT item_code, work_type, category, question,
                            risk_weight, is_critical, law_name, article_no, title
            FROM fn_diagnosis_law_basis(?)
            ORDER BY is_critical DESC, risk_weight DESC, item_code, article_no
            """;

    public record DeficientItem(
            String itemCode,
            String workType,
            String category,
            String question,
            BigDecimal riskWeight,
            boolean critical,
            String lawName,
            String articleNo,
            String title
    ) {
    }

    public List<DeficientItem> findByWorkplace(Long workplaceId) {
        return jdbcTemplate.query(QUERY, (rs, rowNum) -> new DeficientItem(
                rs.getString("item_code"),
                rs.getString("work_type"),
                rs.getString("category"),
                rs.getString("question"),
                rs.getBigDecimal("risk_weight"),
                rs.getBoolean("is_critical"),
                rs.getString("law_name"),
                rs.getString("article_no"),
                rs.getString("title")
        ), workplaceId);
    }
}
