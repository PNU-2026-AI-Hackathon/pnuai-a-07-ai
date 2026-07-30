package com.safework.prevention.repository;

import java.math.BigDecimal;
import java.util.List;

public record PreventionGuideRow(
        int rank,
        String accidentType,
        BigDecimal ratio,
        BigDecimal deathRatio,
        String itemCode,
        String workType,
        String question,
        BigDecimal riskWeight,
        boolean critical,
        List<String> lawBasis
) {
}
