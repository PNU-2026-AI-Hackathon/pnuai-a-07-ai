package com.safework.checklist.service;

import com.safework.checklist.entity.ChecklistItem;

import java.util.Arrays;
import java.util.List;

/** STEP 1의 큰 작업·위험 범주를 SIF 문항의 작업유형과 문구에 연결한다. */
enum ChecklistScope {
    MACHINE_EQUIPMENT("기계", "설비", "자동화", "정비", "보수", "점검", "청소", "원료 투입"),
    VEHICLE_HANDLING("지게차", "건설기계", "양중기", "차량", "운반", "상하차", "하역", "인양"),
    WORK_AT_HEIGHT("고소", "사다리", "비계", "지붕", "작업발판", "철골", "개구부", "난간"),
    ELECTRICAL("전기", "감전", "충전부", "누전"),
    HOT_WORK("용접", "절단", "사상", "화기", "불꽃", "가열"),
    CHEMICAL("화학", "위험물질", "유기용제", "도장", "방수", "누출", "중독"),
    CONFINED_SPACE("밀폐", "피트", "맨홀", "탱크", "오·폐수", "질식", "산소결핍", "수중"),
    CONSTRUCTION("굴착", "거푸집", "철골", "콘크리트", "철거", "해체", "흙막이", "조적", "토목", "기초파일"),
    STORAGE_LOGISTICS("적재", "보관", "창고", "상하차", "하역", "화물"),
    GENERAL("통행", "이동", "일반", "작업환경", "정리", "점검", "청소");

    private static final List<String> COMMON_KEYWORDS = List.of(
            "통행", "작업환경", "정리정돈", "보호구");

    private final List<String> keywords;

    ChecklistScope(String... keywords) {
        this.keywords = Arrays.asList(keywords);
    }

    boolean matches(ChecklistItem item) {
        String text = searchableText(item);
        return keywords.stream().anyMatch(text::contains);
    }

    static boolean isCommon(ChecklistItem item) {
        String text = searchableText(item);
        return COMMON_KEYWORDS.stream().anyMatch(text::contains);
    }

    static List<ChecklistScope> parse(List<String> codes) {
        if (codes == null) return List.of();
        return codes.stream().map(code -> {
            try {
                return valueOf(code);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("알 수 없는 작업·위험 범주입니다: " + code);
            }
        }).distinct().toList();
    }

    private static String searchableText(ChecklistItem item) {
        return String.join(" ",
                item.getWorkType() == null ? "" : item.getWorkType(),
                item.getCategory() == null ? "" : item.getCategory(),
                item.getQuestion() == null ? "" : item.getQuestion());
    }
}
