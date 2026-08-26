package com.safework.law.controller;

import com.safework.law.dto.LawSearchResponse;
import com.safework.law.service.LawSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "법령", description = "산업안전보건 법령 조문 검색 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
@Validated
public class LawSearchController {

    private final LawSearchService lawSearchService;

    @Operation(summary = "법령 조문 검색",
            description = "질문이나 키워드로 관련 조문을 찾습니다. "
                    + "일상어(예: 떨어질 것 같아요)도 산업안전 용어(추락)로 확장해 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<LawSearchResponse> search(
            @RequestParam @NotBlank(message = "검색어는 필수입니다") String q,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int size) {
        return ResponseEntity.ok(lawSearchService.search(q, size));
    }
}
