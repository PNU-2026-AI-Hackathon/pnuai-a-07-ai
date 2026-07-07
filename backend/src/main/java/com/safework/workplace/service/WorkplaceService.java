package com.safework.workplace.service;

import com.safework.auth.entity.Member;
import com.safework.auth.repository.MemberRepository;
import com.safework.workplace.dto.WorkplaceCreateRequest;
import com.safework.workplace.dto.WorkplaceResponse;
import com.safework.workplace.entity.Workplace;
import com.safework.workplace.repository.WorkplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkplaceService {

    private final WorkplaceRepository workplaceRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public WorkplaceResponse create(Long memberId, WorkplaceCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Workplace workplace = Workplace.builder()
                .member(member)
                .industryMajor(request.getIndustryMajor())
                .industryMid(request.getIndustryMid())
                .region(request.getRegion())
                .workerCount(request.getWorkerCount())
                .companyName(request.getCompanyName())
                .address(request.getAddress())
                .build();

        workplaceRepository.save(workplace);
        return WorkplaceResponse.from(workplace);
    }

    public List<WorkplaceResponse> getMyWorkplaces(Long memberId) {
        return workplaceRepository.findByMemberId(memberId).stream()
                .map(WorkplaceResponse::from)
                .collect(Collectors.toList());
    }

    public WorkplaceResponse getWorkplace(Long memberId, Long workplaceId) {
        Workplace workplace = workplaceRepository.findByIdAndMemberId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
        return WorkplaceResponse.from(workplace);
    }

    @Transactional
    public WorkplaceResponse update(Long memberId, Long workplaceId, WorkplaceCreateRequest request) {
        Workplace workplace = workplaceRepository.findByIdAndMemberId(workplaceId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));

        workplace.update(
                request.getIndustryMajor(),
                request.getIndustryMid(),
                request.getRegion(),
                request.getWorkerCount(),
                request.getCompanyName(),
                request.getAddress()
        );

        return WorkplaceResponse.from(workplace);
    }
}