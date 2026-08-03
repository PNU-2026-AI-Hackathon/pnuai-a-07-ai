package com.safework.law.repository;

import com.safework.law.service.QueryExpander;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 법령 조문 검색.
 *
 * 임베딩·FAISS 인덱스가 아직 없어(law_chunk.faiss_idx 전부 NULL) 키워드 매칭으로 찾는다.
 * 랭킹은 세 신호를 순서대로 본다.
 *   1) 조문 제목에 검색어가 있는가 — 가장 강한 신호
 *   2) 서로 다른 검색어가 몇 개나 맞았는가
 *   3) 본문에 몇 번 나오는가
 *
 * similarity() 로 순위를 매기면 긴 청크일수록 점수가 희석돼 오답이 위로 올라온다
 * ("안전난간" 검색 시 '계단의 난간'이 '안전난간 및 울타리의 설치'보다 높게 나옴).
 * 그래서 유사도 대신 위 신호를 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class LawSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SEARCH = """
            WITH tokens AS (
                SELECT t, w FROM unnest(?, ?) AS x(t, w)
            ),
            hits AS (
                SELECT lc.chunk_id, lc.article_id, lc.content, tk.t, tk.w,
                       (length(lc.content) - length(replace(lc.content, tk.t, ''))) / length(tk.t) AS occ
                FROM law_chunk lc
                JOIN tokens tk ON lc.content ILIKE '%' || tk.t || '%'
            ),
            scored AS (
                SELECT h.chunk_id, h.article_id, h.content,
                       count(DISTINCT h.t) AS matched_tokens,
                       sum(DISTINCT h.w)   AS term_score,
                       sum(h.occ)          AS occurrences
                FROM hits h
                GROUP BY h.chunk_id, h.article_id, h.content
            ),
            best_chunk AS (
                -- 한 조문에서 청크가 여러 개 맞을 수 있으므로 가장 잘 맞은 것만 남긴다.
                SELECT DISTINCT ON (s.article_id)
                       s.article_id, s.chunk_id, s.content,
                       s.matched_tokens, s.term_score, s.occurrences
                FROM scored s
                ORDER BY s.article_id, s.term_score DESC, s.occurrences DESC
            ),
            titled AS (
                SELECT b.*, la.law_name, la.article_no, la.clause_no, la.title,
                       COALESCE((SELECT sum(DISTINCT tk.w) FROM tokens tk
                                 WHERE la.title ILIKE '%' || tk.t || '%'), 0) AS title_score
                FROM best_chunk b
                JOIN law_article la ON la.article_id = b.article_id
            )
            SELECT article_id, law_name, article_no, clause_no, title, content,
                   matched_tokens, occurrences, (title_score > 0) AS title_hit
            FROM titled
            -- 점수까지 같으면 제목이 짧은 쪽을 먼저 보여준다. 수식어가 붙지 않은 제목이
            -- 대체로 더 일반적인 조항이라 첫 화면에 적합하다.
            ORDER BY title_score DESC, term_score DESC, occurrences DESC,
                     length(title), article_id
            LIMIT ?
            """;

    public record LawHit(
            Long articleId,
            String lawName,
            String articleNo,
            String clauseNo,
            String title,
            String content,
            int matchedTerms,
            long occurrences,
            boolean titleHit
    ) {
    }

    public List<LawHit> search(List<QueryExpander.WeightedTerm> terms, int limit) {
        Object[] words = terms.stream().map(QueryExpander.WeightedTerm::term).toArray();
        Object[] weights = terms.stream().map(QueryExpander.WeightedTerm::weight).toArray();

        return jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(SEARCH);
                    ps.setArray(1, connection.createArrayOf("text", words));
                    ps.setArray(2, connection.createArrayOf("int4", weights));
                    ps.setInt(3, limit);
                    return ps;
                },
                (rs, rowNum) -> new LawHit(
                        rs.getLong("article_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("clause_no"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getInt("matched_tokens"),
                        rs.getLong("occurrences"),
                        rs.getBoolean("title_hit")
                ));
    }
}
