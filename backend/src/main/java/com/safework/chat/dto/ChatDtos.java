package com.safework.chat.dto;

import com.safework.chat.entity.ChatMessage;
import com.safework.chat.entity.ChatSession;
import com.safework.law.dto.LawSearchResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    @Getter
    @Setter
    public static class SessionCreateRequest {
        /** 사업장을 지정하면 이후 답변에 사업장 맥락을 실을 수 있다. 선택. */
        private Long workplaceId;
    }

    @Getter
    public static class SessionResponse {
        private final UUID sessionId;
        private final Long workplaceId;
        private final String title;
        private final OffsetDateTime createdAt;

        public SessionResponse(ChatSession session) {
            this.sessionId = session.getId();
            this.workplaceId = session.getWorkplaceId();
            this.title = session.getTitle();
            this.createdAt = session.getCreatedAt();
        }
    }

    @Getter
    @Setter
    public static class AskRequest {
        @NotBlank(message = "질문을 입력해 주세요")
        private String question;
    }

    /** 답변이 어떻게 만들어졌는지 */
    public enum AnswerMode {
        /** LLM 이 조문을 근거로 답변을 생성함 */
        GENERATED,
        /** LLM 을 쓸 수 없어 관련 조문만 반환함 */
        RETRIEVAL_ONLY
    }

    @Getter
    public static class AskResponse {
        private final UUID sessionId;
        private final String question;
        private final AnswerMode mode;
        /** 생성된 답변. RETRIEVAL_ONLY 면 null */
        private final String answer;
        /** 답변 근거 조문. mode 와 무관하게 항상 채워진다 */
        private final List<LawSearchResponse.LawArticleDto> citedArticles;
        /** RETRIEVAL_ONLY 인 이유 */
        private final String note;
        private final String modelName;

        public AskResponse(UUID sessionId, String question, AnswerMode mode, String answer,
                           List<LawSearchResponse.LawArticleDto> citedArticles,
                           String note, String modelName) {
            this.sessionId = sessionId;
            this.question = question;
            this.mode = mode;
            this.answer = answer;
            this.citedArticles = citedArticles;
            this.note = note;
            this.modelName = modelName;
        }
    }

    @Getter
    public static class MessageResponse {
        private final Long messageId;
        private final String role;
        private final String content;
        private final List<Long> citedArticles;
        private final String modelName;
        private final OffsetDateTime createdAt;

        public MessageResponse(ChatMessage message) {
            this.messageId = message.getId();
            this.role = message.getRole().name();
            this.content = message.getContent();
            this.citedArticles = message.getCitedArticles() == null
                    ? List.of() : Arrays.asList(message.getCitedArticles());
            this.modelName = message.getModelName();
            this.createdAt = message.getCreatedAt();
        }
    }
}
