package com.safework.risk.repository;

import com.safework.risk.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    Optional<RiskAssessment> findFirstByWorkplaceIdOrderByAssessedAtDesc(Long workplaceId);
}
