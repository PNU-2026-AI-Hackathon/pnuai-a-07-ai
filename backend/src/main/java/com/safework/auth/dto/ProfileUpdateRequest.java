package com.safework.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 내 정보 수정.
 *
 * 이메일과 비밀번호는 여기서 바꾸지 않는다. 이메일은 로그인 아이디라 중복 확인과
 * 토큰 처리가 따로 필요하고, 비밀번호는 현재 비밀번호 확인이 필요해서 별도 API 로 뒀다.
 */
@Getter
@Setter
public class ProfileUpdateRequest {

    /** 안 보내면 그대로 둔다 */
    @Size(max = 50, message = "이름은 50자까지 입력할 수 있습니다")
    private String name;

    /** 빈 문자열을 보내면 지운다 */
    @Size(max = 20, message = "연락처는 20자까지 입력할 수 있습니다")
    private String phone;
}
