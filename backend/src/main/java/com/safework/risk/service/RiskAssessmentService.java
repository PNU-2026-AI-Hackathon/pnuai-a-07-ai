package com.safework.risk.service;

import com.safework.risk.dto.RiskAssessmentResponse;
import com.safework.risk.repository.RiskAssessmentRepository;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskAssessmentService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final WorkplaceRepository workplaceRepository;

    public RiskAssessmentResponse getLatest(Long memberId, Long workplaceId) {
        workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));

        return riskAssessmentRepository.findFirstByWorkplaceIdOrderByAssessedAtDesc(workplaceId)
                .map(RiskAssessmentResponse::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "아직 위험도 진단 결과가 없습니다. 체크리스트를 먼저 제출해 주세요."));
    }
}
