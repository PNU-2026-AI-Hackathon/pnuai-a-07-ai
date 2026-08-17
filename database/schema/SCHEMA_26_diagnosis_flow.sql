-- ============================================================
-- 안전진단 흐름 개편
-- 사업장 객관 정보 → 체크리스트 → 진단 → 사례 → 예방가이드
-- ============================================================

ALTER TABLE workplace
    ADD COLUMN IF NOT EXISTS machine_type VARCHAR(200),
    ADD COLUMN IF NOT EXISTS machine_count INT CHECK (machine_count IS NULL OR machine_count >= 0),
    ADD COLUMN IF NOT EXISTS safety_device_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS storage_location VARCHAR(200),
    ADD COLUMN IF NOT EXISTS storage_method VARCHAR(200);

COMMENT ON COLUMN workplace.machine_type IS '주요 기계·설비 종류(복수 입력 가능)';
COMMENT ON COLUMN workplace.machine_count IS '사업장 내 주요 기계·설비 총수';
COMMENT ON COLUMN workplace.safety_device_status IS '초기 안전장치 상태: INSTALLED/PARTIAL/NONE/UNKNOWN';
COMMENT ON COLUMN workplace.storage_location IS '자재·물건의 주요 적재 위치';
COMMENT ON COLUMN workplace.storage_method IS '자재·물건의 적재 방식 또는 높이';
