package com.safework.prevention.service;

import com.safework.prevention.dto.AccidentGuideDto;
import com.safework.prevention.dto.ChecklistItemDto;
import com.safework.prevention.dto.PreventionGuideRequest;
import com.safework.prevention.dto.PreventionGuideResponse;
import com.safework.prevention.repository.PreventionGuideRepository;
import com.safework.prevention.repository.PreventionGuideRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.safework.workplace.repository.WorkplaceRepository;
import com.safework.risk.repository.RiskAssessmentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreventionGuideService {

    private final PreventionGuideRepository preventionGuideRepository;
    private final WorkplaceRepository workplaceRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;

    public PreventionGuideResponse getGuide(PreventionGuideRequest request) {
        List<PreventionGuideRow> rows = preventionGuideRepository.fetch(
                request.getIndustry(),
                request.getSizeClass(),
                request.getRegion(),
                request.getExpectedAccidentCount(),
                request.getItemsPerAccident());

        return toResponse(rows);
    }

    public PreventionGuideResponse getGuideForDiagnosis(Long memberId, Long workplaceId,
                                                        int accidentCount, int itemsPerAccident) {
        workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
        riskAssessmentRepository.findFirstByWorkplaceIdOrderByAssessedAtDesc(workplaceId)
                .orElseThrow(() -> new IllegalArgumentException("체크리스트를 먼저 제출해 주세요."));
        return toResponse(preventionGuideRepository.fetchForLatestDiagnosis(
                workplaceId, accidentCount, itemsPerAccident));
    }

    private PreventionGuideResponse toResponse(List<PreventionGuideRow> rows) {
        Map<Integer, List<PreventionGuideRow>> byRank = rows.stream()
                .collect(Collectors.groupingBy(PreventionGuideRow::rank, LinkedHashMap::new, Collectors.toList()));

        List<AccidentGuideDto> predictions = byRank.entrySet().stream()
                .map(entry -> toAccidentGuide(entry.getKey(), entry.getValue()))
                .toList();

        return new PreventionGuideResponse(predictions);
    }

    private AccidentGuideDto toAccidentGuide(int rank, List<PreventionGuideRow> rowsForAccident) {
        PreventionGuideRow first = rowsForAccident.get(0);

        // DB 함수가 LEFT JOIN 이라, 점검항목이 없는 사고유형은 item_code 가 NULL 인
        // 행 하나로 내려온다(rank 연속성 유지용). 이런 행은 체크리스트에서 제외한다.
        List<ChecklistItemDto> checklist = rowsForAccident.stream()
                .filter(row -> row.itemCode() != null)
                .map(ChecklistItemDto::new)
                .toList();

        return new AccidentGuideDto(rank, first.accidentType(), first.ratio(), first.deathRatio(), checklist);
    }
}
