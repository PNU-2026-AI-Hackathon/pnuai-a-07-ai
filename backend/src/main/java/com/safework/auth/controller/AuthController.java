package com.safework.auth.controller;

import com.safework.auth.dto.LoginRequest;
import com.safework.auth.dto.MemberResponse;
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
}