package com.safework.chat.repository;

import com.safework.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByIdAndUserId(UUID id, Long userId);

    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
