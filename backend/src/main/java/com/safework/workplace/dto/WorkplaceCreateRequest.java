package com.safework.workplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class WorkplaceCreateRequest {

    @NotBlank(message = "대업종은 필수입니다")
    private String industry;

    private String subIndustry;

    @NotBlank(message = "지역은 필수입니다")
    private String region;

    @Min(value = 0, message = "근로자 수는 0명 이상이어야 합니다")
    private Integer employeeCount;

    @NotBlank(message = "규모는 필수입니다")
    private String sizeClass;

    @NotBlank(message = "사업장명은 필수입니다")
    private String name;

    private String address;
}
