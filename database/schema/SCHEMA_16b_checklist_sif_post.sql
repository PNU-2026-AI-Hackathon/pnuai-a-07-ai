-- SCHEMA_16b_checklist_sif_post.sql
-- SIF 체크리스트 통합 [사후] — 구코드 제거 + 컬럼 의미 정렬 + 파생값 + 근거법령 뷰
-- 선행: SCHEMA_16a → checklist_item_insert.sql. DBeaver Alt+X. 재실행 안전.

-- 1. 기존 CON-/MFG- 체크리스트 제거 (SIF- 로 표준 교체, 중복 방지)
DELETE FROM checklist_item WHERE item_code LIKE 'CON-%' OR item_code LIKE 'MFG-%';

-- 2. 컬럼 의미 정렬
--    데이터모델링: category=작업('굴착 작업'), description='[재해유형] 개요...'
--    내 스키마    : work_type=작업, category=재해유형
--    + is_critical(weight>=8) 재계산, display_order = item_code 뒤 숫자
UPDATE checklist_item SET
    work_type      = category,
    category       = COALESCE(substring(description from '^\[([^\]]+)\]'), '기타'),
    is_critical    = (risk_weight >= 8),
    evidence_cases = to_jsonb(description),
    display_order  = NULLIF(regexp_replace(item_code, '\D', '', 'g'), '')::int
WHERE item_code LIKE 'SIF-%'
  AND work_type IS NULL;          -- 재실행 시 정렬 완료된 행은 건너뜀

-- 3. law_article 시퀀스 복구 (그쪽 파일이 999로 낮춰 놓음 → 향후 충돌 예방)
SELECT setval(pg_get_serial_sequence('public.law_article','article_id'),
              (SELECT max(article_id) FROM law_article));

-- 4. 근거법령 뷰 — law_ref(콤마 id) 를 실제 조문으로 펼침
CREATE OR REPLACE VIEW v_checklist_law AS
SELECT ci.item_code, ci.target_industry, ci.work_type, ci.category,
       ci.question, ci.risk_weight, ci.is_critical,
       la.article_id, la.law_name, la.article_no, la.clause_no, la.title
FROM   checklist_item ci
CROSS  JOIN LATERAL unnest(string_to_array(ci.law_ref, ',')) AS r(aid)
JOIN   law_article la ON la.article_id = trim(r.aid)::int
WHERE  ci.is_active AND ci.law_ref IS NOT NULL AND ci.law_ref <> ''
ORDER  BY ci.item_code, la.article_id;

-- 5. 참조 뷰에 law_ref 노출 (컬럼 변경 → DROP 후 재생성)
DROP VIEW IF EXISTS v_ref_checklist;
CREATE VIEW v_ref_checklist AS
    SELECT item_code, target_industry, work_type, category,
           question, risk_weight, is_critical, law_ref, display_order
    FROM   checklist_item
    WHERE  is_active
    ORDER  BY target_industry, work_type, display_order;

-- ============================================================
-- 검증
-- ============================================================
SELECT count(*) FILTER (WHERE item_code LIKE 'SIF-%')                          AS sif건수,
       count(*) FILTER (WHERE item_code LIKE 'CON-%' OR item_code LIKE 'MFG-%') AS 구코드건수,
       count(*) FILTER (WHERE law_ref IS NOT NULL AND item_code LIKE 'SIF-%')   AS 법령매핑건수
FROM   checklist_item;
-- 기대값: 835 / 0 / 835

-- 문항 → 근거 조문 샘플
SELECT item_code, work_type, category, law_name, article_no, title
FROM   v_checklist_law WHERE item_code = 'SIF-CON-0001';
