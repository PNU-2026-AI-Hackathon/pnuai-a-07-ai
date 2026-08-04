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
        /** 어디에 내는지 (관할 지방고용노동관서 · 근로복지공단 …). 해당 없으면 null */
        private final String agency;
        /** 제출 서식 이름. 없으면 null */
        private final String formName;
        /** 서식 다운로드 · 신청 페이지 링크. 없으면 null */
        private final String formUrl;
        /** 안 지켰을 때의 과태료·벌칙. 확인된 것만 들어간다 */
        private final String penalty;

        /** 법령 조문에서 정리한 의무 — 기관·서식 정보가 없다 */
        public DutyDto(String title, String detail, String deadline, String legalBasis) {
            this(title, detail, deadline, legalBasis, null, null, null, null);
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        public DutyDto(String title, String detail, String deadline, String legalBasis,
                       String agency, String formName, String formUrl, String penalty) {
            this.title = title;
            this.detail = detail;
            this.deadline = deadline;
            this.legalBasis = legalBasis;
            this.agency = agency;
            this.formName = formName;
            this.formUrl = formUrl;
            this.penalty = penalty;
        }
    }

    /** 이 사고와 비슷한 판결. 처벌 위험을 가늠하는 데 쓴다. */
    @Getter
    public static class PrecedentDto {
        private final String caseName;
        private final String court;
        /** 사건번호·선고일 */
        private final String reference;
        /** 이 사고와 어떤 점이 닮았는지 */
        private final String relevance;
        private final String summary;
        private final String url;

        public PrecedentDto(String caseName, String court, String reference,
                            String relevance, String summary, String url) {
            this.caseName = caseName;
            this.court = court;
            this.reference = reference;
            this.relevance = relevance;
            this.summary = summary;
            this.url = url;
        }
    }

    /** 사업주가 신청할 수 있는 지원사업. 재발방지에 쓸 수 있는 돈·컨설팅이다. */
    @Getter
    public static class SupportProgramDto {
        private final String title;
        private final String agency;
        /** 왜 이 사업장에 해당하는지 */
        private final String relevance;
        private final String summary;
        /** 신청 기한 안내 */
        private final String deadline;
        private final String url;

        public SupportProgramDto(String title, String agency, String relevance,
                                 String summary, String deadline, String url) {
            this.title = title;
            this.agency = agency;
            this.relevance = relevance;
            this.summary = summary;
            this.deadline = deadline;
            this.url = url;
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

        /** 이 사고와 비슷한 판결 (없으면 빈 배열) */
        private final List<PrecedentDto> relatedPrecedents;
        /** 재발방지에 쓸 수 있는 지원사업 (없으면 빈 배열) */
        private final List<SupportProgramDto> supportPrograms;

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
                        List<PrecedentDto> relatedPrecedents,
                        List<SupportProgramDto> supportPrograms,
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
            this.relatedPrecedents = relatedPrecedents;
            this.supportPrograms = supportPrograms;
            this.citedArticles = citedArticles;
            this.similarCases = similarCases.stream()
                    .map(AccidentResponseGuide.SimilarCaseDto::new)
                    .toList();
            this.similarCaseNote = similarCaseNote;
            this.disclaimer = disclaimer;
        }
    }
}
