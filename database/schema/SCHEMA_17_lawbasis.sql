-- SCHEMA_17_lawbasis.sql
-- 근거 법령 자동 첨부 : 재해유형/진단 미비항목 → law_ref → 실제 조문
-- 선행: SCHEMA_16b (law_ref·v_checklist_law). DBeaver Alt+X. 재실행 안전.

-- 1. 재해유형별 근거 법령 (예방·사후조언용)
--    입력: 업종 + 재해유형 → 그 사고 관련 점검항목이 참조하는 조문을 빈도순
CREATE OR REPLACE FUNCTION fn_accident_law_basis(
    p_industry      VARCHAR,
    p_accident_type VARCHAR,
    p_limit         INT DEFAULT 8)
RETURNS TABLE(
    law_name    VARCHAR,
    article_no  VARCHAR,
    clause_no   VARCHAR,
    title       VARCHAR,
    ref_items   INT,        -- 이 조문을 근거로 삼는 점검항목 수
    work_types  TEXT        -- 관련 작업
) LANGUAGE sql AS $$
    SELECT la.law_name, la.article_no, la.clause_no, la.title,
           count(DISTINCT ci.item_code)::int              AS ref_items,
           string_agg(DISTINCT ci.work_type, ', ')         AS work_types
    FROM   checklist_item ci
    CROSS  JOIN LATERAL unnest(string_to_array(ci.law_ref, ',')) AS r(aid)
    JOIN   law_article la ON la.article_id = trim(r.aid)::int
    WHERE  ci.is_active
      AND  ci.target_industry = p_industry
      AND  ci.category        = p_accident_type
      AND  ci.law_ref IS NOT NULL AND ci.law_ref <> ''
    GROUP  BY la.article_id, la.law_name, la.article_no, la.clause_no, la.title
    ORDER  BY ref_items DESC, la.article_id
    LIMIT  p_limit;
$$;

COMMENT ON FUNCTION fn_accident_law_basis(VARCHAR,VARCHAR,INT) IS
  '업종·재해유형으로 예방 점검항목이 참조하는 근거 법 조문을 빈도순 반환.';

-- 2. 진단 미비항목의 근거 법령 (진단 리포트용)
--    입력: 사업장 → 최신 제출에서 '아니오(NO)' 답한 항목이 위반 우려하는 조문
CREATE OR REPLACE FUNCTION fn_diagnosis_law_basis(p_workplace_id BIGINT)
RETURNS TABLE(
    item_code   VARCHAR,
    work_type   VARCHAR,
    category    VARCHAR,
    question    TEXT,
    risk_weight NUMERIC,
    is_critical BOOLEAN,
    law_name    VARCHAR,
    article_no  VARCHAR,
    title       VARCHAR
) LANGUAGE plpgsql AS $$
DECLARE v_sub BIGINT;
BEGIN
    SELECT s.submission_id INTO v_sub
    FROM   checklist_submission s
    WHERE  s.workplace_id = p_workplace_id
    ORDER  BY s.submitted_at DESC LIMIT 1;
    IF v_sub IS NULL THEN RETURN; END IF;

    RETURN QUERY
    SELECT ci.item_code, ci.work_type, ci.category, ci.question,
           ci.risk_weight, ci.is_critical,
           la.law_name, la.article_no, la.title
    FROM   checklist_response cr
    JOIN   checklist_item ci ON ci.item_id = cr.item_id
    CROSS  JOIN LATERAL unnest(string_to_array(ci.law_ref, ',')) AS r(aid)
    JOIN   law_article la ON la.article_id = trim(r.aid)::int
    WHERE  cr.submission_id = v_sub
      AND  cr.answer = 'NO'
      AND  ci.law_ref IS NOT NULL AND ci.law_ref <> ''
    ORDER  BY ci.is_critical DESC, ci.risk_weight DESC, ci.item_code;
END $$;

COMMENT ON FUNCTION fn_diagnosis_law_basis(BIGINT) IS
  '사업장 최신 진단에서 미비(NO) 항목이 위반 우려하는 근거 법 조문 반환.';

-- ============================================================
-- 데모
-- ============================================================
-- 제조업 '끼임' 예방 근거 법령 (제92조 운전정지·제87조 회전축 방호 예상)
SELECT law_name AS 법령, article_no AS 조, title AS 제목, ref_items AS 관련항목수
FROM fn_accident_law_basis('제조업', '끼임', 8);

-- 건설업 '떨어짐' (제42조 추락방지·제44조 안전대 예상)
SELECT law_name AS 법령, article_no AS 조, title AS 제목, ref_items AS 관련항목수
FROM fn_accident_law_basis('건설업', '떨어짐', 8);
