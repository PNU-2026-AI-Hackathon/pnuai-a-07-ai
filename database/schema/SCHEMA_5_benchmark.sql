-- ============================================================
-- SafeWork AI - SCHEMA_5_BENCHMARK.SQL
-- 업종 벤치마크 집계 (accident_case 64만 → industry_benchmark)
-- ============================================================
-- 목적
--   "동종업종 평균 대비 내 사업장" 비교 기능의 데이터를 만든다.
--   accident_case 를 업종×규모×지역×연도로 집계해 사망비율·질병비율·
--   최빈 재해유형을 산출하고, 규모별 재해율(size_injury_rate)을 붙인다.
--
-- 산출 지표 (그룹 = industry × size_class × region × year)
--   sample_count      : 사고 건수 (표본 크기)
--   death_count       : 사망사고 건수
--   death_ratio       : 사망 비율 = death_count / sample_count
--   disease_ratio     : 업무상질병 비율
--   top_accident_type : 최빈 재해유형 (mode)
--   avg_injury_rate   : 규모별 공식 재해율 (size_injury_rate 조인)
--   avg_risk_score / percentile_* : 진단 데이터 쌓인 뒤 채움(현재 NULL)
--
-- 실행: DBeaver(ai_safework) 에서 Alt+X. 재실행 안전(멱등).
-- 선행: accident_case, size_injury_rate 적재 완료 상태.
-- 예상 결과: industry_benchmark 약 4,748행.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. 스키마 보강 — accident_case 집계 지표 컬럼 추가
--    (industry_benchmark 원설계는 재해율·위험점수 위주였으나,
--     지금 단계에서 실제로 산출 가능한 지표를 담도록 확장)
-- ------------------------------------------------------------
ALTER TABLE industry_benchmark ADD COLUMN IF NOT EXISTS death_count       INT;
ALTER TABLE industry_benchmark ADD COLUMN IF NOT EXISTS death_ratio       NUMERIC(7,4);
ALTER TABLE industry_benchmark ADD COLUMN IF NOT EXISTS disease_ratio     NUMERIC(7,4);
ALTER TABLE industry_benchmark ADD COLUMN IF NOT EXISTS top_accident_type VARCHAR(40);

-- ------------------------------------------------------------
-- 2. 집계 재적재
-- ------------------------------------------------------------
TRUNCATE industry_benchmark;

INSERT INTO industry_benchmark
    (industry, size_class, region, year,
     sample_count, death_count, death_ratio, disease_ratio,
     top_accident_type, avg_injury_rate)
SELECT
    a.industry,
    a.size_class,
    a.region,
    a.year,
    count(*)                                            AS sample_count,
    sum(a.death_flag)                                   AS death_count,
    round(sum(a.death_flag)::numeric   / count(*), 4)   AS death_ratio,
    round(sum(a.disease_flag)::numeric / count(*), 4)   AS disease_ratio,
    mode() WITHIN GROUP (ORDER BY a.accident_type)      AS top_accident_type,
    max(sir.injury_rate)                                AS avg_injury_rate
FROM accident_case a
LEFT JOIN size_injury_rate sir
       ON sir.size_class = a.size_class
      AND sir.year       = a.year
GROUP BY a.industry, a.size_class, a.region, a.year;

COMMIT;

-- ------------------------------------------------------------
-- 3. 조회 편의 뷰 — 그룹별 '최신 연도' 벤치마크
--    진단 없이도 "동종업종 대비" 조회에 바로 쓸 수 있다.
--    workplace 의 (industry,size_class,region) 으로 조인해 쓰면 됨.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_peer_benchmark AS
SELECT DISTINCT ON (industry, size_class, region)
       industry, size_class, region, year,
       sample_count, death_count, death_ratio, disease_ratio,
       top_accident_type, avg_injury_rate
FROM   industry_benchmark
ORDER  BY industry, size_class, region, year DESC;

-- ============================================================
-- 검증 (COMMIT 후 실행)
-- ============================================================
-- 총 그룹 수: 약 4,748
SELECT count(*) AS benchmark_rows FROM industry_benchmark;

-- 전체 사망비율 sanity: 약 0.0170 (1.70%)
SELECT round(sum(death_count)::numeric / sum(sample_count), 4) AS overall_death_ratio
FROM   industry_benchmark;

-- 부산·경남 제조업 2024년 벤치마크 (표본 20건 이상)
SELECT size_class, region, sample_count, death_count, death_ratio, top_accident_type
FROM   industry_benchmark
WHERE  industry='제조업' AND region IN ('부산','경남') AND year=2024
  AND  sample_count >= 20
ORDER  BY region, sample_count DESC;

-- 표본이 작은 그룹은 신뢰도 낮음 → 서비스 노출 시 sample_count >= 30 권장
-- SELECT count(*) FROM industry_benchmark WHERE sample_count >= 30;   -- 약 2,923
