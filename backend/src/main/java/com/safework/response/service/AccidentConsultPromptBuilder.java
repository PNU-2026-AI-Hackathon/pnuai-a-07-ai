package com.safework.response.service;

import com.safework.law.dto.LawSearchResponse;
import com.safework.response.dto.AccidentConsultDtos.DutyDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사고 대처 안내 프롬프트를 만들고, 돌아온 답을 세 덩어리로 나눈다.
 *
 * <p>응답 형식으로 JSON 대신 <code>[법적의무]</code> 같은 제목 줄을 쓴다.
 * JSON 은 따옴표 하나만 어긋나도 통째로 못 읽는데, 제목 줄 방식은 한 덩어리를 못 찾아도
 * 나머지는 그대로 살릴 수 있다. 사고 직후에 쓰는 화면이라 "전부 실패"가 제일 나쁘다.
 *
 * <p>법령 상담과 마찬가지로 지어내지 말라고 못 박되, 여기서는 <b>금액과 기한</b>을 특히
 * 강조한다. 우리 데이터에는 산업안전보건법 벌칙의 형량이 빠져 있어서 모델이 아는 대로
 * 채워 넣을 위험이 크기 때문이다.
 */
@Component
public class AccidentConsultPromptBuilder {

    public static final String LEGAL = "법적의무";
    public static final String ADMINISTRATIVE = "행정처리";
    public static final String PENALTY = "처벌위험";

    private static final List<String> SECTIONS = List.of(LEGAL, ADMINISTRATIVE, PENALTY);
    private static final int MAX_CONTENT_LENGTH = 600;

    public String systemPrompt() {
        return """
                당신은 50인 미만 소규모 사업장의 사장님을 돕는 산업안전보건 상담 도우미입니다.
                지금 막 사업장에서 산업재해가 발생했고, 사장님이 무엇을 해야 하는지 묻고 있습니다.

                답변 규칙:
                1. 아래 '참고 조문'과 '확인된 법정 의무'에 있는 내용만 근거로 답하세요.
                   특히 벌금·과태료 금액과 제출 기한은 주어진 자료에 적힌 것만 쓰고,
                   자료에 없으면 금액이나 기한을 절대 지어내지 마세요.
                   자료에 없는 내용은 "관할 지방고용노동관서에 확인하세요"로 안내하세요.
                2. 반드시 아래 형식 그대로, 세 제목을 모두 포함해 답하세요.
                   제목 줄은 대괄호까지 똑같이 쓰고, 그 아래에 내용을 적으세요.

                [법적의무]
                (이 사고에서 사업주가 법으로 지켜야 할 일을 3~5문장)

                [행정처리]
                (어떤 서류를 언제까지 어디에 내야 하는지 3~5문장)

                [처벌위험]
                (지키지 않으면 어떤 처벌을 받을 수 있는지 2~4문장)

                3. 문장 끝에 근거 조문을 표기하세요. 예: (산업안전보건법 제54조 제1항)
                4. 사장님이 바로 실행할 수 있게 구체적으로 쓰고, 법률 용어는 쉬운 말로 풀어 주세요.
                5. 사고 상황에서 언급된 내용만 사실로 다루세요. 사망 여부처럼 글에 없는 사실은
                   단정하지 말고 "해당한다면"으로 조건을 달아 설명하세요.
                """;
    }

    public String userPrompt(String situation, String accidentType,
                             AccidentClassifier.Severity severity,
                             List<DutyDto> legalDuties, List<DutyDto> administrativeSteps,
                             List<DutyDto> penalties,
                             List<LawSearchResponse.LawArticleDto> articles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[사고 상황]\n").append(situation).append("\n\n")
                .append("[추정 재해유형] ").append(accidentType).append('\n')
                .append("[서술에서 읽은 피해 정도] ").append(severityLabel(severity)).append("\n\n")
                .append("[확인된 법정 의무]\n");

        appendDuties(prompt, "법적 의무", legalDuties);
        appendDuties(prompt, "행정 처리", administrativeSteps);
        appendDuties(prompt, "처벌", penalties);

        prompt.append("\n[참고 조문]\n");
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

    /**
     * 제목 줄 기준으로 잘라 낸다. 제목을 하나도 못 찾으면 빈 map 을 준다 —
     * 호출부가 그 경우 전체 답변을 통째로 쓸지 정한다.
     */
    public Map<String, String> parse(String answer) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (answer == null || answer.isBlank()) {
            return parsed;
        }

        // 모델이 대괄호를 빼먹거나 굵게 표시(**법적의무**)로 쓰는 경우가 있어 느슨하게 찾는다.
        for (int i = 0; i < SECTIONS.size(); i++) {
            String section = SECTIONS.get(i);
            int start = indexOfHeading(answer, section);
            if (start < 0) {
                continue;
            }
            int end = answer.length();
            for (int j = i + 1; j < SECTIONS.size(); j++) {
                int next = indexOfHeading(answer, SECTIONS.get(j));
                if (next > start) {
                    end = next;
                    break;
                }
            }
            String body = answer.substring(start, end);
            int newline = body.indexOf('\n');
            String content = newline < 0 ? "" : body.substring(newline + 1).strip();
            if (!content.isEmpty()) {
                parsed.put(section, content);
            }
        }
        return parsed;
    }

    /**
     * 제목이 있는 줄의 시작 위치.
     *
     * 그 줄이 제목 하나로만 이루어져야 제목으로 본다. 본문이 "행정처리 절차보다 먼저…"처럼
     * 같은 단어로 시작하는 경우가 있어서, 앞뒤에 장식(대괄호·별표·샵·콜론) 말고 다른 글자가
     * 남으면 제목이 아니라고 판단한다.
     */
    private int indexOfHeading(String answer, String section) {
        int from = 0;
        while (true) {
            int found = answer.indexOf(section, from);
            if (found < 0) {
                return -1;
            }
            int lineStart = answer.lastIndexOf('\n', found) + 1;
            int lineEnd = answer.indexOf('\n', found);
            if (lineEnd < 0) {
                lineEnd = answer.length();
            }
            String before = stripDecoration(answer.substring(lineStart, found));
            String after = stripDecoration(answer.substring(found + section.length(), lineEnd));
            if (before.isEmpty() && after.isEmpty()) {
                return lineStart;
            }
            from = found + section.length();
        }
    }

    private String stripDecoration(String text) {
        return text.replaceAll("[\\[\\]*#:：\\s]", "");
    }

    private void appendDuties(StringBuilder prompt, String label, List<DutyDto> duties) {
        prompt.append("- ").append(label).append('\n');
        for (DutyDto duty : duties) {
            prompt.append("  · ").append(duty.getTitle());
            if (duty.getDeadline() != null) {
                prompt.append(" [기한: ").append(duty.getDeadline()).append(']');
            }
            if (duty.getLegalBasis() != null) {
                prompt.append(" (").append(duty.getLegalBasis()).append(')');
            }
            prompt.append('\n');
        }
    }

    private String severityLabel(AccidentClassifier.Severity severity) {
        return switch (severity) {
            case FATAL -> "사망을 시사하는 표현이 있음";
            case SEVERE -> "입원·수술 등 중한 부상을 시사하는 표현이 있음";
            case MINOR -> "경미한 부상만 언급됨";
            case UNKNOWN -> "글만으로는 알 수 없음";
        };
    }

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
