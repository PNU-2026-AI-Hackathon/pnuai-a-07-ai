package com.safework.response.dto;

import com.safework.response.repository.AccidentResponseRepository;
import lombok.Getter;

import java.util.List;

@Getter
public class AccidentResponseGuide {

    private final String accidentType;
    private final String industry;
    private final String disclaimer;
    private final List<ImmediateActionDto> actions;
    private final List<LawBasisDto> lawBasis;
    private final List<SimilarCaseDto> similarCases;
    /** 사례가 비어 있을 때 그 사유. 사례가 있으면 null */
    private final String similarCaseNote;

    public AccidentResponseGuide(String accidentType, String industry, String disclaimer,
                                 List<ImmediateActionDto> actions,
                                 List<AccidentResponseRepository.LawBasis> lawBasis,
                                 List<AccidentResponseRepository.SimilarCase> similarCases,
                                 String similarCaseNote) {
        this.accidentType = accidentType;
        this.industry = industry;
        this.disclaimer = disclaimer;
        this.actions = actions;
        this.lawBasis = lawBasis.stream().map(LawBasisDto::new).toList();
        this.similarCases = similarCases.stream().map(SimilarCaseDto::new).toList();
        this.similarCaseNote = similarCaseNote;
    }

    @Getter
    public static class LawBasisDto {
        private final String lawName;
        private final String articleNo;
        private final String clauseNo;
        private final String title;
        /** 이 조문을 근거로 삼는 점검항목 수 — 관련도 참고용 */
        private final int referencedBy;

        public LawBasisDto(AccidentResponseRepository.LawBasis basis) {
            this.lawName = basis.lawName();
            this.articleNo = basis.articleNo();
            this.clauseNo = basis.clauseNo();
            this.title = basis.title();
            this.referencedBy = basis.refItems();
        }
    }

    @Getter
    public static class SimilarCaseDto {
        private final Long sifId;
        private final String accidentKind;
        private final String summary;
        private final String highRiskSituation;
        private final String causalFactor;
        /** 재발방지 대책 — 줄 단위로 나눠 프론트가 목록으로 그릴 수 있게 한다 */
        private final List<String> countermeasures;

        public SimilarCaseDto(AccidentResponseRepository.SimilarCase source) {
            this.sifId = source.sifId();
            this.accidentKind = source.accidentKind();
            this.summary = source.summary();
            this.highRiskSituation = source.highRiskSituation();
            this.causalFactor = source.causalFactor();
            this.countermeasures = splitMeasures(source.countermeasure());
        }

        /** 원본이 "▶ ..." 를 줄바꿈으로 이어 붙인 형태라 항목별로 끊어 준다. */
        private static List<String> splitMeasures(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return raw.lines()
                    .map(line -> line.replaceFirst("^[▶\\-•\\s]+", "").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }
}
