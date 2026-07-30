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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreventionGuideService {

    private final PreventionGuideRepository preventionGuideRepository;

    public PreventionGuideResponse getGuide(PreventionGuideRequest request) {
        List<PreventionGuideRow> rows = preventionGuideRepository.fetch(
                request.getIndustry(),
                request.getSizeClass(),
                request.getRegion(),
                request.getExpectedAccidentCount(),
                request.getItemsPerAccident());

        Map<Integer, List<PreventionGuideRow>> byRank = rows.stream()
                .collect(Collectors.groupingBy(PreventionGuideRow::rank, LinkedHashMap::new, Collectors.toList()));

        List<AccidentGuideDto> predictions = byRank.entrySet().stream()
                .map(entry -> toAccidentGuide(entry.getKey(), entry.getValue()))
                .toList();

        return new PreventionGuideResponse(predictions);
    }

    private AccidentGuideDto toAccidentGuide(int rank, List<PreventionGuideRow> rowsForAccident) {
        PreventionGuideRow first = rowsForAccident.get(0);
        List<ChecklistItemDto> checklist = rowsForAccident.stream()
                .map(ChecklistItemDto::new)
                .toList();

        return new AccidentGuideDto(rank, first.accidentType(), first.ratio(), first.deathRatio(), checklist);
    }
}
