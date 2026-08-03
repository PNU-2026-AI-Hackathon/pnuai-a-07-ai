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
            Map.entry("끼임", List.of("끼임", "깔림")),
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
}
