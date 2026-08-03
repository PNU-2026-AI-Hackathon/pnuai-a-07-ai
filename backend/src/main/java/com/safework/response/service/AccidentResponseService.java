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
                actionCatalog.actions(), lawBasis, cases,
                vocabulary.missingCaseReason(industry, cases.isEmpty()));
    }
}
