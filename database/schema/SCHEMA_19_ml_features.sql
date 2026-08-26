-- SCHEMA_19_ml_features.sql
-- ML 피드백 반영 : LIGHTGBM enum + 건설공사금액 + 작업종류 다중 매핑
-- 선행: SCHEMA_3(workplace), SCHEMA_9(checklist_item.work_type 적재). DBeaver Alt+X. 재실행 안전.
-- 성별/연령/근무기간은 ML이 고정값으로 처리 → DB 컬럼 없음.

-- ────────────────────────────────────────────────
-- 1. 진단방식 enum(assess_method_t) — LIGHTGBM 은 SCHEMA_8 에서 이미 추가됨.
--    확인만: SELECT e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid
--            WHERE t.typname='assess_method_t';
--    (혹시 LIGHTGBM 이 없으면 아래 한 줄만 단독 실행)
-- ALTER TYPE assess_method_t ADD VALUE IF NOT EXISTS 'LIGHTGBM';

-- ────────────────────────────────────────────────
-- 2. 건설공사금액 (건설업 ML 피처, 사용자 입력) — 단위: 원
ALTER TABLE workplace ADD COLUMN IF NOT EXISTS construction_amount NUMERIC;
COMMENT ON COLUMN workplace.construction_amount IS '건설공사금액(원). 건설업만 입력, 제조업 NULL.';

-- ────────────────────────────────────────────────
-- 3. 작업종류 다중 매핑
--    (1) 선택 목록용 코드 테이블 — checklist_item.work_type 에서 추출 (건설35+제조33)
CREATE TABLE IF NOT EXISTS code_work_type (
    work_type_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_industry VARCHAR(20)  NOT NULL,   -- 건설업 / 제조업
    work_type       VARCHAR(100) NOT NULL,
    UNIQUE (target_industry, work_type)
);

INSERT INTO code_work_type (target_industry, work_type)
SELECT DISTINCT target_industry, work_type
FROM   checklist_item
WHERE  work_type IS NOT NULL AND is_active
ON CONFLICT (target_industry, work_type) DO NOTHING;

--    (2) 사업장 ↔ 작업종류 연결 (사업장 하나에 여러 작업)
CREATE TABLE IF NOT EXISTS workplace_work_type (
    workplace_id BIGINT       NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    work_type    VARCHAR(100) NOT NULL,
    PRIMARY KEY (workplace_id, work_type)
);
CREATE INDEX IF NOT EXISTS idx_wwt_worktype ON workplace_work_type (work_type);

-- 선택 목록 조회용 뷰 (프론트 드롭다운/체크박스용)
CREATE OR REPLACE VIEW v_ref_work_type_list AS
SELECT target_industry, work_type
FROM   code_work_type
ORDER  BY target_industry, work_type;

-- ============================================================
-- 검증
-- ============================================================
-- 작업종류 개수 (건설35 / 제조33 예상)
SELECT target_industry, count(*) AS 작업종류수
FROM   code_work_type GROUP BY target_industry ORDER BY target_industry;

-- 진단방식 enum 값 확인 (LIGHTGBM 포함 여부)
SELECT e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid
WHERE t.typname='assess_method_t' ORDER BY e.enumsortorder;

-- workplace 새 컬럼 확인
SELECT column_name FROM information_schema.columns
WHERE table_name='workplace' AND column_name='construction_amount';
