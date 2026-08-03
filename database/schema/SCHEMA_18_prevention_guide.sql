-- SCHEMA_18_prevention_guide.sql
-- 예방 가이드 : 사업장 특성 → 예상 재해유형 → 예방 체크리스트 → 근거 법령 (한 번에)
-- 선행: SCHEMA_15(예측) · SCHEMA_16b(law_ref) · SCHEMA_17(근거법령). DBeaver Alt+X. 재실행 안전.

-- 반환 타입(law_basis TEXT[]) 변경을 위해 기존 함수 제거 후 재생성
DROP FUNCTION IF EXISTS fn_prevention_guide(VARCHAR,VARCHAR,VARCHAR,INT,INT);

CREATE OR REPLACE FUNCTION fn_prevention_guide(
    p_industry VARCHAR,          -- 업종 (제조업/건설업)
    p_size     VARCHAR,          -- 규모 (5인 미만 …)
    p_region   VARCHAR,          -- 지역 (부산 …)
    p_top_k    INT DEFAULT 3,    -- 예상 사고유형 상위 몇 개
    p_items    INT DEFAULT 5)    -- 사고유형당 예방 점검항목 몇 개
RETURNS TABLE(
    accident_rank   INT,
    accident_type   VARCHAR,
    accident_ratio  NUMERIC,     -- 그 사고 발생 비율
    death_ratio     NUMERIC,     -- 그 사고의 사망 비율
    item_code       VARCHAR,     -- 체크리스트 없으면 NULL
    work_type       VARCHAR,
    question        TEXT,        -- 예방 점검 항목
    risk_weight     NUMERIC,
    is_critical     BOOLEAN,
    law_basis       TEXT[]       -- 근거 법 조문 배열 (JDBC rs.getArray())
) LANGUAGE sql AS $$
    WITH pred AS (
        SELECT * FROM fn_predict_accidents(p_industry, p_size, p_region, p_top_k)
    ),
    ranked AS (
        SELECT p.rank AS accident_rank, p.accident_type,
               p.ratio AS accident_ratio, p.death_ratio,
               ci.item_code, ci.work_type, ci.question,
               ci.risk_weight, ci.is_critical, ci.law_ref,
               row_number() OVER (PARTITION BY p.accident_type
                    ORDER BY ci.is_critical DESC NULLS LAST,
                             ci.risk_weight DESC NULLS LAST, ci.item_code) AS rn
        FROM pred p
        LEFT JOIN checklist_item ci      -- 체크리스트 없는 사고(질병 등)도 rank 유지 위해 LEFT
          ON ci.is_active
         AND ci.target_industry = p_industry
         AND ci.category        = p.accident_type      -- 재해유형 매칭
    )
    SELECT r.accident_rank, r.accident_type, r.accident_ratio, r.death_ratio,
           r.item_code, r.work_type, r.question, r.risk_weight, r.is_critical,
           (SELECT array_agg(DISTINCT la.law_name || ' ' || la.article_no)
            FROM   unnest(string_to_array(r.law_ref, ',')) AS x(aid)
            JOIN   law_article la ON la.article_id = trim(x.aid)::int) AS law_basis
    FROM   ranked r
    WHERE  r.rn <= p_items          -- 체크리스트 없는 사고는 rn=1 한 행만 (item_code NULL)
    ORDER  BY r.accident_rank, r.is_critical DESC NULLS LAST,
              r.risk_weight DESC NULLS LAST, r.item_code;
$$;

COMMENT ON FUNCTION fn_prevention_guide(VARCHAR,VARCHAR,VARCHAR,INT,INT) IS
  '사업장 특성 → 예상 재해유형별 예방 체크리스트 + 근거 법령을 한 번에 반환. 체크리스트 없는 사고는 item_code NULL 한 행으로 rank 유지.';

-- ============================================================
-- 데모
-- ============================================================
-- 제조업·5인미만·부산 : 예상 사고 상위3 × 사고당 점검 3개
-- (업무상질병처럼 체크리스트 없는 사고는 item_code NULL 로 한 줄 나와 rank 연속성 유지)
SELECT accident_rank AS 순위, accident_type AS 예상사고,
       round(accident_ratio*100,1) AS "발생%",
       work_type AS 작업, question AS 예방점검, law_basis AS 근거법령
FROM fn_prevention_guide('제조업', '5인 미만', '부산', 3, 3);
