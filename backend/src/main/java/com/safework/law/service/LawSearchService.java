package com.safework.law.service;

import com.safework.law.dto.LawSearchResponse;
import com.safework.law.dto.LawSearchResponse.LawArticleDto;
import com.safework.law.repository.LawSearchRepository;
import com.safework.ml.client.MlServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 법령 조문 검색.
 *
 * 키워드 검색과 의미 검색(ML 서버 임베딩)의 결과를 섞어서 준다.
 * 실제로 6개 질문으로 비교해 보니 둘이 서로 잘하는 영역이 달랐다.
 *
 *   "사다리에서 떨어질 것 같아요" -> 키워드가 제42조(추락의 방지)를 1위로, 의미 검색은 3위로
 *   "밀폐공간 들어갈 때"          -> 키워드가 제619조를 1위로, 의미 검색은 2위로
 *   "안전관리자 꼭 둬야 하나요"    -> 의미 검색만 제17조(안전관리자)를 찾아냄
 *
 * 한쪽만 쓰면 다른 쪽이 잘 찾던 질문이 나빠지므로, 양쪽 상위 결과를 번갈아 담아
 * 중복(같은 법령·조문)을 제거한다. ML 서버가 꺼져 있으면 키워드 결과만 나간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LawSearchService {

    private final QueryExpander queryExpander;
    private final LawSearchRepository lawSearchRepository;
    private final MlServerClient mlServerClient;

    public LawSearchResponse search(String query, int size) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }

        List<QueryExpander.WeightedTerm> terms = queryExpander.expand(trimmed);
        List<String> words = terms.stream().map(QueryExpander.WeightedTerm::term).toList();

        List<LawArticleDto> keywordHits = terms.isEmpty() ? List.of()
                : lawSearchRepository.search(terms, size).stream().map(LawArticleDto::of).toList();

        List<LawArticleDto> semanticHits = mlServerClient.searchLaw(trimmed, size)
                .map(hits -> hits.stream().map(LawArticleDto::of).toList())
                .orElse(List.of());

        if (semanticHits.isEmpty()) {
            return LawSearchResponse.keywordOnly(trimmed, words, keywordHits);
        }
        return LawSearchResponse.hybrid(trimmed, words, merge(semanticHits, keywordHits, size));
    }

    /**
     * 두 결과를 번갈아 담는다. 어느 쪽이 항상 낫다고 볼 근거가 없어서 동등하게 취급한다.
     * 같은 조문이 양쪽에서 나오면 먼저 담긴 것만 남긴다.
     */
    private List<LawArticleDto> merge(List<LawArticleDto> semantic, List<LawArticleDto> keyword, int size) {
        Map<String, LawArticleDto> merged = new LinkedHashMap<>();

        int max = Math.max(semantic.size(), keyword.size());
        for (int i = 0; i < max && merged.size() < size; i++) {
            if (i < semantic.size()) {
                merged.putIfAbsent(dedupeKey(semantic.get(i)), semantic.get(i));
            }
            if (merged.size() < size && i < keyword.size()) {
                merged.putIfAbsent(dedupeKey(keyword.get(i)), keyword.get(i));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 항(clause)은 무시하고 조문 단위로 묶는다.
     * 키워드 검색은 항별로 행을 주고 의미 검색은 항을 주지 않아서, 항까지 키에 넣으면
     * 같은 조문이 여러 번 나열돼 사용자에게는 중복으로 보인다.
     * 조문 단위로 묶는 편이 서로 다른 조문을 더 많이 보여줄 수 있다.
     */
    private String dedupeKey(LawArticleDto article) {
        return article.getLawName() + "|" + article.getArticleNo();
    }
}
