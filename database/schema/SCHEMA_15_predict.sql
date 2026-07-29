-- SCHEMA_15_PREDICT.SQL
-- 사전 예측: 사업장 특성(업종·규모·지역) → 예상 재해유형 top-K (통계 베이스라인)
-- 선행: accident_case 적재. DBeaver Alt+X. 재실행 안전.

-- 1. 업종×규모×지역별 재해유형 분포 (accident_case 64만 집계)
DROP TABLE IF EXISTS accident_type_dist;
CREATE TABLE accident_type_dist AS
WITH g AS (
    SELECT industry, size_class, region, accident_type,
           count(*)::int AS n, sum(death_flag)::int AS death_n
    FROM accident_case
    GROUP BY industry, size_class, region, accident_type
),
t AS (
    SELECT industry, size_class, region, sum(n) AS tot FROM g
    GROUP BY industry, size_class, region
)
SELECT g.industry, g.size_class, g.region, g.accident_type, g.n, g.death_n,
       round(g.n::numeric / t.tot, 4)                 AS ratio,
       round(g.death_n::numeric / NULLIF(g.n,0), 4)   AS death_ratio
FROM g JOIN t USING (industry, size_class, region);

ALTER TABLE accident_type_dist
    ADD PRIMARY KEY (industry, size_class, region, accident_type);

-- 2. 예측 함수 (fallback: 정확 → 업종+규모 → 업종)
CREATE OR REPLACE FUNCTION fn_predict_accidents(
    p_industry VARCHAR, p_size VARCHAR, p_region VARCHAR, p_top_k INT DEFAULT 5)
RETURNS TABLE(
    rank          INT,
    accident_type VARCHAR,
    cases         INT,
    ratio         NUMERIC,
    death_ratio   NUMERIC,
    match_level   TEXT
) LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM accident_type_dist d
               WHERE d.industry=p_industry AND d.size_class=p_size AND d.region=p_region) THEN
        RETURN QUERY
        SELECT row_number() OVER (ORDER BY d.n DESC)::int, d.accident_type, d.n,
               d.ratio, d.death_ratio, 'EXACT'::text
        FROM accident_type_dist d
        WHERE d.industry=p_industry AND d.size_class=p_size AND d.region=p_region
        ORDER BY d.n DESC LIMIT p_top_k;
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM accident_type_dist d
               WHERE d.industry=p_industry AND d.size_class=p_size) THEN
        RETURN QUERY
        WITH agg AS (
            SELECT d.accident_type, sum(d.n) AS n, sum(d.death_n) AS dn
            FROM accident_type_dist d
            WHERE d.industry=p_industry AND d.size_class=p_size
            GROUP BY d.accident_type)
        SELECT row_number() OVER (ORDER BY a.n DESC)::int, a.accident_type, a.n::int,
               round(a.n::numeric / sum(a.n) OVER (), 4),
               round(a.dn::numeric / NULLIF(a.n,0), 4), 'INDUSTRY_SIZE'::text
        FROM agg a ORDER BY a.n DESC LIMIT p_top_k;
        RETURN;
    END IF;

    RETURN QUERY
    WITH agg AS (
        SELECT d.accident_type, sum(d.n) AS n, sum(d.death_n) AS dn
        FROM accident_type_dist d
        WHERE d.industry=p_industry
        GROUP BY d.accident_type)
    SELECT row_number() OVER (ORDER BY a.n DESC)::int, a.accident_type, a.n::int,
           round(a.n::numeric / sum(a.n) OVER (), 4),
           round(a.dn::numeric / NULLIF(a.n,0), 4), 'INDUSTRY'::text
    FROM agg a ORDER BY a.n DESC LIMIT p_top_k;
END $$;

COMMENT ON FUNCTION fn_predict_accidents(VARCHAR,VARCHAR,VARCHAR,INT) IS
  '사업장 특성(업종·규모·지역)으로 예상 재해유형 top-K 를 통계 베이스라인으로 예측.';

-- ============================================================
-- 검증·데모
-- ============================================================
SELECT count(*) AS 분포행수 FROM accident_type_dist;

-- 제조업·5인미만·부산 → 예상 사고 (끼임 1위 예상)
SELECT * FROM fn_predict_accidents('제조업', '5인 미만', '부산', 5);

-- 건설업·5인미만·부산 → 예상 사고 (떨어짐 1위 예상)
SELECT * FROM fn_predict_accidents('건설업', '5인 미만', '부산', 5);
