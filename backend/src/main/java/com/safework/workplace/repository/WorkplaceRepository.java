package com.safework.workplace.repository;

import com.safework.workplace.entity.Workplace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkplaceRepository extends JpaRepository<Workplace, Long> {
    List<Workplace> findByMemberId(Long memberId);
    Optional<Workplace> findByIdAndMemberId(Long id, Long memberId);
}