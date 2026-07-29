DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE accident_case (
    industry       VARCHAR(50),
    sub_industry   VARCHAR(100),
    accident_type  VARCHAR(40),
    size_class     VARCHAR(20),
    age_group      VARCHAR(20),
    severity       VARCHAR(20),
    region         VARCHAR(20),
    disease_flag   SMALLINT,
    death_flag     SMALLINT
);

CREATE TABLE size_injury_rate (
    size_class   VARCHAR(20) NOT NULL,
    year         INT NOT NULL,
    injury_rate  NUMERIC(6,3),
    PRIMARY KEY (size_class, year)
);

CREATE TABLE sif_case (
    sif_id              BIGINT PRIMARY KEY,
    industry_div        VARCHAR(20),
    accident_kind       VARCHAR(40),
    accident_summary    TEXT,
    causal_object       VARCHAR(100),
    high_risk_situation TEXT,
    causal_factor       TEXT,
    countermeasure      TEXT,
    content             TEXT
);

CREATE TABLE office_stat (
    labor_office   VARCHAR(30) NOT NULL,
    year           INT NOT NULL,
    injured_count  INT,
    death_count    INT,
    PRIMARY KEY (labor_office, year)
);

CREATE TABLE coldstart_baseline (
    baseline_id       BIGINT PRIMARY KEY,
    industry          VARCHAR(50),
    size_class        VARCHAR(20),
    region            VARCHAR(20),
    accident_count    INT,
    death_count       INT,
    serious_ratio     NUMERIC(6,4),
    top_accident_type VARCHAR(40)
);

CREATE INDEX idx_case_ind_size ON accident_case (industry, size_class);
CREATE INDEX idx_case_region_ind ON accident_case (region, industry);
CREATE INDEX idx_sif_content_trgm ON sif_case USING gin (content gin_trgm_ops);
CREATE INDEX idx_baseline ON coldstart_baseline (industry, size_class, region);
