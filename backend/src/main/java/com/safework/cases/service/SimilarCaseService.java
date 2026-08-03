package com.safework.cases.service;

import com.safework.cases.dto.SimilarCaseResponse;
import com.safework.ml.client.MlServerClient;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimilarCaseService {

    private static final String UNAVAILABLE_NOTE =
            "유사 재해사례를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final WorkplaceRepository workplaceRepository;
    private final MlServerClient mlServerClient;

    /**
     * 사업장과 비슷한 중대재해 사례를 찾는다.
     *
     * 검색은 ML 서버의 임베딩이 담당한다. 서버가 꺼져 있거나 인덱스를 만드는 중이면
     * 예외 대신 사유를 담은 빈 결과를 돌려준다 — 이 기능 하나 때문에 화면 전체가
     * 실패하지는 않도록.
     */
    public SimilarCaseResponse findForWorkplace(Long memberId, Long workplaceId, int topN) {
        Workplace workplace = workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));

        return search(workplace.getIndustry(), workplace.getSubIndustry(), topN);
    }

    public SimilarCaseResponse search(String industry, String subIndustry, int topN) {
        return mlServerClient.analyzeCases(industry, subIndustry, topN)
                .map(result -> SimilarCaseResponse.of(industry, subIndustry, result))
                .orElseGet(() -> SimilarCaseResponse.unavailable(industry, subIndustry, UNAVAILABLE_NOTE));
    }
}
