package com.safework.law.dto;

import com.safework.law.repository.LawSearchRepository;
import com.safework.ml.client.MlServerClient;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Getter
public class LawSearchResponse {

    /** 어떤 방식으로 찾았는지. 결과 성격이 달라 클라이언트가 알 수 있어야 한다. */
    public enum SearchMode {
        /** 키워드 검색 + ML 의미 검색을 함께 사용 */
        HYBRID,
        /** ML 서버를 쓸 수 없어 키워드 검색만 사용 */
        KEYWORD
    }

    private final String query;
    private final SearchMode mode;
    /** 키워드 검색에 실제로 사용한 단어들(동의어 확장 포함) — 왜 이 조문이 나왔는지 설명용 */
    private final List<String> searchTerms;
    private final int totalCount;
    private final List<LawArticleDto> results;

    private LawSearchResponse(String query, SearchMode mode, List<String> searchTerms,
                              List<LawArticleDto> results) {
        this.query = query;
        this.mode = mode;
        this.searchTerms = searchTerms;
        this.totalCount = results.size();
        this.results = results;
    }

    public static LawSearchResponse keywordOnly(String query, List<String> searchTerms,
                                                List<LawArticleDto> results) {
        return new LawSearchResponse(query, SearchMode.KEYWORD, searchTerms, results);
    }

    public static LawSearchResponse hybrid(String query, List<String> searchTerms,
                                           List<LawArticleDto> results) {
        return new LawSearchResponse(query, SearchMode.HYBRID, searchTerms, results);
    }

    @Getter
    public static class LawArticleDto {

        private final Long articleId;
        private final String lawName;
        private final String articleNo;
        private final String clauseNo;
        private final String title;
        /** 조문 본문 (검색에 걸린 청크) */
        private final String content;
        /** 이 결과를 찾아낸 방식 */
        private final String source;
        /** 의미 검색의 유사도(0~1). 키워드 결과면 null */
        private final BigDecimal score;
        /** 키워드 검색에서 맞은 검색어 수. 의미 검색 결과면 null */
        private final Integer matchedTerms;

        private LawArticleDto(Long articleId, String lawName, String articleNo, String clauseNo,
                              String title, String content, String source,
                              BigDecimal score, Integer matchedTerms) {
            this.articleId = articleId;
            this.lawName = lawName;
            this.articleNo = articleNo;
            this.clauseNo = clauseNo;
            this.title = title;
            this.content = content;
            this.source = source;
            this.score = score;
            this.matchedTerms = matchedTerms;
        }

        public static LawArticleDto of(LawSearchRepository.LawHit hit) {
            return new LawArticleDto(hit.articleId(), hit.lawName(), hit.articleNo(),
                    hit.clauseNo(), hit.title(), hit.content(),
                    "KEYWORD", null, hit.matchedTerms());
        }

        public static LawArticleDto of(MlServerClient.LawHit hit) {
            return new LawArticleDto(hit.articleId(), hit.lawName(), hit.articleNo(),
                    // ML 서버는 항(clause)을 따로 주지 않는다.
                    null, hit.title(), hit.content(), "SEMANTIC",
                    hit.score() == null ? null : hit.score().setScale(3, RoundingMode.HALF_UP),
                    null);
        }
    }
}
