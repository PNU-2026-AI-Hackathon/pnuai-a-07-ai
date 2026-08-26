package com.safework.cases.controller;

import com.safework.cases.dto.SimilarCaseResponse;
import com.safework.cases.service.SimilarCaseService;
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

@Tag(name = "유사 재해사례", description = "우리 사업장과 비슷한 중대재해 사례 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequiredArgsConstructor
@Validated
public class SimilarCaseController {

    private final SimilarCaseService similarCaseService;

    @Operation(summary = "우리 사업장 유사 재해사례",
            description = "사업장의 업종·세부업종을 기준으로 비슷한 중대재해 사례와 재발방지 대책을 반환합니다.")
    @GetMapping("/api/workplaces/{workplaceId}/similar-cases")
    public ResponseEntity<SimilarCaseResponse> forWorkplace(
            Authentication authentication,
            @PathVariable Long workplaceId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int size) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(similarCaseService.findForWorkplace(memberId, workplaceId, size));
    }
}
