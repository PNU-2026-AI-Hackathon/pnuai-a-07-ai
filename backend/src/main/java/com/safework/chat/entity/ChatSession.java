package com.safework.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 법령 상담 대화 한 건. 사업장을 붙여 두면 답변에 사업장 맥락을 실을 수 있다. */
@Entity
@Table(name = "chat_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workplace_id")
    private Long workplaceId;

    /** 목록에서 알아보기 위한 제목. 첫 질문으로 채운다. */
    private String title;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ChatSession(Long userId, Long workplaceId, String title) {
        this.userId = userId;
        this.workplaceId = workplaceId;
        this.title = title;
    }

    public void titleIfEmpty(String candidate) {
        if ((title == null || title.isBlank()) && candidate != null && !candidate.isBlank()) {
            this.title = candidate.length() > 100 ? candidate.substring(0, 100) : candidate;
        }
    }
}
