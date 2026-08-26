-- SCHEMA_22_fix_assess_columns.sql
-- 버그 수정: SCHEMA_21 의 fn_coldstart_assess 가 존재하지 않는 컬럼에 INSERT 한다.
--
--   ERROR: column "base_component" of relation "risk_assessment" does not exist
--
-- SCHEMA_21 은 점수 근거를 raw_features(jsonb) 대신 개별 컬럼으로 분리하려 했으나
-- 컬럼을 만드는 ALTER 가 submission_id 하나만 들어가고 나머지 3개가 빠졌다.
-- plpgsql 은 CREATE FUNCTION 시점에 본문을 검증하지 않아 스크립트는 성공하고
-- 실제 호출에서만 실패하기 때문에 발견이 늦었다.
--
-- 선행: SCHEMA_21. DBeaver Alt+X 또는 psql. 재실행 안전.

ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS base_component      NUMERIC(6,3);
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS checklist_component NUMERIC(6,3);
ALTER TABLE risk_assessment ADD COLUMN IF NOT EXISTS match_level         TEXT;

COMMENT ON COLUMN risk_assessment.base_component      IS '동종·동규모·동지역 통계에서 산출한 기본 점수 (0~60)';
COMMENT ON COLUMN risk_assessment.checklist_component IS '체크리스트 미비 항목에서 산출한 점수 (0~40)';
COMMENT ON COLUMN risk_assessment.match_level         IS '베이스라인 매칭 수준 (EXACT/INDUSTRY_SIZE/INDUSTRY/NONE)';

-- ============================================================
-- 검증
-- ============================================================
-- 제출 이력이 있는 사업장으로 진단이 실제로 수행되는지 확인한다.
-- (SCHEMA_21 적용 후 이 스크립트 없이 호출하면 위 ERROR 로 실패한다)
--
-- SELECT fn_coldstart_assess(1);
-- SELECT assessment_id, submission_id, risk_score, risk_grade,
--        base_component, checklist_component, match_level
-- FROM   risk_assessment ORDER BY assessment_id DESC LIMIT 1;
--   → submission_id 와 세 컬럼이 모두 채워지면 성공
