package com.safework.cases.dto;

import com.safework.ml.client.MlServerClient;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Getter
public class SimilarCaseResponse {

    private final String industry;
    private final String subIndustry;
    /** 사례에서 자주 나온 키워드 — 어떤 위험이 많은지 한눈에 보여줄 때 쓴다 */
    private final List<String> topKeywords;
    private final int totalCount;
    private final List<CaseDto> cases;
    /** 업종만이 아니라 어떤 진단정보가 검색에 반영됐는지 설명한다. */
    private final String recommendationBasis;
    /** 결과가 비었을 때 사유. 정상이면 null */
    private final String note;

    private SimilarCaseResponse(String industry, String subIndustry, List<String> topKeywords,
                                List<CaseDto> cases, String recommendationBasis, String note) {
        this.industry = industry;
        this.subIndustry = subIndustry;
        this.topKeywords = topKeywords;
        this.totalCount = cases.size();
        this.cases = cases;
        this.recommendationBasis = recommendationBasis;
        this.note = note;
    }

    public static SimilarCaseResponse of(String industry, String subIndustry,
                                         MlServerClient.CaseSearchResult result) {
        return new SimilarCaseResponse(industry, subIndustry, result.topKeywords(),
                result.similarCases().stream().map(CaseDto::new).toList(), null, null);
    }

    public static SimilarCaseResponse of(String industry, String subIndustry,
                                         String recommendationBasis,
                                         MlServerClient.CaseSearchResult result) {
        return new SimilarCaseResponse(industry, subIndustry, result.topKeywords(),
                result.similarCases().stream().map(CaseDto::new).toList(), recommendationBasis, null);
    }

    public static SimilarCaseResponse unavailable(String industry, String subIndustry, String note) {
        return new SimilarCaseResponse(industry, subIndustry, List.of(), List.of(), null, note);
    }

    public static SimilarCaseResponse unavailable(String industry, String subIndustry,
                                                  String recommendationBasis, String note) {
        return new SimilarCaseResponse(industry, subIndustry, List.of(), List.of(), recommendationBasis, note);
    }

    @Getter
    public static class CaseDto {

        private final Long sifId;
        private final String summary;
        /** 재발방지 대책. 줄 단위로 나눠 프론트가 목록으로 그릴 수 있게 한다 */
        private final List<String> countermeasures;
        /** 유사도 (0~1, 1에 가까울수록 관련도 높음) */
        private final BigDecimal score;

        CaseDto(MlServerClient.SimilarCase source) {
            this.sifId = source.sifId();
            this.summary = source.summary();
            this.countermeasures = splitMeasures(source.countermeasure());
            this.score = source.score() == null
                    ? null : source.score().setScale(3, RoundingMode.HALF_UP);
        }

        /**
         * 원본이 "▶ ..." 를 여러 줄로 이어 붙인 형태라 항목별로 끊어 준다.
         *
         * ML 서버가 주는 텍스트는 실제 개행이 아니라 "\n" 두 글자가 그대로 들어 있어서
         * 줄 단위로만 자르면 하나로 뭉쳐 나온다. 그래서 개행과 "▶" 를 모두 구분자로 본다.
         */
        private static List<String> splitMeasures(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split("\\\\n|\\R|▶"))
                    .map(line -> line.replaceFirst("^[\\-•\\s]+", "").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }
}
