-- SCHEMA_13_ADMINRULE.SQL
-- 행정규칙(고시·훈령·예규) — 국가법령정보 행정규칙 API. 행정 계층 근거 보강.
-- 선행: SCHEMA_3. DBeaver Alt+X. 적재는 fetch_admrules.py 로.

CREATE TABLE IF NOT EXISTS law_admin_rule (
    admrul_id   BIGINT PRIMARY KEY,         -- 행정규칙일련번호
    rule_name   VARCHAR(300) NOT NULL,      -- 행정규칙명
    rule_type   VARCHAR(40),                -- 종류 (고시/훈령/예규 등)
    ministry    VARCHAR(100),               -- 소관부처명
    issue_date  DATE,                       -- 발령일자
    issue_no    VARCHAR(50),                -- 발령번호
    content     TEXT,                       -- 본문
    keyword     VARCHAR(40),                -- 수집 키워드
    source_url  VARCHAR(500),
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admrule_ministry ON law_admin_rule (ministry);
CREATE INDEX IF NOT EXISTS idx_admrule_content_trgm
    ON law_admin_rule USING gin (content gin_trgm_ops);

-- 조회용 뷰
CREATE OR REPLACE VIEW v_admin_rule AS
SELECT admrul_id, rule_name, rule_type, ministry, issue_date, keyword
FROM   law_admin_rule
ORDER  BY issue_date DESC NULLS LAST;

-- 적재 후 검증
-- SELECT count(*) FROM law_admin_rule;
-- SELECT rule_type, count(*) FROM law_admin_rule GROUP BY rule_type;
-- SELECT rule_name, ministry, issue_date FROM v_admin_rule LIMIT 15;
