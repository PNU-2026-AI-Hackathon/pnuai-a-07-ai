package com.safework.response.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccidentResponseRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 같은 재해유형의 중대재해(SIF) 사례와 그 재발방지 대책.
     * 대책이 없는 사례는 대처 가이드에 쓸모가 없으므로 제외한다.
     */
    private static final String SIMILAR_CASES = """
            SELECT sif_id, accident_kind, accident_summary,
                   high_risk_situation, causal_factor, countermeasure
            FROM   sif_case
            WHERE  accident_kind = ANY(?)
              AND  countermeasure IS NOT NULL AND countermeasure <> ''
              AND  (industry_div = ? OR ? IS NULL)
            ORDER  BY sif_id
            LIMIT  ?
            """;

    /** 재해유형별 근거 법령 — checklist_item.law_ref 를 실제 조문으로 펼쳐 빈도순으로 준다. */
    private static final String LAW_BASIS = """
            SELECT law_name, article_no, clause_no, title, ref_items
            FROM   fn_accident_law_basis(?, ?, ?)
            """;

    /**
     * 조문번호로 조문을 그대로 가져온다.
     *
     * 사고 대처에서 반드시 보여야 하는 조문(보고 의무·조사표 제출·벌칙)은 검색으로는 잘 안 나온다.
     * 사고 서술에는 "지게차", "다리"처럼 사고 자체의 어휘만 있고 "보고", "조사표"는 없기 때문이다.
     * 그래서 이 조문들만 번호로 집어 온다.
     *
     * (법령명, 조문번호) 짝을 한 문자열로 이어 붙여 비교하고, 넘긴 순서를 그대로 유지한다.
     */
    private static final String ARTICLES_BY_NO = """
            SELECT article_id, law_name, article_no, clause_no, title, content
            FROM   law_article
            WHERE  law_name || '|' || article_no = ANY(?)
            ORDER  BY array_position(?, law_name || '|' || article_no), article_id
            """;

    public record SimilarCase(
            Long sifId,
            String accidentKind,
            String summary,
            String highRiskSituation,
            String causalFactor,
            String countermeasure
    ) {
    }

    public record LawBasis(
            String lawName,
            String articleNo,
            String clauseNo,
            String title,
            int refItems
    ) {
    }

    public record ArticleRow(
            Long articleId,
            String lawName,
            String articleNo,
            String clauseNo,
            String title,
            String content
    ) {
    }

    public List<ArticleRow> findArticlesByNo(List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(ARTICLES_BY_NO);
                    ps.setArray(1, connection.createArrayOf("text", keys.toArray()));
                    ps.setArray(2, connection.createArrayOf("text", keys.toArray()));
                    return ps;
                },
                (rs, rowNum) -> new ArticleRow(
                        rs.getLong("article_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("clause_no"),
                        rs.getString("title"),
                        rs.getString("content")
                ));
    }

    public List<SimilarCase> findSimilarCases(List<String> sifKinds, String industry, int limit) {
        return jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(SIMILAR_CASES);
                    ps.setArray(1, connection.createArrayOf("text", sifKinds.toArray()));
                    ps.setString(2, industry);
                    ps.setString(3, industry);
                    ps.setInt(4, limit);
                    return ps;
                },
                (rs, rowNum) -> new SimilarCase(
                        rs.getLong("sif_id"),
                        rs.getString("accident_kind"),
                        rs.getString("accident_summary"),
                        rs.getString("high_risk_situation"),
                        rs.getString("causal_factor"),
                        rs.getString("countermeasure")
                ));
    }

    public List<LawBasis> findLawBasis(String industry, String accidentType, int limit) {
        return jdbcTemplate.query(LAW_BASIS,
                (rs, rowNum) -> new LawBasis(
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("clause_no"),
                        rs.getString("title"),
                        rs.getInt("ref_items")
                ),
                industry, accidentType, limit);
    }
}
