package com.safework.prevention.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreventionGuideRequest {

    @NotBlank(message = "업종은 필수입니다")
    private String industry;

    @NotBlank(message = "규모는 필수입니다")
    private String sizeClass;

    @NotBlank(message = "지역은 필수입니다")
    private String region;

    @Min(value = 1, message = "예상 사고 수는 1개 이상이어야 합니다")
    private int expectedAccidentCount = 3;

    @Min(value = 1, message = "사고당 항목 수는 1개 이상이어야 합니다")
    private int itemsPerAccident = 3;
}
