package com.safework.risk.controller;

import com.safework.risk.dto.RiskAssessmentResponse;
import com.safework.risk.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "위험도 진단", description = "사업장 위험도 진단 결과 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/workplaces/{workplaceId}")
@RequiredArgsConstructor
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    @Operation(summary = "최신 위험도 진단 결과 조회")
    @GetMapping("/risk-assessments/latest")
    public ResponseEntity<RiskAssessmentResponse> getLatest(
            Authentication authentication,
            @PathVariable Long workplaceId) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(riskAssessmentService.getLatest(memberId, workplaceId));
    }
}
