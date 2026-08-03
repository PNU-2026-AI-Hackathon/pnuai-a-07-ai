package com.safework.response.service;

import com.safework.response.dto.AccidentResponseGuide;
import com.safework.response.repository.AccidentResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccidentResponseService {

    private static final int LAW_BASIS_LIMIT = 8;
    private static final int SIMILAR_CASE_LIMIT = 3;

    private final AccidentResponseRepository repository;
    private final AccidentTypeVocabulary vocabulary;
    private final ImmediateActionCatalog actionCatalog;

    public AccidentResponseGuide getGuide(String accidentType, String industry) {
        String type = accidentType == null ? "" : accidentType.trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("재해유형은 필수입니다.");
        }

        List<AccidentResponseRepository.LawBasis> lawBasis =
                repository.findLawBasis(industry, type, LAW_BASIS_LIMIT);

        // sif_case 는 기술어(추락)·업종명(제조업등)이 달라 바꿔서 조회한다.
        List<AccidentResponseRepository.SimilarCase> cases = repository.findSimilarCases(
                vocabulary.toSifKinds(type), vocabulary.toSifIndustry(industry), SIMILAR_CASE_LIMIT);

        return new AccidentResponseGuide(type, industry, ImmediateActionCatalog.DISCLAIMER,
                actionCatalog.actions(), lawBasis, cases, similarCaseNote(industry, cases.isEmpty()));
    }

    /**
     * 사례가 비었을 때 프론트가 "없음"과 "아직 정리 안 됨"을 구분해 안내할 수 있게 사유를 준다.
     * 현재 sif_case 는 건설업만 재해유형이 분류돼 있고, 제조업등 2,573건은 대책은 있으나
     * accident_kind 가 비어 있어 유형별로 찾을 수 없다.
     */
    private String similarCaseNote(String industry, boolean empty) {
        if (!empty) {
            return null;
        }
        if ("제조업".equals(industry)) {
            return "제조업 중대재해 사례는 재해유형 분류가 아직 정리되지 않아 표시할 수 없습니다.";
        }
        return "해당 업종·재해유형으로 정리된 중대재해 사례가 없습니다.";
    }
}
