package com.safework.auth.dto;

import com.safework.auth.entity.Member;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 로그인한 사용자 정보.
 *
 * 토큰만으로는 화면에 이름을 띄울 수 없어서 따로 준다.
 * (프론트가 JWT 를 직접 뜯어 읽게 하면 토큰 형식이 바뀔 때 화면이 같이 깨진다)
 *
 * 비밀번호 해시는 절대 담지 않는다.
 */
@Getter
public class MemberResponse {

    private final Long userId;
    private final String email;
    private final String name;
    private final String phone;
    /** OWNER / ADMIN — 지금은 가입 시 전부 OWNER 다 */
    private final String role;
    private final LocalDateTime createdAt;

    public MemberResponse(Member member) {
        this.userId = member.getId();
        this.email = member.getEmail();
        this.name = member.getName();
        this.phone = member.getPhone();
        this.role = member.getRole().name();
        this.createdAt = member.getCreatedAt();
    }
}
