-- ============================================================
-- SafeWork AI - SCHEMA_6_COLDSTART.SQL
-- 콜드스타트 위험도 스코어링 함수
-- ============================================================
-- 목적
--   진단 이력이 없는 신규 사업장의 위험도(0~100)를 산출한다.
--   하이브리드 = 업종 베이스라인(peer 통계) + 체크리스트 가감점.
--
-- 점수 구성
--   base_component      0~60 : coldstart_baseline 의 중대재해비율(serious_ratio)
--                              를 전체 그룹 중 백분위로 환산 (× 60)
--   checklist_component 0~40 : 최신 체크리스트 제출의 'NO'(미비) 응답
--                              risk_weight 합 (is_critical 항목 ×2, 40 상한)
--   risk_score = clamp(base + checklist, 0, 100)
--   등급: LOW<25, 25≤MEDIUM<50, 50≤HIGH<75, 75≤CRITICAL
--
-- 매칭 fallback (콜드스타트 핵심)
--   정확(업종×규모×지역) → 없으면 업종×규모 평균 → 없으면 업종 평균 → NONE
--
-- 설명가능성
--   base/checklist 분해값과 매칭수준·최빈재해를 함께 반환 → SHAP 유사 설명 근거
--
-- 실행: DBeaver(ai_safework) Alt+X. 재실행 안전.
-- 선행: coldstart_baseline 적재, SCHEMA_3(workplace/checklist), SCHEMA_4(FK).
-- ============================================================

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

    -- ---- 1) 베이스라인 매칭 (fallback 단계적) ----
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

    -- ---- 베이스 점수: serious_ratio 백분위 × 60 ----
    SELECT (count(*) FILTER (WHERE b.serious_ratio <= v_serious))::numeric
             / NULLIF(count(*),0) * 60
      INTO v_base
    FROM coldstart_baseline b;
    v_base := COALESCE(v_base, 0);

    -- ---- 2) 체크리스트 가감점: 최신 제출의 'NO' 응답 ----
    SELECT s.submission_id INTO v_sub
    FROM checklist_submission s
    WHERE s.workplace_id = p_workplace_id
    ORDER BY s.submitted_at DESC
    LIMIT 1;

    v_check := 0;
    IF v_sub IS NOT NULL THEN
        SELECT LEAST(40, COALESCE(sum(
                 ci.risk_weight * CASE WHEN ci.is_critical THEN 2 ELSE 1 END), 0))
          INTO v_check
        FROM checklist_response cr
        JOIN checklist_item ci ON ci.item_id = cr.item_id
        WHERE cr.submission_id = v_sub AND cr.answer = 'NO';
    END IF;

    -- ---- 최종 점수·등급 ----
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

COMMENT ON FUNCTION fn_coldstart_score(BIGINT) IS
  '신규 사업장 콜드스타트 위험도. 베이스라인 백분위(0~60)+체크리스트(0~40).';


-- ============================================================
-- (선택) 진단 결과를 risk_assessment 에 저장하는 래퍼
--   체크리스트 제출 후 호출하면 진단 1건을 기록한다.
-- ============================================================
CREATE OR REPLACE FUNCTION fn_coldstart_assess(p_workplace_id BIGINT, p_model_ver VARCHAR DEFAULT 'coldstart-v1')
RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE r RECORD; v_id BIGINT;
BEGIN
    SELECT * INTO r FROM fn_coldstart_score(p_workplace_id);
    INSERT INTO risk_assessment
        (workplace_id, method, risk_score, risk_grade,
         top_accident_type, model_version, raw_features)
    VALUES
        (p_workplace_id, 'COLDSTART', r.risk_score, r.risk_grade,
         r.top_accident_type, p_model_ver,
         jsonb_build_object('base', r.base_component,
                            'checklist', r.checklist_component,
                            'match_level', r.match_level,
                            'peer_serious_ratio', r.peer_serious_ratio))
    RETURNING assessment_id INTO v_id;
    RETURN v_id;
END $$;


-- ============================================================
-- 데모 테스트 (BEGIN~ROLLBACK: 실행 후 흔적 안 남김)
--   제조업 × 5인 미만 × 부산 신규 사업장 → 예상 risk_score ≈ 32.8 (MEDIUM)
-- ============================================================
BEGIN;

INSERT INTO app_user(email, password_hash, name)
VALUES ('_demo_coldstart@test', 'x', '데모');

INSERT INTO workplace(owner_user_id, name, industry, size_class, region)
SELECT user_id, '데모공장', '제조업', '5인 미만', '부산'
FROM app_user WHERE email='_demo_coldstart@test';

SELECT *
FROM fn_coldstart_score(
    (SELECT w.workplace_id FROM workplace w
     JOIN app_user u ON u.user_id=w.owner_user_id
     WHERE u.email='_demo_coldstart@test'));

ROLLBACK;   -- 데모 데이터 원복. 위 SELECT 결과는 그대로 표시됨.

-- 기대: risk_score≈32.8, grade=MEDIUM, base≈32.8, checklist=0,
--       match_level=EXACT, peer_serious_ratio≈0.0175, top=끼임
