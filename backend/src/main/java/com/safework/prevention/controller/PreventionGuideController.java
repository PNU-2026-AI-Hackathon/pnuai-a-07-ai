package com.safework.prevention.controller;

import com.safework.prevention.dto.PreventionGuideRequest;
import com.safework.prevention.dto.PreventionGuideResponse;
import com.safework.prevention.service.PreventionGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예방 가이드", description = "업종/규모/지역 기반 예상 사고유형 및 체크리스트 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/prevention-guide")
@RequiredArgsConstructor
public class PreventionGuideController {

    private final PreventionGuideService preventionGuideService;

    @Operation(summary = "예방 가이드 조회", description = "업종/규모/지역별 예상 사고유형 Top-N과 사고별 안전조치 체크리스트를 반환합니다.")
    @GetMapping
    public ResponseEntity<PreventionGuideResponse> getGuide(@Valid PreventionGuideRequest request) {
        return ResponseEntity.ok(preventionGuideService.getGuide(request));
    }
}
