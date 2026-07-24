package com.safework.workplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WorkplaceCreateRequest {

    @NotBlank(message = "대업종은 필수입니다")
    private String industryMajor;

    @NotBlank(message = "중업종은 필수입니다")
    private String industryMid;

    @NotBlank(message = "지역은 필수입니다")
    private String region;

    @NotNull(message = "근로자 수는 필수입니다")
    @Min(value = 1, message = "근로자 수는 1명 이상이어야 합니다")
    private Integer workerCount;

    private String companyName;

    private String address;
}