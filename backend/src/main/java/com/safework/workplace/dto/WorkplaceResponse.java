package com.safework.workplace.dto;

import com.safework.workplace.entity.Workplace;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class WorkplaceResponse {

    private Long id;
    private String industryMajor;
    private String industryMid;
    private String region;
    private Integer workerCount;
    private String companyName;
    private String address;
    private LocalDateTime createdAt;

    public static WorkplaceResponse from(Workplace workplace) {
        return new WorkplaceResponse(
                workplace.getId(),
                workplace.getIndustryMajor(),
                workplace.getIndustryMid(),
                workplace.getRegion(),
                workplace.getWorkerCount(),
                workplace.getCompanyName(),
                workplace.getAddress(),
                workplace.getCreatedAt()
        );
    }
}