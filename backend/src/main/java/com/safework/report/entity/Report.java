package com.safework.report.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 생성된 PDF 리포트의 메타데이터. 파일 자체는 file_path 에 저장한다. */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "workplace_id", nullable = false)
    private Long workplaceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public Report(Long assessmentId, Long workplaceId, ReportStatus status) {
        this.assessmentId = assessmentId;
        this.workplaceId = workplaceId;
        this.status = status;
    }

    public void markDone(String filePath, int fileSize) {
        this.status = ReportStatus.DONE;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.generatedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = ReportStatus.FAILED;
    }
}
