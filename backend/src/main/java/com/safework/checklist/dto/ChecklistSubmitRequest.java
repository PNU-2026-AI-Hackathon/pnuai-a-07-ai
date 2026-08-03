package com.safework.checklist.dto;

import com.safework.checklist.entity.Answer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChecklistSubmitRequest {

    @NotEmpty(message = "응답은 최소 1건 이상이어야 합니다")
    @Valid
    private List<ResponseItem> responses;

    @Getter
    @Setter
    public static class ResponseItem {

        @NotBlank(message = "문항 코드는 필수입니다")
        private String itemCode;

        @NotNull(message = "답변은 필수입니다 (YES/NO/NA)")
        private Answer answer;

        private String note;
    }
}
