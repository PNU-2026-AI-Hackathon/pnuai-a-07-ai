package com.safework.law.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사장님이 일상어로 던진 질문을 법령 본문에서 찾을 수 있는 검색어로 바꾼다.
 *
 * 법령은 법률용어("추락", "협착")를 쓰는데 질문은 일상어("떨어질 것 같아요", "끼었어요")로
 * 들어온다. 키워드 검색만으로는 이 간극을 못 넘으므로, 산업안전 도메인 어휘를
 * 손으로 매핑해 보완한다.
 *
 * 임베딩 기반 의미 검색이 준비되면 이 클래스는 필요 없어진다.
 */
@Component
public class QueryExpander {

    /** 질문에 왼쪽 표현이 들어 있으면 오른쪽 검색어들을 함께 찾는다. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("떨어", List.of("추락", "떨어짐")),
            Map.entry("추락", List.of("추락", "떨어짐")),
            Map.entry("낙상", List.of("추락", "떨어짐")),
            Map.entry("끼", List.of("끼임", "협착", "말림")),
            Map.entry("협착", List.of("끼임", "협착")),
            Map.entry("말려", List.of("끼임", "말림", "권취")),
            Map.entry("감전", List.of("감전", "충전전로", "절연")),
            Map.entry("전기", List.of("감전", "충전전로", "절연")),
            Map.entry("누전", List.of("감전", "누전", "접지")),
            Map.entry("넘어", List.of("넘어짐", "전도", "미끄러")),
            Map.entry("미끄러", List.of("미끄러", "넘어짐", "전도")),
            Map.entry("무너", List.of("무너짐", "붕괴")),
            Map.entry("붕괴", List.of("붕괴", "무너짐")),
            Map.entry("부딪", List.of("부딪힘", "충돌")),
            Map.entry("충돌", List.of("충돌", "부딪힘")),
            // "맞" 한 글자로 두면 "알맞은"·"맞춤" 같은 말에도 걸린다.
            Map.entry("맞았", List.of("낙하", "비래", "물체")),
            Map.entry("맞아", List.of("낙하", "비래", "물체")),
            Map.entry("낙하", List.of("낙하", "비래")),
            Map.entry("질식", List.of("산소결핍", "질식", "밀폐공간")),
            Map.entry("숨", List.of("산소결핍", "질식", "환기")),
            Map.entry("밀폐", List.of("밀폐공간", "산소결핍", "환기")),
            Map.entry("화재", List.of("화재", "인화", "소화")),
            // "불" 한 글자는 "불균형"·"불량" 에도 걸린다.
            Map.entry("불이", List.of("화재", "인화")),
            Map.entry("불길", List.of("화재", "인화")),
            Map.entry("폭발", List.of("폭발", "파열")),
            // "베" 한 글자로 두면 "컨베이어" 에서 걸려 절단·베임이 딸려온다.
            Map.entry("베였", List.of("절단", "베임", "날")),
            Map.entry("베어", List.of("절단", "베임", "날")),
            Map.entry("찔", List.of("찔림", "절단")),
            Map.entry("화상", List.of("화상", "고열", "이상온도")),
            Map.entry("소음", List.of("소음", "청력")),
            Map.entry("분진", List.of("분진", "호흡용 보호구")),
            Map.entry("중독", List.of("중독", "유해물질", "관리대상")),
            Map.entry("난간", List.of("안전난간", "난간")),
            // 사다리 사고는 결국 추락이라 제42조(추락의 방지)까지 함께 찾아야 한다.
            Map.entry("사다리", List.of("사다리", "추락")),
            Map.entry("비계", List.of("비계")),
            Map.entry("지게차", List.of("지게차", "하역")),
            Map.entry("크레인", List.of("크레인", "양중기")),
            Map.entry("보호구", List.of("보호구", "안전모", "안전대")),
            Map.entry("안전모", List.of("안전모", "보호구")),
            Map.entry("교육", List.of("안전보건교육", "교육")),
            Map.entry("건강검진", List.of("건강진단")),
            Map.entry("관리자", List.of("안전관리자", "보건관리자", "관리감독자"))
    );

    /**
     * 조사·어미처럼 검색에 방해되는 꼬리. 떼고 나서도 2글자 이상 남을 때만 뗀다.
     * 긴 것부터 검사해야 "에서"가 "에"보다 먼저 잡힌다.
     */
    private static final List<String> TAILS = List.of(
            "습니까", "하나요", "할까요", "인가요", "은가요", "나요", "어요", "예요", "이에요",
            "에서", "에게", "으로", "까지", "부터", "보다", "처럼", "한테",
            "은", "는", "이", "가", "을", "를", "로", "의", "도", "만", "와", "과", "랑", "에"
    );

    /**
     * 질문에 자주 섞이지만 조문을 가려내는 데는 도움이 안 되는 말들.
     * 남겨 두면 엉뚱한 조문이 상위로 올라온다.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "해야", "하나요", "할까요", "합니까", "하는", "하고", "해도", "되나요", "되는", "인가요",
            "같아요", "같은데", "어떻게", "어떤", "무엇", "뭐가", "뭘", "언제", "어디", "누가", "왜",
            "들어갈", "들어가", "둬야", "있나요", "없나요", "그리고", "그런데", "우리", "저희",
            "경우", "때문", "관련", "대해", "대한", "알려", "궁금", "질문", "사업장", "작업"
    );

    private static final int MIN_TOKEN_LENGTH = 2;
    private static final int MAX_TOKENS = 12;

    /** 산업안전 전문용어로 확장된 검색어의 가중치. 일반 어절보다 조문을 잘 가려낸다. */
    private static final int DOMAIN_TERM_WEIGHT = 3;
    private static final int PLAIN_TERM_WEIGHT = 1;

    public record WeightedTerm(String term, int weight) {
    }

    /**
     * 질문 → 가중치가 붙은 검색어 목록.
     *
     * 질문에서 뽑은 어절("기계")은 흔해서 변별력이 낮고, 동의어 사전이 붙여 준
     * 전문용어("협착")는 해당 조문에만 나온다. 같은 비중으로 세면 흔한 단어가
     * 이겨서 엉뚱한 조문이 상위로 올라오므로 전문용어에 가중치를 준다.
     */
    public List<WeightedTerm> expand(String query) {
        Map<String, Integer> weights = new LinkedHashMap<>();

        for (String token : tokenize(query)) {
            weights.putIfAbsent(token, PLAIN_TERM_WEIGHT);
        }

        // 동의어는 어절이 아니라 질문 전체에서 찾는다.
        // "떨어질" 처럼 활용된 형태도 "떨어" 로 잡아내기 위함.
        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            if (query.contains(entry.getKey())) {
                entry.getValue().forEach(term -> weights.put(term, DOMAIN_TERM_WEIGHT));
            }
        }

        // 자르기 전에 가중치순으로 세운다.
        //
        // 넣은 순서대로 자르면 일반 어절이 앞에 있어서 전문용어가 통째로 잘려 나간다.
        // 사장님이 사업장 상황을 길게 적으면(어절 12개는 금방 넘는다) 정작 물어본
        // "사다리·고소작업·보호구"가 검색어에서 빠지고 "소형·부품·생산·공장" 만 남는다.
        // 실제로 그래서 답을 못 찾은 질문이 있었다.
        //
        // 같은 가중치끼리는 넣은 순서를 유지한다(정렬이 안정적이라 질문에 먼저 나온 말이 앞).
        return weights.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed())
                .limit(MAX_TOKENS)
                .map(e -> new WeightedTerm(e.getKey(), e.getValue()))
                .toList();
    }

    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        for (String raw : query.split("[\\s,.·?!\"'()\\[\\]/]+")) {
            String trimmed = raw.trim();
            if (STOPWORDS.contains(trimmed)) {
                continue;
            }
            String token = stripTail(trimmed);
            if (token.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String stripTail(String token) {
        for (String tail : TAILS) {
            if (token.length() > tail.length() + MIN_TOKEN_LENGTH - 1 && token.endsWith(tail)) {
                return token.substring(0, token.length() - tail.length());
            }
        }
        return token;
    }
}
