-- ============================================================
-- SafeWork AI - SCHEMA_8_APICONTRACT.SQL
-- ML 입출력 스키마 ↔ DB 정합 (프런트·백엔드·ML 계약 고정)
-- ============================================================
-- 배경
--   ML RiskPredictResponse 를 백엔드가 risk_assessment 에 저장하고,
--   프런트가 유효한 입력값(업종/규모/지역/체크리스트)을 보내려면
--   DB가 응답을 담을 컬럼과 참조 도메인을 제공해야 한다.
--
-- ML 응답 필드 ↔ DB 매핑
--   risk_score           -> risk_assessment.risk_score        (NULL 허용으로 변경)
--   risk_grade           -> risk_assessment.risk_grade         (NULL 허용)
--   base_component       -> risk_assessment.base_component     (신규 컬럼)
--   checklist_component  -> risk_assessment.checklist_component (신규 컬럼)
--   match_level          -> risk_assessment.match_level        (신규 컬럼)
--   top_risks[]          -> risk_assessment.predicted_accident_types (JSONB)
--   severity_prediction[]-> risk_assessment.predicted_severity        (JSONB)
--   model_version        -> risk_assessment.model_version      (기존)
--
-- 실행: DBeaver(ai_safework), 자동커밋(기본) 상태로 Alt+X. 재실행 안전(멱등).
-- ============================================================

-- ------------------------------------------------------------
-- 1. 진단 방식 enum 에 LIGHTGBM 추가
--    (트랜잭션 밖에서 실행되어야 안전 → 파일 맨 앞, BEGIN 미사용)
-- ------------------------------------------------------------
ALTER TYPE assess_method_t ADD VALUE IF NOT EXISTS 'LIGHTGBM';

-- ------------------------------------------------------------
-- 2. risk_assessment 확장 — ML 응답 저장용
-- ------------------------------------------------------------
-- 2-1. NONE 매칭 시 점수/등급이 없을 수 있음 → NULL 허용
ALTER TABLE risk_assessment ALTER COLUMN risk_score DROP NOT NULL;
ALTER TABLE risk_assessment ALTER COLUMN risk_grade DROP NOT NULL;

-- 2-2. 점수 분해 컬럼 (프런트가 "왜 이 점수인지" 표시)
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS base_component      NUMERIC(6,3);
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS checklist_component NUMERIC(6,3) DEFAULT 0;
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS match_level         VARCHAR(20);

-- 2-3. LightGBM top-k 예측 결과 (리스트라 JSONB)
--   predicted_accident_types 예: [{"type":"끼임","prob":0.42}, ...]
--   predicted_severity        예: [{"severity":"사망자","prob":0.11}, ...]
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS predicted_accident_types JSONB;
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS predicted_severity        JSONB;

-- 2-4. match_level 값 제약
ALTER TABLE risk_assessment DROP CONSTRAINT IF EXISTS ck_match_level;
ALTER TABLE risk_assessment ADD CONSTRAINT ck_match_level
    CHECK (match_level IS NULL
           OR match_level IN ('EXACT','INDUSTRY_SIZE','INDUSTRY','NONE'));

-- ------------------------------------------------------------
-- 3. code_size_class — 모델 입력값 매핑 (DB 10종 → 모델 9종)
--    20~29인 / 30~49인 → 20~49인 으로 병합
-- ------------------------------------------------------------
ALTER TABLE code_size_class ADD COLUMN IF NOT EXISTS model_size_class VARCHAR(20);
UPDATE code_size_class SET model_size_class = size_class;                 -- 기본: 동일
UPDATE code_size_class SET model_size_class = '20~49인'
 WHERE size_class IN ('20~29인','30~49인');

-- ------------------------------------------------------------
-- 4. sub_industry 정규화 매핑 스켈레톤 (DB 58종 → 모델 44종)
--    model_category 는 ML 이 정규화 규칙을 주면 UPDATE 로 채운다.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_sub_industry (
    sub_industry   VARCHAR(100) PRIMARY KEY,  -- DB 원본 (accident_case.sub_industry)
    model_category VARCHAR(100),              -- ML 정규화 44종 (★ ML 제공 후 채움)
    note           VARCHAR(200)
);
-- 실데이터의 모든 세부업종을 자동 시드 (58종)
INSERT INTO code_sub_industry(sub_industry)
SELECT DISTINCT sub_industry FROM accident_case
WHERE sub_industry IS NOT NULL
ON CONFLICT (sub_industry) DO NOTHING;

-- ------------------------------------------------------------
-- 5. 프런트 참조 도메인 뷰 — 백엔드 GET 엔드포인트 소스
--    프런트가 값을 하드코딩하지 않고 DB 값을 그대로 사용하게 함
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_ref_industry AS
    SELECT industry AS code, display_name, is_high_risk
    FROM code_industry ORDER BY industry;

CREATE OR REPLACE VIEW v_ref_size_class AS
    SELECT size_class AS code, display_name, model_size_class, sort_order
    FROM code_size_class ORDER BY sort_order;

CREATE OR REPLACE VIEW v_ref_region AS
    SELECT region AS code, display_name, is_target
    FROM code_region ORDER BY region;

CREATE OR REPLACE VIEW v_ref_checklist AS
    SELECT item_code, category, question, description, target_industry,
           risk_weight, is_critical, law_ref, display_order
    FROM checklist_item
    WHERE is_active
    ORDER BY display_order;

-- ------------------------------------------------------------
-- 6. fn_coldstart_assess 갱신 — 새 컬럼(분해값)에 저장
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_coldstart_assess(
    p_workplace_id BIGINT, p_model_ver VARCHAR DEFAULT 'coldstart-v1')
RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE r RECORD; v_id BIGINT;
BEGIN
    SELECT * INTO r FROM fn_coldstart_score(p_workplace_id);
    INSERT INTO risk_assessment
        (workplace_id, method, risk_score, risk_grade,
         base_component, checklist_component, match_level,
         top_accident_type, model_version)
    VALUES
        (p_workplace_id, 'COLDSTART', r.risk_score, r.risk_grade,
         r.base_component, r.checklist_component, r.match_level,
         r.top_accident_type, p_model_ver)
    RETURNING assessment_id INTO v_id;
    RETURN v_id;
END $$;

-- ============================================================
-- 백엔드 저장 예시 (참고용, 실행 X)
--   ML RiskPredictResponse 를 받아 이렇게 INSERT 하면 된다.
-- ============================================================
-- INSERT INTO risk_assessment
--   (workplace_id, submission_id, method, risk_score, risk_grade,
--    base_component, checklist_component, match_level,
--    top_accident_type, predicted_accident_types, predicted_severity,
--    model_version)
-- VALUES
--   (:wp, :sub, 'LIGHTGBM', :risk_score, :risk_grade,
--    :base, :checklist, :match_level,
--    :top1_type,                              -- top_risks[0].type (대표 1개)
--    :top_risks_json, :severity_json,         -- 전체 top-k 리스트(JSONB)
--    'lightgbm-2026.07');

-- ============================================================
-- 검증
-- ============================================================
-- 신규 컬럼 확인
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name='risk_assessment'
  AND column_name IN ('risk_score','risk_grade','base_component',
        'checklist_component','match_level','predicted_accident_types','predicted_severity')
ORDER BY column_name;

-- size_class 매핑 확인 (20~29인·30~49인 → 20~49인)
SELECT size_class, model_size_class FROM code_size_class ORDER BY sort_order;

-- sub_industry 스켈레톤 (58행, model_category 는 아직 NULL)
SELECT count(*) AS 전체, count(model_category) AS 매핑완료 FROM code_sub_industry;

-- 참조 뷰 동작 확인
SELECT * FROM v_ref_size_class;
