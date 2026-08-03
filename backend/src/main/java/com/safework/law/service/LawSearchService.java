package com.safework.law.service;

import com.safework.law.dto.LawSearchResponse;
import com.safework.law.repository.LawSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LawSearchService {

    private final QueryExpander queryExpander;
    private final LawSearchRepository lawSearchRepository;

    public LawSearchResponse search(String query, int size) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }

        List<QueryExpander.WeightedTerm> terms = queryExpander.expand(trimmed);
        List<String> words = terms.stream().map(QueryExpander.WeightedTerm::term).toList();

        if (terms.isEmpty()) {
            // 조사·불용어만 남아 검색할 단어를 못 뽑은 경우. 빈 결과로 돌려주고 안내는 프론트에서.
            return new LawSearchResponse(trimmed, words, List.of());
        }

        return new LawSearchResponse(trimmed, words, lawSearchRepository.search(terms, size));
    }
}
