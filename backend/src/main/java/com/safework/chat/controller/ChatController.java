package com.safework.chat.controller;

import com.safework.chat.dto.ChatDtos;
import com.safework.chat.service.ChatService;
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
import java.util.UUID;

@Tag(name = "법령 상담", description = "관련 조문을 근거로 답하는 상담 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "대화 시작")
    @PostMapping("/sessions")
    public ResponseEntity<ChatDtos.SessionResponse> createSession(
            Authentication authentication,
            @RequestBody(required = false) ChatDtos.SessionCreateRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        Long workplaceId = request == null ? null : request.getWorkplaceId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createSession(memberId, workplaceId));
    }

    @Operation(summary = "내 대화 목록")
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatDtos.SessionResponse>> listSessions(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.listSessions(memberId));
    }

    @Operation(summary = "질문하기",
            description = "관련 법령 조문을 찾아 그 조문만 근거로 답변합니다. "
                    + "답변 생성 모델이 설정되지 않은 경우 조문만 반환합니다(mode=RETRIEVAL_ONLY).")
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatDtos.AskResponse> ask(
            Authentication authentication,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.AskRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.ask(memberId, sessionId, request.getQuestion()));
    }

    @Operation(summary = "대화 이력 조회")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatDtos.MessageResponse>> messages(
            Authentication authentication,
            @PathVariable UUID sessionId) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.getMessages(memberId, sessionId));
    }
}
