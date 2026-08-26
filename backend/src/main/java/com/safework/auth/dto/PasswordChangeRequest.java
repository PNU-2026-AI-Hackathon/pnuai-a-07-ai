package com.safework.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 비밀번호 변경.
 *
 * 현재 비밀번호를 함께 받는다. 토큰만으로 바꾸게 하면, 자리를 비운 사이 남이 브라우저를
 * 만졌을 때 비밀번호까지 바뀌어 계정을 통째로 잃는다.
 */
@Getter
@Setter
public class PasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호는 필수입니다")
    private String currentPassword;

    /**
     * 가입 때는 길이 제한이 없지만 변경은 최소 8자를 받는다.
     * 기존 사용자를 막지 않으면서 앞으로 만들어지는 비밀번호는 조금이라도 낫게 하려는 것이다.
     */
    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상 64자 이하여야 합니다")
    private String newPassword;
}
