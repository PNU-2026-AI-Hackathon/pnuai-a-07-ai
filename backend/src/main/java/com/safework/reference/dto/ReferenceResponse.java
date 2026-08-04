package com.safework.reference.dto;

import com.safework.reference.repository.ReferenceRepository;
import lombok.Getter;

import java.util.List;

/**
 * 화면에서 고를 수 있는 값 전부.
 *
 * 다섯 종류를 합쳐도 120여 건이라 나눠서 부르게 하는 것보다 한 번에 주는 편이 낫다.
 * 프론트는 앱을 켤 때 한 번 받아 두고 계속 쓰면 된다.
 */
@Getter
public class ReferenceResponse {

    private final List<IndustryDto> industries;
    private final List<SizeClassDto> sizeClasses;
    private final List<RegionDto> regions;
    private final List<AccidentTypeDto> accidentTypes;
    private final List<WorkTypeDto> workTypes;

    public ReferenceResponse(List<ReferenceRepository.Industry> industries,
                             List<ReferenceRepository.SizeClass> sizeClasses,
                             List<ReferenceRepository.Region> regions,
                             List<ReferenceRepository.AccidentType> accidentTypes,
                             List<ReferenceRepository.WorkType> workTypes) {
        this.industries = industries.stream().map(IndustryDto::new).toList();
        this.sizeClasses = sizeClasses.stream().map(SizeClassDto::new).toList();
        this.regions = regions.stream().map(RegionDto::new).toList();
        this.accidentTypes = accidentTypes.stream().map(AccidentTypeDto::new).toList();
        this.workTypes = workTypes.stream().map(WorkTypeDto::new).toList();
    }

    @Getter
    public static class IndustryDto {
        private final String code;
        private final String displayName;
        /** 고위험 업종인지 — 화면에서 표시를 다르게 하고 싶을 때 쓰세요 */
        private final boolean highRisk;

        public IndustryDto(ReferenceRepository.Industry source) {
            this.code = source.code();
            this.displayName = source.displayName();
            this.highRisk = source.highRisk();
        }
    }

    @Getter
    public static class SizeClassDto {
        private final String code;
        private final String displayName;
        /** ML 모델이 쓰는 규모 구분(9종). DB 는 10종이라 20~29인·30~49인이 하나로 합쳐진다. */
        private final String modelSizeClass;
        /** 셀렉트박스 표시 순서. 이 순서대로 내려갑니다 */
        private final int sortOrder;

        public SizeClassDto(ReferenceRepository.SizeClass source) {
            this.code = source.code();
            this.displayName = source.displayName();
            this.modelSizeClass = source.modelSizeClass();
            this.sortOrder = source.sortOrder();
        }
    }

    @Getter
    public static class RegionDto {
        private final String code;
        private final String displayName;
        /** 우리 서비스의 주 대상 지역(부산·경남)인지 */
        private final boolean target;

        public RegionDto(ReferenceRepository.Region source) {
            this.code = source.code();
            this.displayName = source.displayName();
            this.target = source.target();
        }
    }

    @Getter
    public static class AccidentTypeDto {
        private final String code;
        private final String displayName;

        public AccidentTypeDto(ReferenceRepository.AccidentType source) {
            this.code = source.code();
            this.displayName = source.displayName();
        }
    }

    @Getter
    public static class WorkTypeDto {
        private final String industry;
        private final String workType;
        /** 이 작업 종류의 점검 문항 수 — 화면에서 "12문항" 처럼 미리 알려줄 때 쓰세요 */
        private final int itemCount;

        public WorkTypeDto(ReferenceRepository.WorkType source) {
            this.industry = source.industry();
            this.workType = source.workType();
            this.itemCount = source.itemCount();
        }
    }
}
