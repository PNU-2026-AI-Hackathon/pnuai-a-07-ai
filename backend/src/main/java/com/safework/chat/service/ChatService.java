package com.safework.chat.service;

import com.safework.chat.dto.ChatDtos;
import com.safework.chat.entity.ChatMessage;
import com.safework.chat.entity.ChatRole;
import com.safework.chat.entity.ChatSession;
import com.safework.chat.repository.ChatMessageRepository;
import com.safework.chat.repository.ChatSessionRepository;
import com.safework.law.dto.LawSearchResponse;
import com.safework.law.service.LawSearchService;
import com.safework.llm.LlmClient;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 법령 상담 대화.
 *
 * 흐름은 RAG 그대로다.
 *   질문 → 관련 조문 검색(R) → 조문을 프롬프트에 담기(A) → 답변 생성(G)
 *
 * LLM 키가 없으면 G 를 건너뛰고 조문만 돌려준다. 챗봇이 안 될 뿐 "관련 법령 찾기"는
 * 계속 동작하므로, 키를 넣는 순간 같은 API 가 답변까지 하게 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int SEARCH_SIZE = 5;
    private static final String NO_LLM_NOTE =
            "답변 생성 모델이 설정되지 않아 관련 법령 조문만 보여드립니다.";
    private static final String NO_ANSWER_NOTE =
            "답변을 생성하지 못했습니다. 관련 법령 조문을 확인해 주세요.";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LawSearchService lawSearchService;
    private final LawPromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final WorkplaceRepository workplaceRepository;

    @Transactional
    public ChatDtos.SessionResponse createSession(Long memberId, Long workplaceId) {
        if (workplaceId != null) {
            workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                    .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
        }
        ChatSession session = sessionRepository.save(ChatSession.builder()
                .userId(memberId)
                .workplaceId(workplaceId)
                .build());
        return new ChatDtos.SessionResponse(session);
    }

    public List<ChatDtos.SessionResponse> listSessions(Long memberId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(memberId).stream()
                .map(ChatDtos.SessionResponse::new)
                .toList();
    }

    public List<ChatDtos.MessageResponse> getMessages(Long memberId, UUID sessionId) {
        ChatSession session = findOwnedSession(memberId, sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(ChatDtos.MessageResponse::new)
                .toList();
    }

    @Transactional
    public ChatDtos.AskResponse ask(Long memberId, UUID sessionId, String question) {
        ChatSession session = findOwnedSession(memberId, sessionId);
        String trimmed = question.trim();

        session.titleIfEmpty(trimmed);
        messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.USER)
                .content(trimmed)
                .build());

        // R: 관련 조문 검색 (키워드 + 의미 검색)
        List<LawSearchResponse.LawArticleDto> articles =
                lawSearchService.search(trimmed, SEARCH_SIZE).getResults();
        Long[] citedIds = articles.stream()
                .map(LawSearchResponse.LawArticleDto::getArticleId)
                .filter(Objects::nonNull)
                .distinct()
                .toArray(Long[]::new);

        // A + G: 조문을 근거로 답변 생성
        var generated = llmClient.generate(
                promptBuilder.systemPrompt(), promptBuilder.userPrompt(trimmed, articles));

        if (generated.isEmpty()) {
            String note = llmClient.available() ? NO_ANSWER_NOTE : NO_LLM_NOTE;
            return new ChatDtos.AskResponse(session.getId(), trimmed,
                    ChatDtos.AnswerMode.RETRIEVAL_ONLY, null, articles, note, null);
        }

        LlmClient.LlmAnswer answer = generated.get();
        messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatRole.ASSISTANT)
                .content(answer.content())
                .citedArticles(citedIds)
                .modelName(llmClient.modelName())
                .tokenUsage(answer.tokenUsage())
                .latencyMs(answer.latencyMs())
                .build());

        return new ChatDtos.AskResponse(session.getId(), trimmed,
                ChatDtos.AnswerMode.GENERATED, answer.content(), articles, null,
                llmClient.modelName());
    }

    private ChatSession findOwnedSession(Long memberId, UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));
    }
}
