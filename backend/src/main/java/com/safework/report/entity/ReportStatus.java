package com.safework.report.entity;

/** report.status 의 PostgreSQL enum(report_status_t) 대응 */
public enum ReportStatus {
    PENDING,
    GENERATING,
    DONE,
    FAILED
}
