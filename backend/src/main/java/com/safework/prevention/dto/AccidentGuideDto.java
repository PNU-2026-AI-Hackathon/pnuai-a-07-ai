package com.safework.prevention.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class AccidentGuideDto {

    private final int rank;
    private final String accidentType;
    private final BigDecimal ratio;
    private final BigDecimal deathRatio;
    private final List<ChecklistItemDto> checklist;

    public AccidentGuideDto(int rank, String accidentType, BigDecimal ratio,
                             BigDecimal deathRatio, List<ChecklistItemDto> checklist) {
        this.rank = rank;
        this.accidentType = accidentType;
        this.ratio = ratio;
        this.deathRatio = deathRatio;
        this.checklist = checklist;
    }
}
