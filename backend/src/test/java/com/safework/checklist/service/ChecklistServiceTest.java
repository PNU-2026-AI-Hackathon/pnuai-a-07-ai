package com.safework.checklist.service;

import com.safework.auth.repository.MemberRepository;
import com.safework.checklist.entity.ChecklistItem;
import com.safework.checklist.repository.ChecklistItemRepository;
import com.safework.checklist.repository.ChecklistResponseRepository;
import com.safework.checklist.repository.ChecklistSubmissionRepository;
import com.safework.ml.client.MlServerClient;
import com.safework.risk.repository.ColdstartAssessRepository;
import com.safework.risk.repository.RiskAssessmentRepository;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChecklistServiceTest {

    @Mock ChecklistItemRepository checklistItemRepository;
    @Mock ChecklistSubmissionRepository submissionRepository;
    @Mock ChecklistResponseRepository responseRepository;
    @Mock WorkplaceRepository workplaceRepository;
    @Mock MemberRepository memberRepository;
    @Mock ColdstartAssessRepository coldstartAssessRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock MlServerClient mlServerClient;
    @InjectMocks ChecklistService checklistService;

    @BeforeEach
    void ownManufacturingWorkplace() {
        Workplace workplace = Workplace.builder().id(1L).industry("제조업").build();
        when(workplaceRepository.findByIdAndOwnerId(1L, 7L)).thenReturn(Optional.of(workplace));
    }

    @Test
    void capsLargeScopeResultAtThirtyFive() {
        List<ChecklistItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(item("COMMON-" + i, "작업장소 통행/이동", "통로를 점검하나요?"));
        for (int i = 0; i < 40; i++) items.add(item("MACHINE-" + i, "자동화 설비 작업", "기계 방호장치를 점검하나요?"));
        for (int i = 0; i < 48; i++) items.add(item("OTHER-" + i, "기타 작업", "기타 위험을 확인하나요?"));
        givenItems(items);

        var result = checklistService.getItems(7L, 1L, true, null,
                List.of("MACHINE_EQUIPMENT"), null, 35);

        assertThat(result).hasSize(35);
        assertThat(result).allMatch(item -> item.getItemCode().startsWith("COMMON-")
                || item.getItemCode().startsWith("MACHINE-"));
    }

    @Test
    void fillsSmallScopeResultToTwentyFiveWithIndustryPriority() {
        List<ChecklistItem> items = new ArrayList<>();
        for (int i = 0; i < 4; i++) items.add(item("COMMON-" + i, "작업장소 통행/이동", "통로를 점검하나요?"));
        for (int i = 0; i < 4; i++) items.add(item("HEIGHT-" + i, "고소작업대 사용 작업", "고소 작업발판을 점검하나요?"));
        for (int i = 0; i < 30; i++) items.add(item("BACKFILL-" + i, "기타 작업", "업종 고위험 문항을 확인하나요?"));
        givenItems(items);

        var result = checklistService.getItems(7L, 1L, true, null,
                List.of("WORK_AT_HEIGHT"), null, 35);

        assertThat(result).hasSize(25);
        assertThat(result.stream().limit(8).map(item -> item.getItemCode()))
                .allMatch(code -> code.startsWith("COMMON-") || code.startsWith("HEIGHT-"));
    }

    @Test
    void usesSameScopeItemsAcrossIndustriesWhenIndustryHasNoSifPool() {
        when(checklistItemRepository.search("제조업", true, null, null)).thenReturn(List.of());
        List<ChecklistItem> shared = new ArrayList<>();
        for (int i = 0; i < 30; i++) shared.add(item("FORKLIFT-" + i, "지게차를 사용하는 작업", "차량 운반구역을 점검하나요?"));
        when(checklistItemRepository.searchAcrossIndustries(true, null)).thenReturn(shared);

        var result = checklistService.getItems(7L, 1L, true, null,
                List.of("VEHICLE_HANDLING"), null, 35);

        assertThat(result).hasSize(30);
        assertThat(result).allMatch(item -> item.getItemCode().startsWith("FORKLIFT-"));
    }

    private void givenItems(List<ChecklistItem> items) {
        when(checklistItemRepository.search("제조업", true, null, null)).thenReturn(items);
        when(checklistItemRepository.searchAcrossIndustries(true, null)).thenReturn(items);
    }

    private ChecklistItem item(String code, String workType, String question) {
        ChecklistItem item = mock(ChecklistItem.class);
        when(item.getItemCode()).thenReturn(code);
        when(item.getCategory()).thenReturn("테스트");
        when(item.getWorkType()).thenReturn(workType);
        when(item.getQuestion()).thenReturn(question);
        when(item.getRiskWeight()).thenReturn(BigDecimal.TEN);
        when(item.isCritical()).thenReturn(true);
        return item;
    }
}
