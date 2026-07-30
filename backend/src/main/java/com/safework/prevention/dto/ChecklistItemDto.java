package com.safework.prevention.dto;

import com.safework.prevention.repository.PreventionGuideRow;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class ChecklistItemDto {

    private final String itemCode;
    private final String workType;
    private final String question;
    private final BigDecimal riskWeight;
    private final boolean isCritical;
    private final List<String> lawBasis;

    public ChecklistItemDto(PreventionGuideRow row) {
        this.itemCode = row.itemCode();
        this.workType = row.workType();
        this.question = row.question();
        this.riskWeight = row.riskWeight();
        this.isCritical = row.critical();
        this.lawBasis = row.lawBasis();
    }
}
