package com.safework.reference.controller;

import com.safework.reference.dto.ReferenceResponse;
import com.safework.reference.service.ReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "코드값", description = "화면에서 고를 수 있는 값 (업종·규모·지역·재해유형·작업종류)")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/references")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    @Operation(summary = "코드값 전체 조회",
            description = """
                    사업장 등록 폼 등에서 쓸 코드값을 한 번에 반환합니다.
                    DB 의 v_ref_* 뷰를 그대로 내보내므로 값을 화면에 하드코딩하지 마세요.
                    다섯 종류를 합쳐도 120여 건이라, 앱을 켤 때 한 번 받아 두고 쓰시면 됩니다.
                    """)
    @GetMapping
    public ResponseEntity<ReferenceResponse> getAll() {
        return ResponseEntity.ok(referenceService.getAll());
    }
}
