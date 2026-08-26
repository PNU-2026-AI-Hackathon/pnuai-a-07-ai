-- ============================================================
-- SafeWork AI - SCHEMA_2_PATCH.SQL
-- 기존 SCHEMA_2.SQL 보정 (SCHEMA_3 적용 "전"에 실행)
-- ============================================================
-- 변경 사유
--   1) accident_case 에 PK 부재 → SCHEMA_3 에서 FK 참조 불가, 중복행 식별 불가
--   2) coldstart_baseline / sif_case 의 수동 PK → IDENTITY 로 전환하면 적재 편의
--      (단 sif_id 는 원본 데이터 ID를 쓰는 중이면 그대로 두는 것이 맞음)
--   3) 조인 키 도메인 통일을 위한 코드 마스터 테이블 추가
-- ============================================================

-- 1. accident_case 대리키 부여
ALTER TABLE accident_case
    ADD COLUMN case_id BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE accident_case
    ADD CONSTRAINT pk_accident_case PRIMARY KEY (case_id);

-- 발생연도 컬럼이 없어 시간 편향(Temporal Bias) 가중치 적용이 불가함 → 추가
ALTER TABLE accident_case
    ADD COLUMN occur_year INT;
CREATE INDEX idx_case_year ON accident_case (occur_year);

-- 2. NOT NULL 보강 (조인 키는 NULL 이면 안 됨)
ALTER TABLE accident_case
    ALTER COLUMN industry   SET NOT NULL,
    ALTER COLUMN size_class SET NOT NULL,
    ALTER COLUMN region     SET NOT NULL;

-- 3. 플래그 컬럼 값 제약
ALTER TABLE accident_case
    ADD CONSTRAINT ck_disease_flag CHECK (disease_flag IN (0,1)),
    ADD CONSTRAINT ck_death_flag   CHECK (death_flag   IN (0,1));

-- 4. 재해율 범위 제약
ALTER TABLE size_injury_rate
    ADD CONSTRAINT ck_injury_rate CHECK (injury_rate >= 0),
    ADD CONSTRAINT ck_year_range  CHECK (year BETWEEN 2000 AND 2100);

-- 5. coldstart_baseline: 조회 조합의 유일성 보장
ALTER TABLE coldstart_baseline
    ADD CONSTRAINT uq_baseline UNIQUE (industry, size_class, region);
ALTER TABLE coldstart_baseline
    ADD CONSTRAINT ck_serious_ratio CHECK (serious_ratio BETWEEN 0 AND 1);


-- ============================================================
-- 6. 코드 마스터 테이블 (가장 중요)
--    workplace.industry 와 accident_case.industry 문자열이 1글자라도
--    다르면 콜드스타트 조회가 통째로 실패함. 마스터로 강제한다.
-- ============================================================
CREATE TABLE code_industry (
    industry     VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_high_risk BOOLEAN NOT NULL DEFAULT FALSE   -- 조선/금속가공 등
);

CREATE TABLE code_size_class (
    size_class   VARCHAR(20) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    min_employee INT,
    max_employee INT,
    sort_order   INT NOT NULL DEFAULT 0
);

CREATE TABLE code_region (
    region       VARCHAR(20) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    is_target    BOOLEAN NOT NULL DEFAULT FALSE   -- 부산/경남 = TRUE
);

CREATE TABLE code_accident_type (
    accident_type VARCHAR(40) PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL
);

-- 규모 구간 시드 (실제 데이터 구간에 맞춰 조정할 것)
INSERT INTO code_size_class (size_class, display_name, min_employee, max_employee, sort_order) VALUES
  ('5_UNDER',   '5인 미만',      0,   4, 1),
  ('5_49',      '5~49인',        5,  49, 2),
  ('50_99',     '50~99인',      50,  99, 3),
  ('100_299',   '100~299인',   100, 299, 4),
  ('300_OVER',  '300인 이상',  300, NULL, 5);

INSERT INTO code_region (region, display_name, is_target) VALUES
  ('BUSAN',     '부산',   TRUE),
  ('GYEONGNAM', '경남',   TRUE);

-- 참고: 마스터 적재 완료 후 아래 FK를 걸어 도메인을 고정한다.
--   ALTER TABLE accident_case ADD CONSTRAINT fk_case_ind
--       FOREIGN KEY (industry) REFERENCES code_industry(industry);
--   ALTER TABLE workplace     ADD CONSTRAINT fk_wp_ind
--       FOREIGN KEY (industry) REFERENCES code_industry(industry);
--   (size_class, region 도 동일하게 적용)
