package com.safework.checklist.service;

import com.safework.auth.entity.Member;
import com.safework.auth.repository.MemberRepository;
import com.safework.checklist.dto.ChecklistItemResponse;
import com.safework.checklist.dto.ChecklistSubmitRequest;
import com.safework.checklist.dto.ChecklistSubmitResponse;
import com.safework.checklist.entity.Answer;
import com.safework.checklist.entity.ChecklistItem;
import com.safework.checklist.entity.ChecklistSubmission;
import com.safework.checklist.repository.ChecklistItemRepository;
import com.safework.checklist.repository.ChecklistResponseRepository;
import com.safework.checklist.repository.ChecklistSubmissionRepository;
import com.safework.ml.client.MlServerClient;
import com.safework.risk.dto.RiskAssessmentResponse;
import com.safework.risk.entity.RiskAssessment;
import com.safework.risk.repository.ColdstartAssessRepository;
import com.safework.risk.repository.RiskAssessmentRepository;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistSubmissionRepository submissionRepository;
    private final ChecklistResponseRepository responseRepository;
    private final WorkplaceRepository workplaceRepository;
    private final MemberRepository memberRepository;
    private final ColdstartAssessRepository coldstartAssessRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final MlServerClient mlServerClient;

    /** 사업장 업종에 해당하는 점검 문항 목록 */
    public List<ChecklistItemResponse> getItems(Long memberId, Long workplaceId,
                                                 boolean criticalOnly, List<String> workTypes,
                                                 String category, int limit) {
        Workplace workplace = findOwnedWorkplace(memberId, workplaceId);

        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ChecklistItem> items = workTypes == null || workTypes.isEmpty()
                ? checklistItemRepository.search(workplace.getIndustry(), criticalOnly, null, category)
                : checklistItemRepository.searchByWorkTypes(
                        workplace.getIndustry(), criticalOnly, workTypes, category);

        return items.stream()
                .limit(safeLimit)
                .map(ChecklistItemResponse::new)
                .toList();
    }

    /** 체크리스트 제출 → 위험도 진단까지 한 번에 수행 */
    @Transactional
    public ChecklistSubmitResponse submit(Long memberId, Long workplaceId, ChecklistSubmitRequest request) {
        Workplace workplace = findOwnedWorkplace(memberId, workplaceId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Map<String, ChecklistItem> itemsByCode = resolveItems(request.getResponses());

        int totalItems = request.getResponses().size();
        int answeredItems = (int) request.getResponses().stream()
                .filter(r -> r.getAnswer() != Answer.NA)
                .count();

        ChecklistSubmission submission = submissionRepository.save(ChecklistSubmission.builder()
                .workplace(workplace)
                .submittedBy(member)
                .totalItems(totalItems)
                .answeredItems(answeredItems)
                .build());

        List<ChecklistResponseRepository.ResponseRow> rows = request.getResponses().stream()
                .map(r -> new ChecklistResponseRepository.ResponseRow(
                        itemsByCode.get(r.getItemCode()).getId(), r.getAnswer(), r.getNote()))
                .toList();
        responseRepository.saveAll(submission.getId(), rows);

        // fn_coldstart_assess 는 방금 저장한 제출을 읽으므로, 응답 INSERT 이후에 호출해야 한다.
        // 점수 계산은 DB 함수가 정본이다(공식이 두 군데 있으면 어긋난다).
        Long assessmentId = coldstartAssessRepository.assess(workplaceId);
        RiskAssessment assessment = riskAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalStateException("위험도 진단 결과를 찾을 수 없습니다."));

        attachMlPrediction(workplace, assessment);

        return new ChecklistSubmitResponse(submission.getId(), totalItems, answeredItems,
                new RiskAssessmentResponse(assessment));
    }

    /**
     * 통계 점수 위에 ML 예측(어떤 재해가 날 가능성이 높은지, 얼마나 심각할지)을 얹는다.
     * ML 서버가 없거나 느리면 그냥 콜드스타트 결과만 남는다 — 진단 자체가 실패하면 안 된다.
     */
    private void attachMlPrediction(Workplace workplace, RiskAssessment assessment) {
        mlServerClient.predictRisk(workplace.getIndustry(), workplace.getSubIndustry(),
                        workplace.getSizeClass(), workplace.getRegion())
                .ifPresent(prediction -> assessment.attachMlPrediction(
                        prediction.topRisks(), prediction.severityPrediction(),
                        prediction.modelVersion()));
    }

    /** 요청의 itemCode 를 실제 문항으로 바꾸고, 잘못된 코드/중복이 있으면 막는다. */
    private Map<String, ChecklistItem> resolveItems(List<ChecklistSubmitRequest.ResponseItem> responses) {
        List<String> requestedCodes = responses.stream()
                .map(ChecklistSubmitRequest.ResponseItem::getItemCode)
                .toList();

        Set<String> uniqueCodes = new LinkedHashSet<>(requestedCodes);
        if (uniqueCodes.size() != requestedCodes.size()) {
            throw new IllegalArgumentException("같은 문항에 대한 응답이 중복되었습니다.");
        }

        Map<String, ChecklistItem> itemsByCode = checklistItemRepository.findByItemCodeIn(uniqueCodes)
                .stream()
                .collect(Collectors.toMap(ChecklistItem::getItemCode, Function.identity()));

        List<String> unknownCodes = uniqueCodes.stream()
                .filter(code -> !itemsByCode.containsKey(code))
                .toList();
        if (!unknownCodes.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 문항 코드입니다: " + String.join(", ", unknownCodes));
        }

        return itemsByCode;
    }

    private Workplace findOwnedWorkplace(Long memberId, Long workplaceId) {
        return workplaceRepository.findByIdAndOwnerId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
    }
}
