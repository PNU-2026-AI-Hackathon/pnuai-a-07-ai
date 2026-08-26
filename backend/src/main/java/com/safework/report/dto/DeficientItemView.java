package com.safework.report.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** 리포트 템플릿용 — 미비 항목 1건과 그에 딸린 근거 법령들 */
@Getter
public class DeficientItemView {

    private final String itemCode;
    private final String category;
    private final String workType;
    private final String question;
    private final BigDecimal riskWeight;
    private final boolean critical;
    private final List<LawView> laws;

    public DeficientItemView(String itemCode, String category, String workType, String question,
                             BigDecimal riskWeight, boolean critical, List<LawView> laws) {
        this.itemCode = itemCode;
        this.category = category;
        this.workType = workType;
        this.question = question;
        this.riskWeight = riskWeight;
        this.critical = critical;
        this.laws = laws;
    }

    @Getter
    public static class LawView {
        private final String lawName;
        private final String articleNo;
        private final String title;

        public LawView(String lawName, String articleNo, String title) {
            this.lawName = lawName;
            this.articleNo = articleNo;
            this.title = title;
        }
    }
}
