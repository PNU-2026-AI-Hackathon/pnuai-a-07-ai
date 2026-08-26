package com.safework.chat.service;

import com.safework.law.dto.LawSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 검색된 조문을 근거로 프롬프트를 만든다(RAG 의 A).
 *
 * 법령 상담이라 모델이 아는 대로 지어내면 위험하다. 그래서 "찾아준 조문 안에서만 답하고,
 * 없으면 없다고 말하라"를 강하게 못 박는다.
 */
@Component
public class LawPromptBuilder {

    private static final int MAX_CONTENT_LENGTH = 700;

    public String systemPrompt() {
        return """
                당신은 50인 미만 소규모 사업장의 사장님을 돕는 산업안전보건 상담 도우미입니다.

                답변 규칙:
                1. 아래에 주어진 '참고 조문'에 있는 내용만 근거로 답하세요.
                   조문에 없는 내용은 절대 지어내지 마세요.
                2. 참고 조문으로 질문에 답할 수 없으면, 솔직하게 "제공된 법령에서 관련 내용을
                   찾지 못했습니다"라고 말하고 관할 고용노동관서 문의를 안내하세요.
                3. 답변에 근거 조문을 반드시 표기하세요. 예: (산업안전보건기준에 관한 규칙 제42조)
                4. 사장님이 바로 실행할 수 있게 구체적으로 쓰세요. 법률 용어는 쉬운 말로 풀어 주세요.
                5. 3~5문장으로 간결하게 답하세요.
                6. 이 답변은 법률 자문이 아니라 참고 자료임을 마지막에 한 줄로 덧붙이세요.
                """;
    }

    public String userPrompt(String question, List<LawSearchResponse.LawArticleDto> articles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[질문]\n").append(question).append("\n\n[참고 조문]\n");

        if (articles.isEmpty()) {
            prompt.append("(검색된 조문이 없습니다)\n");
            return prompt.toString();
        }

        int index = 1;
        for (LawSearchResponse.LawArticleDto article : articles) {
            prompt.append(index++).append(". ")
                    .append(article.getLawName()).append(' ')
                    .append(article.getArticleNo());
            if (article.getClauseNo() != null) {
                prompt.append(' ').append(article.getClauseNo());
            }
            prompt.append(" (").append(article.getTitle()).append(")\n")
                    .append(truncate(article.getContent())).append("\n\n");
        }
        return prompt.toString();
    }

    /** 조문이 길면 프롬프트가 불필요하게 커진다. 앞부분만으로도 판단에 충분한 경우가 많다. */
    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= MAX_CONTENT_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_CONTENT_LENGTH) + " ...";
    }
}
