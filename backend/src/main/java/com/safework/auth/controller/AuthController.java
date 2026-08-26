package com.safework.auth.controller;

import com.safework.auth.dto.LoginRequest;
import com.safework.auth.dto.MemberResponse;
import com.safework.auth.dto.PasswordChangeRequest;
import com.safework.auth.dto.ProfileUpdateRequest;
import com.safework.auth.dto.SignUpRequest;
import com.safework.auth.dto.TokenResponse;
import com.safework.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입/로그인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signUp(request));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "내 정보 조회",
            description = "토큰의 주인 정보를 반환합니다. 화면에 이름을 띄우거나 "
                    + "저장해 둔 토큰이 아직 유효한지 확인할 때 사용하세요.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/me")
    public ResponseEntity<MemberResponse> me(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getMe(memberId));
    }

    @Operation(summary = "내 정보 수정",
            description = "이름과 연락처를 바꿉니다. 보내지 않은 항목은 그대로 둡니다. "
                    + "이메일은 로그인 아이디라 바꿀 수 없고, 비밀번호는 아래 API 를 쓰세요.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/me")
    public ResponseEntity<MemberResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.updateProfile(memberId, request));
    }

    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호를 함께 보내야 합니다. 성공하면 204 를 돌려줍니다. "
                    + "기존 토큰은 그대로 쓸 수 있습니다(다시 로그인하지 않아도 됩니다).")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        authService.changePassword(memberId, request);
        return ResponseEntity.noContent().build();
    }
}