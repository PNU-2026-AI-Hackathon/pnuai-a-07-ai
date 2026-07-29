-- SCHEMA_9_CHECKLIST_V2.SQL
-- 체크리스트 835문항(SIF→LLM) 수용 + 비율 기반 스코어링
-- 실행: 이 파일(Alt+X) → load_checklist.py 적재 → VERIFY_CHECKLIST_V2.SQL

-- 1. 스키마 확장
ALTER TABLE checklist_item ADD COLUMN IF NOT EXISTS work_type      VARCHAR(100);
ALTER TABLE checklist_item ADD COLUMN IF NOT EXISTS evidence_cases JSONB;
ALTER TABLE checklist_item DROP CONSTRAINT IF EXISTS ck_ci_weight;
ALTER TABLE checklist_item ADD CONSTRAINT ck_ci_weight CHECK (risk_weight >= 0);
CREATE INDEX IF NOT EXISTS idx_ci_ind_work
    ON checklist_item (target_industry, work_type, is_active);

-- 2. fn_coldstart_score : checklist_component = (미비 가중치합 / 응답 가중치합) * 40
--    is_critical 항목은 가중치 ×2
CREATE OR REPLACE FUNCTION fn_coldstart_score(p_workplace_id BIGINT)
RETURNS TABLE(
    workplace_id        BIGINT,
    risk_score          NUMERIC,
    risk_grade          risk_grade_t,
    base_component      NUMERIC,
    checklist_component NUMERIC,
    match_level         TEXT,
    peer_serious_ratio  NUMERIC,
    top_accident_type   VARCHAR
) LANGUAGE plpgsql AS $$
DECLARE
    v_ind VARCHAR; v_size VARCHAR; v_region VARCHAR;
    v_serious NUMERIC; v_top VARCHAR; v_match TEXT;
    v_base NUMERIC; v_check NUMERIC; v_score NUMERIC; v_grade risk_grade_t;
    v_sub BIGINT;
BEGIN
    SELECT w.industry, w.size_class, w.region
      INTO v_ind, v_size, v_region
    FROM workplace w WHERE w.workplace_id = p_workplace_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'workplace % 가 없습니다', p_workplace_id;
    END IF;

    -- 베이스라인 매칭 (정확 → 업종+규모 → 업종 → NONE)
    SELECT b.serious_ratio, b.top_accident_type, 'EXACT'
      INTO v_serious, v_top, v_match
    FROM coldstart_baseline b
    WHERE b.industry=v_ind AND b.size_class=v_size AND b.region=v_region;

    IF v_serious IS NULL THEN
        SELECT avg(b.serious_ratio),
               mode() WITHIN GROUP (ORDER BY b.top_accident_type), 'INDUSTRY_SIZE'
          INTO v_serious, v_top, v_match
        FROM coldstart_baseline b
        WHERE b.industry=v_ind AND b.size_class=v_size;
    END IF;
    IF v_serious IS NULL THEN
        SELECT avg(b.serious_ratio),
               mode() WITHIN GROUP (ORDER BY b.top_accident_type), 'INDUSTRY'
          INTO v_serious, v_top, v_match
        FROM coldstart_baseline b
        WHERE b.industry=v_ind;
    END IF;
    IF v_serious IS NULL THEN
        v_serious := 0; v_top := NULL; v_match := 'NONE';
    END IF;

    -- 베이스 점수 (0~60): serious_ratio 백분위 × 60
    SELECT (count(*) FILTER (WHERE b.serious_ratio <= v_serious))::numeric
             / NULLIF(count(*),0) * 60
      INTO v_base
    FROM coldstart_baseline b;
    v_base := COALESCE(v_base, 0);

    -- 체크리스트 점수 (0~40): 최신 제출의 미비 비율
    SELECT s.submission_id INTO v_sub
    FROM checklist_submission s
    WHERE s.workplace_id = p_workplace_id
    ORDER BY s.submitted_at DESC LIMIT 1;

    v_check := 0;
    IF v_sub IS NOT NULL THEN
        SELECT COALESCE(
                 sum(ci.risk_weight * CASE WHEN ci.is_critical THEN 2 ELSE 1 END)
                     FILTER (WHERE cr.answer = 'NO')
                 / NULLIF(sum(ci.risk_weight * CASE WHEN ci.is_critical THEN 2 ELSE 1 END)
                     FILTER (WHERE cr.answer IN ('YES','NO')), 0)
                 * 40, 0)
          INTO v_check
        FROM checklist_response cr
        JOIN checklist_item ci ON ci.item_id = cr.item_id
        WHERE cr.submission_id = v_sub;
    END IF;

    v_score := round(LEAST(100, GREATEST(0, v_base + v_check)), 2);
    v_grade := (CASE
        WHEN v_score >= 75 THEN 'CRITICAL'
        WHEN v_score >= 50 THEN 'HIGH'
        WHEN v_score >= 25 THEN 'MEDIUM'
        ELSE 'LOW' END)::risk_grade_t;

    RETURN QUERY SELECT
        p_workplace_id, v_score, v_grade,
        round(v_base,2), round(v_check,2), v_match,
        round(v_serious,4), v_top;
END $$;

-- 3. 참조 뷰 (컬럼 구성이 바뀌므로 DROP 후 재생성)
DROP VIEW IF EXISTS v_ref_checklist;
CREATE VIEW v_ref_checklist AS
    SELECT item_code, target_industry, work_type, category,
           question, risk_weight, is_critical, display_order
    FROM checklist_item
    WHERE is_active
    ORDER BY target_industry, work_type, display_order;

CREATE OR REPLACE VIEW v_ref_work_type AS
    SELECT target_industry, work_type, count(*) AS 문항수
    FROM checklist_item
    WHERE is_active AND work_type IS NOT NULL
    GROUP BY target_industry, work_type
    ORDER BY target_industry, work_type;
