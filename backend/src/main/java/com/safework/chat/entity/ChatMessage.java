package com.safework.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 대화 메시지 한 줄.
 *
 * 답변에는 근거로 삼은 조문(cited_articles)을 함께 남긴다. 법령 상담이라
 * "무엇을 보고 이렇게 답했는지" 추적할 수 없으면 신뢰하기 어렵다.
 */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false)
    private String content;

    /** 답변 근거가 된 law_article.article_id 목록 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cited_articles")
    private Long[] citedArticles;

    /** 답변 근거가 된 sif_case.sif_id 목록 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cited_sif")
    private Long[] citedSif;

    /** 어떤 모델이 답했는지. 검색 결과만 돌려준 경우 null */
    @Column(name = "model_name")
    private String modelName;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ChatMessage(UUID sessionId, ChatRole role, String content, Long[] citedArticles,
                       String modelName, Integer tokenUsage, Integer latencyMs) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citedArticles = citedArticles;
        this.modelName = modelName;
        this.tokenUsage = tokenUsage;
        this.latencyMs = latencyMs;
    }
}
