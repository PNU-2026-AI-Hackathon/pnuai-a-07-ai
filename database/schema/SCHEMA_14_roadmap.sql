-- SCHEMA_14_ROADMAP.SQL
-- 사고 발생 → 사업주 대상 행정·법률·정책 조언
-- 입력: 업종 + 발생한 재해유형 + 중대재해 여부
-- 선행: admin_procedure, policy_service, law_precedent. DBeaver Alt+X. 재실행 안전.

CREATE OR REPLACE FUNCTION fn_accident_advice(
    p_industry       VARCHAR,       -- 사업장 업종 (제조업/건설업 …)
    p_accident_type  VARCHAR,       -- 발생한 재해유형 (끼임/떨어짐 …)
    p_is_severe      BOOLEAN DEFAULT TRUE)  -- 사망·중대재해 여부
RETURNS TABLE(
    layer     TEXT,   -- 행정 / 법률 / 정책
    priority  INT,
    title     TEXT,   -- 조언 항목
    reason    TEXT,   -- 이 사고와의 연관
    detail    TEXT,   -- 무엇을 해야 하나
    agency    TEXT,
    reference TEXT,
    url       TEXT
) LANGUAGE plpgsql AS $$
BEGIN
    RETURN QUERY
    WITH admin AS (
        -- 행정: 사고 발생 시 이행 의무 (중대재해 여부로 필터)
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
        -- 법률: 발생한 재해유형과 유사한 판례 우선 (처벌 리스크 대비)
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
        -- 정책: 재발방지·인력 등 사업주 대상 지원 (업종 매칭 우선)
        SELECT 3 AS lr, '정책'::text AS layer,
               (CASE WHEN ps.title ~ '예방|융자|요율|컨설팅|상생|안전보건관리' THEN 1
                     WHEN ps.title ~ '대체인력|직장복귀|원직장' THEN 2 ELSE 3 END) AS priority,
               ps.title::text,
               (CASE WHEN ps.target ILIKE '%'||p_industry||'%' OR ps.content ILIKE '%'||p_industry||'%'
                     THEN p_industry||' 대상 · 재발방지 지원'
                     ELSE '사업주 지원' END)::text AS reason,
               (COALESCE(ps.support_type,'')||' · '||COALESCE(left(ps.summary,70),''))::text AS detail,
               ps.agency::text, ps.apply_deadline::text AS reference, ps.detail_url::text AS url
        FROM policy_service ps
        WHERE ps.is_employer
          AND ps.title ~ '예방|융자|요율|컨설팅|상생|안전보건관리|대체인력|직장복귀|원직장'
        ORDER BY priority LIMIT 5
    )
    SELECT u.layer, u.priority, u.title, u.reason, u.detail, u.agency, u.reference, u.url
    FROM (SELECT * FROM admin
          UNION ALL SELECT * FROM legal
          UNION ALL SELECT * FROM policy) u
    ORDER BY u.lr, u.priority;
END $$;

COMMENT ON FUNCTION fn_accident_advice(VARCHAR, VARCHAR, BOOLEAN) IS
  '사고 발생 시 업종·재해유형에 맞는 행정·법률·정책 조언을 우선순위순으로 반환.';

-- ============================================================
-- 데모 (인서트 없이 바로 호출)
-- ============================================================
-- 예1) 제조업에서 '끼임' 중대재해 발생
SELECT layer AS 계층, title AS 항목, reason AS 왜, detail AS 조치, agency AS 기관
FROM fn_accident_advice('제조업', '끼임', TRUE);

-- 예2) 건설업에서 '떨어짐' 경미 사고 (중대재해 아님 → 즉시대응 절차 제외)
-- SELECT layer AS 계층, title AS 항목, reason AS 왜, detail AS 조치
-- FROM fn_accident_advice('건설업', '떨어짐', FALSE);
