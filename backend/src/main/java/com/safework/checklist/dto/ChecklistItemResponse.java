package com.safework.checklist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.safework.checklist.entity.ChecklistItem;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ChecklistItemResponse {

    private final String itemCode;
    private final String category;
    private final String workType;
    private final String question;
    private final String description;
    private final BigDecimal riskWeight;

    // 필드명을 critical 로 두어야 getter(isCritical())와 같은 property 로 합쳐진다.
    @JsonProperty("isCritical")
    private final boolean critical;

    public ChecklistItemResponse(ChecklistItem item) {
        this.itemCode = item.getItemCode();
        this.category = item.getCategory();
        this.workType = item.getWorkType();
        this.question = item.getQuestion();
        this.description = item.getDescription();
        this.riskWeight = item.getRiskWeight();
        this.critical = item.isCritical();
    }
}
