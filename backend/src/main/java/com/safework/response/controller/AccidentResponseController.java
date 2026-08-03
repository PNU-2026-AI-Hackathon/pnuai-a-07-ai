package com.safework.response.controller;

import com.safework.response.dto.AccidentResponseGuide;
import com.safework.response.service.AccidentResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사고 대처", description = "사고 발생 시 대처 가이드 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/accident-response")
@RequiredArgsConstructor
@Validated
public class AccidentResponseController {

    private final AccidentResponseService accidentResponseService;

    @Operation(summary = "사고 대처 가이드 조회",
            description = "사고 직후 조치 절차(법적 근거 포함), 재해유형별 근거 법령, "
                    + "같은 유형의 중대재해 사례와 재발방지 대책을 함께 반환합니다.")
    @GetMapping
    public ResponseEntity<AccidentResponseGuide> getGuide(
            @RequestParam @NotBlank(message = "재해유형은 필수입니다") String accidentType,
            @RequestParam @NotBlank(message = "업종은 필수입니다") String industry) {
        return ResponseEntity.ok(accidentResponseService.getGuide(accidentType, industry));
    }
}
