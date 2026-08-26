package com.safework.report.controller;

import com.safework.report.dto.ReportCreateResponse;
import com.safework.report.entity.Report;
import com.safework.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "리포트", description = "안전관리 진단 PDF 리포트 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "PDF 리포트 생성",
            description = "최신 위험도 진단 결과를 바탕으로 PDF 리포트를 만들고 reportId 를 반환합니다.")
    @PostMapping("/api/workplaces/{workplaceId}/reports")
    public ResponseEntity<ReportCreateResponse> create(
            Authentication authentication,
            @PathVariable Long workplaceId) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.create(memberId, workplaceId));
    }

    @Operation(summary = "PDF 리포트 다운로드")
    @GetMapping("/api/reports/{reportId}/download")
    public ResponseEntity<byte[]> download(
            Authentication authentication,
            @PathVariable Long reportId) {
        Long memberId = (Long) authentication.getPrincipal();
        Report report = reportService.getForDownload(memberId, reportId);
        byte[] body = reportService.readFile(report);

        String fileName = URLEncoder.encode("안전관리_진단_리포트_" + reportId + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .body(body);
    }
}
