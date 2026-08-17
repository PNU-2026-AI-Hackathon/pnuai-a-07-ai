package com.safework.prevention.controller;

import com.safework.prevention.dto.PreventionGuideResponse;
import com.safework.prevention.service.PreventionGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예방 가이드", description = "체크리스트 진단 기반 맞춤 예방가이드 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequiredArgsConstructor
@Validated
public class DiagnosisPreventionGuideController {

    private final PreventionGuideService preventionGuideService;

    @Operation(summary = "최신 진단 기반 맞춤 예방가이드")
    @GetMapping("/api/workplaces/{workplaceId}/prevention-guide")
    public ResponseEntity<PreventionGuideResponse> forWorkplace(
            Authentication authentication,
            @PathVariable Long workplaceId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(5) int accidentCount,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int itemsPerAccident) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(preventionGuideService.getGuideForDiagnosis(
                memberId, workplaceId, accidentCount, itemsPerAccident));
    }
}
