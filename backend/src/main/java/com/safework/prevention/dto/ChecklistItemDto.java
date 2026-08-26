package com.safework.prevention.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // 필드명을 critical 로 두어야 getter(isCritical())와 같은 property 로 합쳐진다.
    // 그 위에서 프론트 계약대로 isCritical 로 이름을 고정.
    @JsonProperty("isCritical")
    private final boolean critical;

    private final List<String> lawBasis;

    public ChecklistItemDto(PreventionGuideRow row) {
        this.itemCode = row.itemCode();
        this.workType = row.workType();
        this.question = row.question();
        this.riskWeight = row.riskWeight();
        this.critical = row.critical();
        this.lawBasis = row.lawBasis();
    }
}
