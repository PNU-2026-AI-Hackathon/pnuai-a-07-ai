package com.safework.prevention.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PreventionGuideResponse {

    private final List<AccidentGuideDto> predictions;

    public PreventionGuideResponse(List<AccidentGuideDto> predictions) {
        this.predictions = predictions;
    }
}
