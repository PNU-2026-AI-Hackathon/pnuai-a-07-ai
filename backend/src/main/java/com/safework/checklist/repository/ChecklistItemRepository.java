package com.safework.checklist.repository;

import com.safework.checklist.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    @Query("""
            SELECT i FROM ChecklistItem i
            WHERE i.active = true
              AND i.targetIndustry = :industry
              AND (:criticalOnly = false OR i.critical = true)
              AND (:workType IS NULL OR i.workType = :workType)
              AND (:category IS NULL OR i.category = :category)
            ORDER BY i.critical DESC, i.riskWeight DESC, i.itemCode
            """)
    List<ChecklistItem> search(@Param("industry") String industry,
                               @Param("criticalOnly") boolean criticalOnly,
                               @Param("workType") String workType,
                               @Param("category") String category);

    @Query("""
            SELECT i FROM ChecklistItem i
            WHERE i.active = true
              AND i.targetIndustry = :industry
              AND (:criticalOnly = false OR i.critical = true)
              AND i.workType IN :workTypes
              AND (:category IS NULL OR i.category = :category)
            ORDER BY i.critical DESC, i.riskWeight DESC, i.itemCode
            """)
    List<ChecklistItem> searchByWorkTypes(@Param("industry") String industry,
                                          @Param("criticalOnly") boolean criticalOnly,
                                          @Param("workTypes") Collection<String> workTypes,
                                          @Param("category") String category);

    @Query("""
            SELECT i FROM ChecklistItem i
            WHERE i.active = true
              AND (:criticalOnly = false OR i.critical = true)
              AND (:category IS NULL OR i.category = :category)
            ORDER BY i.critical DESC, i.riskWeight DESC, i.itemCode
            """)
    List<ChecklistItem> searchAcrossIndustries(@Param("criticalOnly") boolean criticalOnly,
                                                @Param("category") String category);

    List<ChecklistItem> findByItemCodeIn(Collection<String> itemCodes);
}
