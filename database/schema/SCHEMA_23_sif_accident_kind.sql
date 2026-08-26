-- SCHEMA_23_sif_accident_kind.sql
-- sif_case 제조업 accident_kind 채우기 (재해개요·위험상황·기인물 키워드 자동분류)
-- 어휘: 건설업이 이미 쓰는 sif 기술어(추락/낙하/전도/베임/끼임...)에 맞춤.
-- v2: 깔림·부딪힘을 끼임(협착)보다 먼저 판정 (명시적 '깔려/충돌' 우선).
-- 제조업 전체 재분류 (재실행 안전, 멱등). DBeaver Alt+X.

UPDATE sif_case c
SET    accident_kind = k.kind
FROM (
    SELECT sif_id,
      CASE
        WHEN t ~ '감전|누전|충전부|활선|전기에'                                  THEN '감전'
        WHEN t ~ '폭발'                                                        THEN '폭발'
        WHEN t ~ '화재|불길|연소'                                              THEN '화재'
        WHEN t ~ '화상|데임|고온|뜨거|용융|용탕|쇳물|스팀|증기에|황산|염산'      THEN '화상'
        WHEN t ~ '질식|산소결핍|밀폐공간'                                       THEN '질식'
        WHEN t ~ '중독|유해가스|일산화탄소|황화수소|가스에'                      THEN '중독'
        WHEN t ~ '절단|베이|베임|날에|칼날|그라인더|재단|커터'                    THEN '베임'
        WHEN t ~ '찔림|찔려|찔린|관통'                                          THEN '찔림'
        WHEN t ~ '깔려|깔림|전복|뒤집|하부에 협착|밑에 협착'                      THEN '깔림'
        WHEN t ~ '충돌|부딪|들이받|받혀|추돌'                                    THEN '부딪힘'
        WHEN t ~ '붕괴|무너'                                                    THEN '붕괴'
        WHEN t ~ '낙하|비래|낙하물|떨어지는 물체|물체에 맞|맞아|맞고'            THEN '낙하'
        WHEN t ~ '추락|아래로 떨어|떨어져|사다리|고소작업|개구부|비계|난간|지붕'  THEN '추락'
        WHEN t ~ '협착|끼이|끼여|끼임|말려|감겨|감김|물려|프레스|롤러|사출|분쇄|컨베이어' THEN '끼임'
        WHEN t ~ '전도|넘어|미끄러|걸려'                                        THEN '전도'
        WHEN t ~ '익사|빠져|빠짐'                                              THEN '익사'
        WHEN t ~ '파열|터져|터짐'                                              THEN '파열'
        ELSE '기타'
      END AS kind
    FROM (
      SELECT sif_id,
             coalesce(accident_summary,'')||' '||coalesce(high_risk_situation,'')||' '||
             coalesce(causal_object,'')||' '||coalesce(causal_factor,'') AS t
      FROM   sif_case
      WHERE  industry_div LIKE '제조%'      -- 제조업 전체 재분류
    ) x
) k
WHERE c.sif_id = k.sif_id;

-- ============================================================
-- 검증
-- ============================================================
SELECT accident_kind, count(*) FROM sif_case
WHERE industry_div LIKE '제조%'
GROUP BY accident_kind ORDER BY count(*) DESC;

SELECT count(*) AS 미분류 FROM sif_case
WHERE accident_kind IS NULL OR accident_kind = '';

-- 아까 오분류였던 케이스 재확인 (깔려/충돌 → 깔림/부딪힘 로 바뀌었는지)
SELECT accident_kind, left(accident_summary, 50) AS 개요, causal_object AS 기인물
FROM sif_case
WHERE industry_div LIKE '제조%' AND (accident_summary ~ '깔려|충돌')
ORDER BY sif_id LIMIT 12;
