package com.safework.response.controller;

import com.safework.response.dto.AccidentConsultDtos;
import com.safework.response.dto.AccidentResponseGuide;
import com.safework.response.service.AccidentConsultService;
import com.safework.response.service.AccidentResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AccidentConsultService accidentConsultService;

    @Operation(summary = "사고 대처 가이드 조회",
            description = "사고 직후 조치 절차(법적 근거 포함), 재해유형별 근거 법령, "
                    + "같은 유형의 중대재해 사례와 재발방지 대책을 함께 반환합니다.")
    @GetMapping
    public ResponseEntity<AccidentResponseGuide> getGuide(
            @RequestParam @NotBlank(message = "재해유형은 필수입니다") String accidentType,
            @RequestParam @NotBlank(message = "업종은 필수입니다") String industry) {
        return ResponseEntity.ok(accidentResponseService.getGuide(accidentType, industry));
    }

    @Operation(summary = "사고 상황 서술 기반 대처 안내",
            description = """
                    어떤 사고가 났는지 그대로 적으면 재해유형을 추정해 대처 방법을 안내합니다.
                    응답은 즉시 조치 · 법적 의무 · 행정 처리 · 위반 시 처벌 네 덩어리로 나뉘며,
                    각 항목에 근거 조문이 붙습니다. 목록은 법령에서 정리해 둔 것이라 답변 생성
                    모델이 없어도 항상 내려가고, 모델이 있으면 이 사고 상황에 맞춘 설명(guidance)이
                    각 덩어리에 함께 채워집니다(mode=GENERATED).
                    """)
    @PostMapping("/consult")
    public ResponseEntity<AccidentConsultDtos.Response> consult(
            @Valid @RequestBody AccidentConsultDtos.Request request) {
        return ResponseEntity.ok(accidentConsultService.consult(request));
    }
}
