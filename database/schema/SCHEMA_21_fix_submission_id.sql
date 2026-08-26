-- SCHEMA_21_fix_submission_id.sql
-- 버그 수정: fn_coldstart_assess 가 risk_assessment.submission_id 를 안 채움
-- → 진단이 어느 제출(submission) 기반인지 추적 불가. 최신 제출 id 를 저장하도록 수정.
-- 선행: SCHEMA_9(fn_coldstart_score). DBeaver Alt+X 또는 psql. 재실행 안전.

-- submission_id 컬럼 없으면 대비 (있으면 무시)
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS submission_id BIGINT;

CREATE OR REPLACE FUNCTION public.fn_coldstart_assess(
    p_workplace_id bigint,
    p_model_ver varchar DEFAULT 'coldstart-v1')
RETURNS bigint
LANGUAGE plpgsql AS $function$
DECLARE r RECORD; v_id BIGINT; v_sub BIGINT;
BEGIN
    -- fn_coldstart_score 가 체크리스트 점수 계산에 쓰는 것과 동일한 "최신 제출"
    SELECT s.submission_id INTO v_sub
    FROM   checklist_submission s
    WHERE  s.workplace_id = p_workplace_id
    ORDER  BY s.submitted_at DESC
    LIMIT  1;

    SELECT * INTO r FROM fn_coldstart_score(p_workplace_id);

    INSERT INTO risk_assessment
        (workplace_id, submission_id, method, risk_score, risk_grade,
         base_component, checklist_component, match_level,
         top_accident_type, model_version)
    VALUES
        (p_workplace_id, v_sub, 'COLDSTART', r.risk_score, r.risk_grade,
         r.base_component, r.checklist_component, r.match_level,
         r.top_accident_type, p_model_ver)
    RETURNING assessment_id INTO v_id;

    RETURN v_id;
END $function$;

-- ============================================================
-- 검증 (실데이터 있을 때)
-- ============================================================
-- 제출이 있는 사업장으로 진단 실행 → submission_id 채워지는지
-- SELECT fn_coldstart_assess(1);
-- SELECT assessment_id, workplace_id, submission_id, method, risk_score
-- FROM   risk_assessment ORDER BY assessment_id DESC LIMIT 3;
--   → submission_id 가 NULL 이 아니면 성공
--   (단, 해당 사업장에 제출이 없으면 정상적으로 NULL — 순수 베이스라인 진단)
