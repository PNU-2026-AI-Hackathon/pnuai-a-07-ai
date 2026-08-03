package com.safework.response.dto;

import lombok.Getter;

@Getter
public class ImmediateActionDto {

    private final int step;
    private final String title;
    private final String description;
    /** 법정 의무인 경우 근거 조문. 실무 단계면 null */
    private final String legalBasis;
    /** 사고 직후 즉시 해야 하는 단계인지 (false 면 이후 처리) */
    private final boolean immediate;

    public ImmediateActionDto(int step, String title, String description,
                              String legalBasis, boolean immediate) {
        this.step = step;
        this.title = title;
        this.description = description;
        this.legalBasis = legalBasis;
        this.immediate = immediate;
    }
}
