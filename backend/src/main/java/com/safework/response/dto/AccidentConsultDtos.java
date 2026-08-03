package com.safework.response.dto;

import com.safework.law.dto.LawSearchResponse;
import com.safework.response.repository.AccidentResponseRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 사고 상황을 글로 적어 대처 방법을 받는 API 의 요청·응답 */
public final class AccidentConsultDtos {

    private AccidentConsultDtos() {
    }

    @Getter
    @Setter
    public static class Request {

        /** 어떤 사고가 났는지 그대로 적은 글 */
        @NotBlank(message = "어떤 사고가 났는지 입력해 주세요")
        @Size(max = 2000, message = "사고 내용은 2000자까지 입력할 수 있습니다")
        private String situation;

        /** 업종. 있으면 유사 재해사례를 같은 업종에서 찾는다. 선택 */
        private String industry;

        /**
         * 재해유형을 사용자가 직접 고른 경우. 서술에서 추정한 값보다 우선한다.
         * 프론트가 "추정 유형이 틀렸다면 고르세요"로 다시 물었을 때 쓴다. 선택
         */
        private String accidentType;
    }

    /** 안내문이 어떻게 만들어졌는지 */
    public enum GuidanceMode {
        /** LLM 이 조문을 근거로 상황에 맞춰 설명을 덧붙임 */
        GENERATED,
        /** LLM 을 쓸 수 없어 법정 의무 목록과 조문만 반환함 */
        RETRIEVAL_ONLY
    }

    /** 사업주가 해야 할 일 한 가지 */
    @Getter
    public static class DutyDto {
        private final String title;
        private final String detail;
        /** 법이 정한 기한. 없으면 null */
        private final String deadline;
        /** 근거 조문. 법령 근거가 없는 실무 안내면 null */
        private final String legalBasis;

        public DutyDto(String title, String detail, String deadline, String legalBasis) {
            this.title = title;
            this.detail = detail;
            this.deadline = deadline;
            this.legalBasis = legalBasis;
        }
    }

    /**
     * 안내 한 덩어리.
     *
     * items 는 조문에서 뽑아 둔 목록이라 LLM 이 없어도 항상 채워진다.
     * guidance 는 LLM 이 이 사고 상황에 맞춰 쓴 설명이고, 없으면 null 이다.
     * 세 덩어리(법적 의무·행정 처리·처벌)가 같은 모양이라 프론트는 한 컴포넌트로 그리면 된다.
     */
    @Getter
    public static class Section {
        private final String guidance;
        private final List<DutyDto> items;

        public Section(String guidance, List<DutyDto> items) {
            this.guidance = guidance;
            this.items = items;
        }
    }

    /**
     * 서술에서 읽어 낸 피해 정도.
     *
     * 중대재해 여부는 여기서 <b>판정하지 않는다</b>. 사망자 수나 요양 기간은 글에 없을 수 있어서
     * 기준(criteria)을 함께 내려보내고 사용자가 스스로 확인하게 한다.
     */
    @Getter
    public static class SeverityDto {
        private final String level;
        /** 중대재해일 가능성이 있어 보이는지. 판정이 아니라 안내 강도를 정한 근거다. */
        private final boolean seriousAccidentLikely;
        private final String note;
        /** 중대재해 판단 기준 — 사용자가 직접 대조할 수 있게 그대로 준다 */
        private final List<String> criteria;
        private final String criteriaBasis;

        public SeverityDto(String level, boolean seriousAccidentLikely, String note,
                           List<String> criteria, String criteriaBasis) {
            this.level = level;
            this.seriousAccidentLikely = seriousAccidentLikely;
            this.note = note;
            this.criteria = criteria;
            this.criteriaBasis = criteriaBasis;
        }
    }

    @Getter
    public static class Response {

        private final String situation;
        /** 서술에서 추정하거나 사용자가 고른 재해유형 */
        private final String accidentType;
        /** 추정이 확실한지. false 면 프론트가 사용자에게 확인을 받는 편이 좋다 */
        private final boolean accidentTypeCertain;
        /** 유형이 틀렸을 때 고를 수 있는 목록 */
        private final List<String> selectableTypes;
        private final SeverityDto severity;

        private final GuidanceMode mode;
        /** LLM 을 못 쓴 이유 등. 정상 생성되면 null */
        private final String note;
        private final String model;

        /** 사고 직후 조치 순서 (재해유형과 무관하게 동일) */
        private final List<ImmediateActionDto> immediateActions;
        private final Section legalObligations;
        private final Section administrativeSteps;
        private final Section penaltyRisk;

        /** 위 안내의 근거가 된 조문 원문 */
        private final List<LawSearchResponse.LawArticleDto> citedArticles;
        private final List<AccidentResponseGuide.SimilarCaseDto> similarCases;
        private final String similarCaseNote;
        private final String disclaimer;

        @SuppressWarnings("checkstyle:ParameterNumber")
        public Response(String situation, String accidentType, boolean accidentTypeCertain,
                        List<String> selectableTypes, SeverityDto severity,
                        GuidanceMode mode, String note, String model,
                        List<ImmediateActionDto> immediateActions,
                        Section legalObligations, Section administrativeSteps, Section penaltyRisk,
                        List<LawSearchResponse.LawArticleDto> citedArticles,
                        List<AccidentResponseRepository.SimilarCase> similarCases,
                        String similarCaseNote, String disclaimer) {
            this.situation = situation;
            this.accidentType = accidentType;
            this.accidentTypeCertain = accidentTypeCertain;
            this.selectableTypes = selectableTypes;
            this.severity = severity;
            this.mode = mode;
            this.note = note;
            this.model = model;
            this.immediateActions = immediateActions;
            this.legalObligations = legalObligations;
            this.administrativeSteps = administrativeSteps;
            this.penaltyRisk = penaltyRisk;
            this.citedArticles = citedArticles;
            this.similarCases = similarCases.stream()
                    .map(AccidentResponseGuide.SimilarCaseDto::new)
                    .toList();
            this.similarCaseNote = similarCaseNote;
            this.disclaimer = disclaimer;
        }
    }
}
