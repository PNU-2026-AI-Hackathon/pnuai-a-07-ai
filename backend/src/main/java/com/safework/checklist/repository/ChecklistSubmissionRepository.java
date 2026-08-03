package com.safework.checklist.repository;

import com.safework.checklist.entity.ChecklistSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChecklistSubmissionRepository extends JpaRepository<ChecklistSubmission, Long> {

    Optional<ChecklistSubmission> findFirstByWorkplaceIdOrderBySubmittedAtDesc(Long workplaceId);
}
