package com.safework.report.dto;

import com.safework.prevention.dto.AccidentGuideDto;
import com.safework.prevention.dto.ChecklistItemDto;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 리포트 템플릿용 — 예상 재해유형 1건. 비율은 미리 % 로 환산해 템플릿을 단순하게 유지한다. */
@Getter
public class PredictionView {

    private final int rank;
    private final String accidentType;
    private final BigDecimal ratioPercent;
    private final BigDecimal deathRatioPercent;
    private final List<ChecklistItemDto> checklist;

    public PredictionView(AccidentGuideDto guide) {
        this.rank = guide.getRank();
        this.accidentType = guide.getAccidentType();
        this.ratioPercent = toPercent(guide.getRatio());
        this.deathRatioPercent = toPercent(guide.getDeathRatio());
        this.checklist = guide.getChecklist();
    }

    private static BigDecimal toPercent(BigDecimal ratio) {
        if (ratio == null) {
            return BigDecimal.ZERO;
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }
}
