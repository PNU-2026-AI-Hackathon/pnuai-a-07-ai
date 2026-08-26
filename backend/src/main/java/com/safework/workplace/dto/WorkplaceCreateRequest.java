package com.safework.workplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 200, message = "기계·설비 정보는 200자 이하여야 합니다")
    private String machineType;

    @Min(value = 0, message = "기계·설비 수는 0대 이상이어야 합니다")
    private Integer machineCount;

    @Pattern(regexp = "INSTALLED|PARTIAL|NONE|UNKNOWN",
            message = "안전장치 상태는 INSTALLED/PARTIAL/NONE/UNKNOWN 중 하나여야 합니다")
    private String safetyDeviceStatus;

    @Size(max = 200, message = "적재 위치는 200자 이하여야 합니다")
    private String storageLocation;

    @Size(max = 200, message = "적재 방식은 200자 이하여야 합니다")
    private String storageMethod;
}
