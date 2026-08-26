package com.safework.report.dto;

import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class ReportCreateResponse {

    private final Long reportId;
    private final String status;
    private final Integer fileSize;
    private final OffsetDateTime generatedAt;

    public ReportCreateResponse(Long reportId, String status, Integer fileSize, OffsetDateTime generatedAt) {
        this.reportId = reportId;
        this.status = status;
        this.fileSize = fileSize;
        this.generatedAt = generatedAt;
    }
}
