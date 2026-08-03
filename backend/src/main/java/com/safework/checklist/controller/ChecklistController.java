package com.safework.checklist.controller;

import com.safework.checklist.dto.ChecklistItemResponse;
import com.safework.checklist.dto.ChecklistSubmitRequest;
import com.safework.checklist.dto.ChecklistSubmitResponse;
import com.safework.checklist.service.ChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "체크리스트", description = "안전조치 점검 문항 조회 및 제출 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/workplaces/{workplaceId}")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @Operation(summary = "점검 문항 목록 조회",
            description = "사업장 업종에 해당하는 점검 문항을 반환합니다. 문항 수가 많으므로 필터 사용을 권장합니다.")
    @GetMapping("/checklist-items")
    public ResponseEntity<List<ChecklistItemResponse>> getItems(
            Authentication authentication,
            @PathVariable Long workplaceId,
            @RequestParam(defaultValue = "false") boolean criticalOnly,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) String category) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(
                checklistService.getItems(memberId, workplaceId, criticalOnly, workType, category));
    }

    @Operation(summary = "체크리스트 제출 (위험도 진단 포함)",
            description = "응답을 저장하고 곧바로 위험도 진단을 수행해 결과까지 반환합니다.")
    @PostMapping("/checklist-submissions")
    public ResponseEntity<ChecklistSubmitResponse> submit(
            Authentication authentication,
            @PathVariable Long workplaceId,
            @Valid @RequestBody ChecklistSubmitRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.submit(memberId, workplaceId, request));
    }
}
