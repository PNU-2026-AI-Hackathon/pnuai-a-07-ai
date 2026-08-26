package com.safework.auth.entity;

import com.safework.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private MemberRole role = MemberRole.OWNER;

    /**
     * 이름·연락처 변경.
     *
     * 이메일은 바꾸지 않는다. 로그인 아이디이자 유일 키라서, 바꾸려면 중복 확인과
     * 기존 토큰 처리가 함께 필요하다. 지금은 그 요구가 없으므로 열어 두지 않는다.
     */
    public void updateProfile(String name, String phone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        // 전화번호는 지우는 것도 의미가 있으므로 빈 문자열을 null 로 받아 준다.
        if (phone != null) {
            this.phone = phone.isBlank() ? null : phone;
        }
    }

    /** 이미 해시된 비밀번호를 넣는다. 평문을 그대로 넣지 않도록 이름에 hashed 를 붙였다. */
    public void changePassword(String hashedPassword) {
        this.password = hashedPassword;
    }
}
