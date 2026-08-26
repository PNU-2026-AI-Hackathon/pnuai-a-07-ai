package com.safework.response.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 재해유형 어휘가 테이블마다 다르다.
 *
 *   code_accident_type / checklist_item.category : 일상어  (떨어짐, 넘어짐, 무너짐)
 *   sif_case.accident_kind                       : 기술어  (추락,   전도,   붕괴)
 *
 * API 는 위험도 진단(topAccidentType)·예방 가이드(accidentType)와 맞추기 위해
 * 일상어를 받고, 중대재해 사례를 찾을 때만 기술어로 바꿔 조회한다.
 */
@Component
public class AccidentTypeVocabulary {

    /** 일상어(code_accident_type) → 중대재해 사례(sif_case.accident_kind) 어휘 */
    private static final Map<String, List<String>> TO_SIF_KIND = Map.ofEntries(
            Map.entry("떨어짐", List.of("추락")),
            Map.entry("넘어짐", List.of("전도")),
            Map.entry("무너짐", List.of("붕괴")),
            Map.entry("물체에맞음", List.of("낙하")),
            // 끼임과 깔림은 눌리는 사고라는 점이 같아 사례를 함께 보여주는 편이 도움이 된다.
            Map.entry("끼임", List.of("끼임", "깔림")),
            // 코드값은 '깔림.뒤집힘' 인데 sif_case 는 '깔림'·'전도' 로 나뉘어 있다.
            // 매핑을 빼먹으면 '깔림.뒤집힘' 을 그대로 찾다가 사례가 하나도 안 나온다.
            Map.entry("깔림.뒤집힘", List.of("깔림", "전도")),
            Map.entry("부딪힘", List.of("부딪힘")),
            Map.entry("감전", List.of("감전")),
            Map.entry("빠짐익사", List.of("익사")),
            Map.entry("산소결핍", List.of("질식")),
            Map.entry("이상온도물체접촉", List.of("화상", "이상온도 접촉")),
            Map.entry("화재", List.of("화재")),
            Map.entry("폭발파열", List.of("폭발", "파열")),
            Map.entry("절단베임찔림", List.of("베임", "찔림")),
            Map.entry("화학물질누출접촉", List.of("중독", "유해물 접촉")),
            Map.entry("업무상질병", List.of("중독"))
    );

    /**
     * 중대재해 사례 조회에 쓸 재해유형 이름들.
     * 매핑이 없으면 입력값을 그대로 쓴다(양쪽 어휘가 같은 '끼임', '감전' 등).
     */
    public List<String> toSifKinds(String accidentType) {
        return TO_SIF_KIND.getOrDefault(accidentType, List.of(accidentType));
    }

    /**
     * 업종도 어휘가 다르다. code_industry 는 '제조업', sif_case 는 '제조업등' 을 쓴다.
     * sif_case 에는 건설업/제조업등 두 값만 있으므로 나머지 업종은 사례를 찾을 수 없다.
     */
    public String toSifIndustry(String industry) {
        if (industry == null) {
            return null;
        }
        return switch (industry) {
            case "건설업" -> "건설업";
            case "제조업" -> "제조업등";
            default -> industry;
        };
    }

    /**
     * 사례가 비었을 때 프론트가 사유를 안내할 수 있게 문구를 준다. 사례가 있으면 null.
     *
     * 2026-08-04 덤프에서 제조업등 2,573건의 재해유형이 모두 채워져, 예전에 있던
     * "제조업은 분류가 안 돼 있어 표시할 수 없습니다" 안내는 더 이상 필요 없다.
     * sif_case 에 있는 업종은 건설업·제조업등 둘뿐이라 그 밖의 업종은 여전히 비어 나온다.
     */
    public String missingCaseReason(String industry, boolean empty) {
        if (!empty) {
            return null;
        }
        String sifIndustry = toSifIndustry(industry);
        if (sifIndustry != null && !SIF_INDUSTRIES.contains(sifIndustry)) {
            return "중대재해 사례는 건설업·제조업만 정리되어 있어 "
                    + industry + " 사례는 표시할 수 없습니다.";
        }
        return "해당 업종·재해유형으로 정리된 중대재해 사례가 없습니다.";
    }

    /** sif_case.industry_div 에 실제로 존재하는 값 */
    private static final List<String> SIF_INDUSTRIES = List.of("건설업", "제조업등");
}
