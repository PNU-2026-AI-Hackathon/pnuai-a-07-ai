-- SCHEMA_16a_checklist_sif_pre.sql
-- SIF 체크리스트 통합 [사전] — 컬럼 추가 + 생략 컬럼 NULL 허용
-- 실행 순서: 이 파일 → checklist_item_insert.sql → SCHEMA_16b_checklist_sif_post.sql
-- DBeaver Alt+X. 재실행 안전.

-- 데이터모델링 파일이 넣는 새 컬럼
ALTER TABLE checklist_item ADD COLUMN IF NOT EXISTS law_ref     TEXT;   -- '1956,1958,1340' 형태 article_id 목록
ALTER TABLE checklist_item ADD COLUMN IF NOT EXISTS description TEXT;   -- [재해유형] 재해개요

-- 그쪽 INSERT가 값을 주지 않는 컬럼은 NULL 허용이어야 에러 안 남
ALTER TABLE checklist_item ALTER COLUMN is_critical   DROP NOT NULL;
ALTER TABLE checklist_item ALTER COLUMN work_type     DROP NOT NULL;
ALTER TABLE checklist_item ALTER COLUMN display_order DROP NOT NULL;
