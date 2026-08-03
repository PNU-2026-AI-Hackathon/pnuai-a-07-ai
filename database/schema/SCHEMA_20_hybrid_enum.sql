-- SCHEMA_20_hybrid_enum.sql
-- 진단방식 enum 에 HYBRID 추가 (콜드스타트 + LightGBM 혼합 결과 태그)
-- 백엔드 확정: risk_assessment.method 에 'HYBRID' 기록.
-- ※ ADD VALUE 는 트랜잭션 안에서 막힐 수 있음 → DBeaver Auto-commit 켜고 이 한 줄만 실행.

ALTER TYPE assess_method_t ADD VALUE IF NOT EXISTS 'HYBRID';

-- 확인
SELECT e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid
WHERE t.typname='assess_method_t' ORDER BY e.enumsortorder;
-- 기대: COLDSTART, LIGHTGBM, HYBRID (+ 기존 값)
