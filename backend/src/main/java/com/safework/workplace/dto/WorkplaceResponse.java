package com.safework.workplace.dto;

import com.safework.workplace.entity.Workplace;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class WorkplaceResponse {

    private Long id;
    private String name;
    private String industry;
    private String subIndustry;
    private String region;
    private Integer employeeCount;
    private String sizeClass;
    private String address;
    private LocalDateTime createdAt;

    public static WorkplaceResponse from(Workplace workplace) {
        return new WorkplaceResponse(
                workplace.getId(),
                workplace.getName(),
                workplace.getIndustry(),
                workplace.getSubIndustry(),
                workplace.getRegion(),
                workplace.getEmployeeCount(),
                workplace.getSizeClass(),
                workplace.getAddress(),
                workplace.getCreatedAt()
        );
    }
}
