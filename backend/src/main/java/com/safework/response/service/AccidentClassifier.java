package com.safework.response.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사장님이 서술한 사고 상황에서 재해유형과 심각도를 추정한다.
 *
 * 사고 직후에는 "떨어짐/끼임" 같은 분류를 고를 여유가 없다. 그래서 있었던 일을 그대로
 * 적게 하고, 그 문장에서 유형을 찾아낸다. 유형이 정해져야 근거 법령과 유사 사례를
 * 찾을 수 있기 때문이다.
 *
 * 형태소 분석기 없이 부분 문자열로만 판단한다. 사고 서술은 "지게차에 다리가 끼여서"처럼
 * 어휘가 뚜렷해서 이 정도로도 대부분 맞고, 틀리더라도 추정값임을 응답에 함께 실어
 * 프론트가 사용자에게 수정할 기회를 줄 수 있게 한다.
 *
 * 심각도는 중대재해 여부를 <b>판정하지 않는다</b>. 중대재해 판단은 사망자 수·요양 기간처럼
 * 서술에 없을 수 있는 사실에 달려 있어서(산업안전보건법 시행규칙 제3조), 여기서는
 * "가능성이 있어 보인다"까지만 말하고 판단 기준을 함께 돌려준다.
 */
@Component
public class AccidentClassifier {

    /** 서술에서 읽어 낸 피해 정도. 법적 판정이 아니라 안내 강도를 정하기 위한 추정값이다. */
    public enum Severity {
        /** 사망을 시사하는 표현이 있음 */
        FATAL,
        /** 입원·수술·절단 등 중한 부상을 시사하는 표현이 있음 */
        SEVERE,
        /** 경미한 부상만 언급됨 */
        MINOR,
        /** 판단할 단서가 없음 */
        UNKNOWN
    }

    public record Result(String accidentType, boolean certain, Severity severity) {
    }

    /**
     * code_accident_type 값 → 그 유형을 가리키는 일상 표현들.
     *
     * 값 자체(끼임, 감전)도 서술에 그대로 나오는 경우가 많아 함께 넣는다.
     * 위험도 진단·예방 가이드와 어휘를 맞춰야 해서 결과는 반드시 이 표의 키로만 낸다.
     */
    private static final Map<String, List<String>> TYPE_KEYWORDS = new LinkedHashMap<>();

    static {
        TYPE_KEYWORDS.put("끼임", List.of("끼임", "끼여", "끼었", "끼인", "협착", "말려", "말림", "빨려", "물려"));
        TYPE_KEYWORDS.put("떨어짐", List.of("떨어졌", "떨어져", "떨어지", "추락", "낙상", "실족", "굴러떨어"));
        TYPE_KEYWORDS.put("넘어짐", List.of("넘어졌", "넘어져", "미끄러", "전도", "자빠"));
        TYPE_KEYWORDS.put("물체에맞음", List.of("맞았", "맞아", "떨어진 물체", "낙하물", "비래", "날아온"));
        TYPE_KEYWORDS.put("부딪힘", List.of("부딪", "충돌", "받혔", "치였"));
        TYPE_KEYWORDS.put("깔림.뒤집힘", List.of("깔렸", "깔려", "뒤집", "전복", "넘어진 지게차"));
        TYPE_KEYWORDS.put("무너짐", List.of("무너졌", "무너져", "붕괴", "도괴", "토사"));
        TYPE_KEYWORDS.put("감전", List.of("감전", "전기에 감", "누전", "충전전로", "전기 쇼크"));
        TYPE_KEYWORDS.put("화재", List.of("불이 났", "화재", "불길", "불에 타"));
        TYPE_KEYWORDS.put("폭발파열", List.of("폭발", "터졌", "터져", "파열"));
        TYPE_KEYWORDS.put("절단베임찔림", List.of("절단", "베였", "베여", "베임", "찔렸", "찔려", "잘렸", "잘려"));
        TYPE_KEYWORDS.put("이상온도물체접촉", List.of("화상", "데었", "데여", "고온", "뜨거운", "동상"));
        TYPE_KEYWORDS.put("화학물질누출접촉", List.of("누출", "유해물질", "약품", "가스가 새", "중독", "흡입"));
        TYPE_KEYWORDS.put("산소결핍", List.of("질식", "산소결핍", "밀폐공간", "숨을 못", "숨을 쉬지"));
        TYPE_KEYWORDS.put("빠짐익사", List.of("빠졌", "빠져", "익사", "수몰"));
        TYPE_KEYWORDS.put("사업장내교통사고", List.of("구내", "사업장 안에서 차", "구내운반차"));
        TYPE_KEYWORDS.put("사업장외교통사고", List.of("출장 중 사고", "교통사고", "차량 사고"));
        TYPE_KEYWORDS.put("불균형및무리한동작", List.of("무리한 동작", "허리를 삐", "삐끗", "근골격"));
        TYPE_KEYWORDS.put("업무상질병", List.of("직업병", "업무상 질병", "과로", "소음성 난청"));
        TYPE_KEYWORDS.put("폭력행위", List.of("폭행", "폭력", "맞아서 다"));
    }

    private static final List<String> FATAL_SIGNS = List.of(
            "사망", "숨졌", "숨져", "숨을 거", "사망자", "돌아가셨", "즉사", "심정지", "사고사");

    private static final List<String> SEVERE_SIGNS = List.of(
            "입원", "수술", "중상", "의식이 없", "의식불명", "골절", "절단", "119", "응급실",
            "구급차", "실려", "후송", "이송", "중태", "위독", "혼수", "장해");

    private static final List<String> MINOR_SIGNS = List.of(
            "찰과", "타박", "경상", "가벼운", "약만 바", "연고", "멍이 들", "긁혔");

    /** 유형을 확정으로 볼 최소 근거 수. 표현이 하나만 걸리면 오탐일 수 있어 추정으로 표시한다. */
    private static final String FALLBACK_TYPE = "기타";

    public Result classify(String situation) {
        String text = situation == null ? "" : situation;

        String bestType = null;
        int bestHits = 0;
        int bestPosition = Integer.MAX_VALUE;
        int matchedTypes = 0;

        for (Map.Entry<String, List<String>> entry : TYPE_KEYWORDS.entrySet()) {
            Match match = match(text, entry.getValue());
            if (match.hits() == 0) {
                continue;
            }
            matchedTypes++;
            // 걸린 표현이 같은 수면 먼저 나온 쪽을 택한다.
            // "프레스에 손이 끼여서 손가락이 절단됐습니다"처럼 사고 방식(끼임)을 먼저 쓰고
            // 다친 결과(절단)를 뒤에 쓰는 게 보통이라, 앞쪽이 사고 자체를 가리킨다.
            if (match.hits() > bestHits
                    || (match.hits() == bestHits && match.position() < bestPosition)) {
                bestHits = match.hits();
                bestPosition = match.position();
                bestType = entry.getKey();
            }
        }

        Severity severity = severityOf(text);
        if (bestType == null) {
            return new Result(FALLBACK_TYPE, false, severity);
        }
        // 표현이 둘 이상 걸렸거나, 걸린 유형이 하나뿐이면 확정으로 본다.
        // 여러 유형이 한 번씩 걸린 경우는 헷갈릴 수 있으니 프론트가 확인을 받게 한다.
        return new Result(bestType, bestHits >= 2 || matchedTypes == 1, severity);
    }

    /** 걸린 표현 수와, 그중 가장 앞에 나온 위치 */
    private record Match(int hits, int position) {
    }

    private Match match(String text, List<String> keywords) {
        int hits = 0;
        int first = Integer.MAX_VALUE;
        for (String keyword : keywords) {
            int at = text.indexOf(keyword);
            if (at >= 0) {
                hits++;
                first = Math.min(first, at);
            }
        }
        return new Match(hits, first);
    }

    /**
     * 사망 표현이 있으면 다른 표현과 상관없이 사망으로 본다.
     * 사고 서술에는 "실려 갔지만 사망했다"처럼 여러 단계가 함께 적히는데,
     * 안내 강도는 가장 무거운 쪽에 맞춰야 한다.
     */
    private Severity severityOf(String text) {
        if (containsAny(text, FATAL_SIGNS)) {
            return Severity.FATAL;
        }
        if (containsAny(text, SEVERE_SIGNS)) {
            return Severity.SEVERE;
        }
        if (containsAny(text, MINOR_SIGNS)) {
            return Severity.MINOR;
        }
        return Severity.UNKNOWN;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 프론트가 "유형이 틀렸다면 고르세요" 목록을 그릴 수 있게 분류 가능한 유형을 알려 준다. */
    public List<String> knownTypes() {
        return new ArrayList<>(TYPE_KEYWORDS.keySet());
    }
}
