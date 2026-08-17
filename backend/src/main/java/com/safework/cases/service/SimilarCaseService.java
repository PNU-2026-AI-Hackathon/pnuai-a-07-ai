package com.safework.cases.service;

import com.safework.cases.dto.SimilarCaseResponse;
import com.safework.ml.client.MlServerClient;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import com.safework.checklist.repository.ChecklistResponseRepository;
import com.safework.risk.entity.RiskAssessment;
import com.safework.risk.repository.RiskAssessmentRepository;
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
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final ChecklistResponseRepository checklistResponseRepository;

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

        RiskAssessment assessment = riskAssessmentRepository
                .findFirstByWorkplaceIdOrderByAssessedAtDesc(workplaceId)
                .orElse(null);
        if (assessment == null || assessment.getSubmissionId() == null) {
            return search(workplace.getIndustry(), workplace.getSubIndustry(), topN);
        }

        var deficientItems = checklistResponseRepository.findDeficientItems(assessment.getSubmissionId());
        String context = buildDiagnosisContext(workplace, assessment, deficientItems);
        String basis = buildRecommendationBasis(assessment, deficientItems);

        return mlServerClient.analyzeCases(
                        workplace.getIndustry(), workplace.getSubIndustry(), context, topN)
                .map(result -> SimilarCaseResponse.of(
                        workplace.getIndustry(), workplace.getSubIndustry(), basis, result))
                .orElseGet(() -> SimilarCaseResponse.unavailable(
                        workplace.getIndustry(), workplace.getSubIndustry(), basis, UNAVAILABLE_NOTE));
    }

    public SimilarCaseResponse search(String industry, String subIndustry, int topN) {
        return mlServerClient.analyzeCases(industry, subIndustry, topN)
                .map(result -> SimilarCaseResponse.of(industry, subIndustry, result))
                .orElseGet(() -> SimilarCaseResponse.unavailable(industry, subIndustry, UNAVAILABLE_NOTE));
    }

    private String buildDiagnosisContext(
            Workplace workplace,
            RiskAssessment assessment,
            java.util.List<ChecklistResponseRepository.DeficientItem> items) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        parts.add(workplace.getIndustry());
        if (workplace.getSubIndustry() != null) parts.add(workplace.getSubIndustry());
        if (workplace.getMachineType() != null) parts.add(workplace.getMachineType() + " 설비");
        if (workplace.getSafetyDeviceStatus() != null) {
            parts.add("안전장치 상태 " + workplace.getSafetyDeviceStatus());
        }
        if (workplace.getStorageLocation() != null) parts.add(workplace.getStorageLocation() + " 적재 위치");
        if (workplace.getStorageMethod() != null) parts.add(workplace.getStorageMethod() + " 적재 방식");
        if (assessment.getTopAccidentType() != null) parts.add(assessment.getTopAccidentType() + " 위험");
        items.stream().limit(5).forEach(item -> {
            if (item.workType() != null) parts.add(item.workType());
            parts.add(item.category() + " " + item.question());
        });
        return String.join(" ", parts) + " 관련 사고";
    }

    private String buildRecommendationBasis(
            RiskAssessment assessment,
            java.util.List<ChecklistResponseRepository.DeficientItem> items) {
        java.util.List<String> categories = items.stream()
                .map(ChecklistResponseRepository.DeficientItem::category)
                .distinct()
                .limit(3)
                .toList();
        if (!categories.isEmpty()) {
            return "체크리스트 미비 위험(" + String.join(", ", categories) + ")과 현장 정보를 반영했습니다.";
        }
        return assessment.getTopAccidentType() == null
                ? "최신 안전진단과 현장 정보를 반영했습니다."
                : "최우선 재해유형 " + assessment.getTopAccidentType() + "과 현장 정보를 반영했습니다.";
    }
}
