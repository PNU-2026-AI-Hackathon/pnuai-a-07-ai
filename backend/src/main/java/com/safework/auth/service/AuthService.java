package com.safework.auth.service;

import com.safework.auth.dto.LoginRequest;
import com.safework.auth.dto.MemberResponse;
import com.safework.auth.dto.PasswordChangeRequest;
import com.safework.auth.dto.ProfileUpdateRequest;
import com.safework.auth.dto.SignUpRequest;
import com.safework.auth.dto.TokenResponse;
import com.safework.auth.entity.Member;
import com.safework.auth.repository.MemberRepository;
import com.safework.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse signUp(SignUpRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        Member member = Member.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .build();

        memberRepository.save(member);

        String token = jwtTokenProvider.createToken(member.getId(), member.getEmail());
        return TokenResponse.of(token);
    }

    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.createToken(member.getId(), member.getEmail());
        return TokenResponse.of(token);
    }

    /**
     * 토큰의 주인 정보.
     *
     * 토큰은 유효한데 사용자가 없을 수 있다(탈퇴, DB 초기화, 다른 환경에서 발급한 토큰).
     * 그때 500 이 아니라 "사용자를 찾을 수 없습니다"로 내려보내 프론트가 로그아웃 처리하게 한다.
     */
    public MemberResponse getMe(Long memberId) {
        return new MemberResponse(findMember(memberId));
    }

    /** 이름·연락처 수정. 바꾼 내용을 그대로 돌려줘서 프론트가 다시 조회하지 않아도 되게 한다. */
    @Transactional
    public MemberResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateProfile(request.getName(), request.getPhone());
        return new MemberResponse(member);
    }

    /**
     * 비밀번호 변경.
     *
     * 현재 비밀번호가 틀리면 "현재 비밀번호가 올바르지 않습니다"로 명확히 알려준다.
     * 로그인과 달리 이미 본인이 로그인한 상태라, 여기서 뭉뚱그려도 얻을 보안 이득이 없고
     * 사용자만 헤맨다.
     */
    @Transactional
    public void changePassword(Long memberId, PasswordChangeRequest request) {
        Member member = findMember(memberId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), member.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호가 현재 비밀번호와 같습니다.");
        }
        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}