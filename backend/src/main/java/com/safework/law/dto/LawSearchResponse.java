package com.safework.law.dto;

import com.safework.law.repository.LawSearchRepository;
import lombok.Getter;

import java.util.List;

@Getter
public class LawSearchResponse {

    private final String query;
    /** 실제로 검색에 사용한 단어들 (동의어 확장 결과 포함) — 왜 이 조문이 나왔는지 설명용 */
    private final List<String> searchTerms;
    private final int totalCount;
    private final List<LawArticleDto> results;

    public LawSearchResponse(String query, List<String> searchTerms,
                             List<LawSearchRepository.LawHit> hits) {
        this.query = query;
        this.searchTerms = searchTerms;
        this.totalCount = hits.size();
        this.results = hits.stream().map(LawArticleDto::new).toList();
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
        /** 몇 개의 검색어가 맞았는지 — 관련도 참고용 */
        private final int matchedTerms;
        /** 조문 제목에 검색어가 있었는지 */
        private final boolean titleMatched;

        public LawArticleDto(LawSearchRepository.LawHit hit) {
            this.articleId = hit.articleId();
            this.lawName = hit.lawName();
            this.articleNo = hit.articleNo();
            this.clauseNo = hit.clauseNo();
            this.title = hit.title();
            this.content = hit.content();
            this.matchedTerms = hit.matchedTerms();
            this.titleMatched = hit.titleHit();
        }
    }
}
