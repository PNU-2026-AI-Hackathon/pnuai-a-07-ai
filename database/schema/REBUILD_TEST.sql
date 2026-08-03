-- REBUILD_TEST.sql — 스키마 순서 검증용 (테스트 DB 전용)
-- 실행: schema 폴더에서
--   psql -U postgres -d ai_safework_test -f REBUILD_TEST.sql
-- 선행: ai_safework_test 에 기반 데이터(ai_safework.sql) 적재됨.
-- ON_ERROR_STOP: 첫 오류에서 멈춤 → 어느 파일에서 깨지는지 바로 확인.

\set ON_ERROR_STOP on
\echo '========== [1] SCHEMA_3_service =========='
\i SCHEMA_3_service.sql
\echo '========== [2] SCHEMA_4_codemaster =========='
\i SCHEMA_4_codemaster.sql
\echo '========== [3] SCHEMA_5_benchmark =========='
\i SCHEMA_5_benchmark.sql
\echo '========== [4] SCHEMA_6_coldstart =========='
\i SCHEMA_6_coldstart.sql
\echo '========== [5] SCHEMA_8_apicontract =========='
\i SCHEMA_8_apicontract.sql
\echo '========== [6] SCHEMA_9_checklist_v2 =========='
\i SCHEMA_9_checklist_v2.sql
\echo '========== [7] SCHEMA_10_admin =========='
\i SCHEMA_10_admin.sql
\echo '========== [8] SCHEMA_11_policy =========='
\i SCHEMA_11_policy.sql
\echo '========== [9] SCHEMA_12_precedent =========='
\i SCHEMA_12_precedent.sql
\echo '========== [10] SCHEMA_13_adminrule =========='
\i SCHEMA_13_adminrule.sql
\echo '========== [11] SCHEMA_14_roadmap =========='
\i SCHEMA_14_roadmap.sql
\echo '========== [12] SCHEMA_15_predict =========='
\i SCHEMA_15_predict.sql
\echo '========== [13] SCHEMA_16a_checklist_sif_pre =========='
\i SCHEMA_16a_checklist_sif_pre.sql
\echo '========== [14] SCHEMA_16b_checklist_sif_post =========='
\i SCHEMA_16b_checklist_sif_post.sql
\echo '========== [15] SCHEMA_17_lawbasis =========='
\i SCHEMA_17_lawbasis.sql
\echo '========== [16] SCHEMA_18_prevention_guide =========='
\i SCHEMA_18_prevention_guide.sql
\echo '========== [17] SCHEMA_19_ml_features =========='
\i SCHEMA_19_ml_features.sql
\echo '========== [18] SCHEMA_20_hybrid_enum =========='
\i SCHEMA_20_hybrid_enum.sql
\echo ''
\echo '########## 스키마 순서 검증 완료 — 오류 없이 여기까지 왔으면 성공 ##########'

-- 객체 생성 확인
\echo '--- 테이블 수 ---'
SELECT count(*) AS tables FROM information_schema.tables WHERE table_schema='public';
\echo '--- 함수 목록 ---'
SELECT proname FROM pg_proc WHERE proname LIKE 'fn_%' ORDER BY proname;
