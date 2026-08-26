package com.safework.workplace.controller;

import com.safework.workplace.dto.WorkplaceCreateRequest;
import com.safework.workplace.dto.WorkplaceResponse;
import com.safework.workplace.service.WorkplaceService;
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

@Tag(name = "사업장", description = "사업장 등록/조회/수정 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/workplaces")
@RequiredArgsConstructor
public class WorkplaceController {

    private final WorkplaceService workplaceService;

    @Operation(summary = "사업장 등록")
    @PostMapping
    public ResponseEntity<WorkplaceResponse> create(
            Authentication authentication,
            @Valid @RequestBody WorkplaceCreateRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workplaceService.create(memberId, request));
    }

    @Operation(summary = "내 사업장 목록 조회")
    @GetMapping
    public ResponseEntity<List<WorkplaceResponse>> getMyWorkplaces(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(workplaceService.getMyWorkplaces(memberId));
    }

    @Operation(summary = "사업장 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<WorkplaceResponse> getWorkplace(
            Authentication authentication,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(workplaceService.getWorkplace(memberId, id));
    }

    @Operation(summary = "사업장 수정")
    @PutMapping("/{id}")
    public ResponseEntity<WorkplaceResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody WorkplaceCreateRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(workplaceService.update(memberId, id, request));
    }
}