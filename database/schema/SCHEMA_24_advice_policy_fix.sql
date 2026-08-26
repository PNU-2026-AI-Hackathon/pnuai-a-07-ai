-- SCHEMA_24_advice_policy_fix.sql
-- fn_accident_advice 정책 필터 개선: title 정규식 → field(분야) 조건
-- 이유: title '예방' 매칭으로 "사과 기상 재해예방(농림축산어업)" 등 노이즈 유입 →
--       백엔드가 코드에서 우회 중. field 로 걸러 DB단에서 깔끔하게 해결.
-- 선행: SCHEMA_10/11/12. DBeaver Alt+X (또는 함수 안 Ctrl+Enter). 재실행 안전.

CREATE OR REPLACE FUNCTION fn_accident_advice(
    p_industry       VARCHAR,
    p_accident_type  VARCHAR,
    p_is_severe      BOOLEAN DEFAULT TRUE)
RETURNS TABLE(
    layer     TEXT,
    priority  INT,
    title     TEXT,
    reason    TEXT,
    detail    TEXT,
    agency    TEXT,
    reference TEXT,
    url       TEXT
) LANGUAGE plpgsql AS $$
BEGIN
    RETURN QUERY
    WITH admin AS (
        SELECT 1 AS lr, '행정'::text AS layer, ap.priority,
               ap.title::text,
               (CASE WHEN ap.is_critical_only THEN '중대재해 필수 조치'
                     ELSE '사고 발생 시 의무' END)::text AS reason,
               (COALESCE(ap.deadline_text,'기한없음')||' · '||ap.action_summary)::text AS detail,
               ap.agency::text, ap.legal_basis::text AS reference, ap.form_url::text AS url
        FROM admin_procedure ap
        WHERE ap.is_active AND (p_is_severe OR NOT ap.is_critical_only)
    ),
    legal AS (
        SELECT 2 AS lr, '법률'::text AS layer, 1 AS priority,
               lp.case_name::text AS title,
               (CASE WHEN lp.content ILIKE '%'||p_accident_type||'%'
                     THEN p_accident_type||' 재해 유사 판례'
                     ELSE '산업안전보건법 위반 판례' END)::text AS reason,
               left(COALESCE(lp.summary, lp.holding, ''),120)::text AS detail,
               lp.court::text AS agency,
               (COALESCE(lp.case_no,'')||' '||COALESCE(lp.decision_date::text,''))::text AS reference,
               lp.source_url::text AS url
        FROM law_precedent lp
        ORDER BY (CASE WHEN lp.content ILIKE '%'||p_accident_type||'%' THEN 0 ELSE 1 END),
                 lp.decision_date DESC NULLS LAST
        LIMIT 3
    ),
    policy AS (
        -- 정책: field(분야) 로 산재·안전·고용 관련만. 농림축산어업 등 무관 분야 제외.
        SELECT 3 AS lr, '정책'::text AS layer,
               (CASE WHEN ps.field = '행정·안전' THEN 1
                     WHEN ps.field = '고용·창업' THEN 2
                     ELSE 3 END) AS priority,
               ps.title::text,
               (CASE WHEN ps.target ILIKE '%'||p_industry||'%' OR ps.content ILIKE '%'||p_industry||'%'
                     THEN p_industry||' 대상 · 재발방지 지원'
                     ELSE '사업주 지원' END)::text AS reason,
               (COALESCE(ps.support_type,'')||' · '||COALESCE(left(ps.summary,70),''))::text AS detail,
               ps.agency::text, ps.apply_deadline::text AS reference, ps.detail_url::text AS url
        FROM policy_service ps
        WHERE ps.is_employer
          AND ps.field IN ('행정·안전', '고용·창업', '보건·의료')   -- 노이즈 분야 제외
        ORDER BY priority LIMIT 5
    )
    SELECT u.layer, u.priority, u.title, u.reason, u.detail, u.agency, u.reference, u.url
    FROM (SELECT * FROM admin
          UNION ALL SELECT * FROM legal
          UNION ALL SELECT * FROM policy) u
    ORDER BY u.lr, u.priority;
END $$;

-- 검증: 정책 계층에 농업 노이즈가 안 나오는지
SELECT layer, title, agency
FROM fn_accident_advice('제조업','끼임',TRUE)
WHERE layer='정책';
-- 기대: 산재예방 융자·요율제·컨설팅·대체인력 등만. '사과 기상 재해예방' 안 나와야 함.
