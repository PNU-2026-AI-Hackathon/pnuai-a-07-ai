-- SCHEMA_12_PRECEDENT.SQL
-- 판례 (법률 계층) — 국가법령정보 판례 API
-- 선행: SCHEMA_3. DBeaver Alt+X. 적재는 fetch_precedents.py 로.

CREATE TABLE IF NOT EXISTS law_precedent (
    prec_id       BIGINT PRIMARY KEY,       -- 판례일련번호
    case_name     VARCHAR(300),             -- 사건명
    case_no       VARCHAR(100),             -- 사건번호
    court         VARCHAR(100),             -- 법원명
    decision_date DATE,                     -- 선고일자
    case_type     VARCHAR(50),              -- 사건종류명
    holding       TEXT,                     -- 판시사항
    summary       TEXT,                     -- 판결요지
    ref_articles  TEXT,                     -- 참조조문
    content       TEXT,                     -- 판례내용(본문)
    keyword       VARCHAR(40),              -- 수집 키워드
    source_url    VARCHAR(500),
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_prec_date ON law_precedent (decision_date DESC);
CREATE INDEX IF NOT EXISTS idx_prec_content_trgm
    ON law_precedent USING gin (content gin_trgm_ops);

-- 최근 판례 조회용 뷰
CREATE OR REPLACE VIEW v_precedent_recent AS
SELECT prec_id, case_name, court, decision_date, case_type,
       left(summary, 200) AS 요지, keyword
FROM   law_precedent
ORDER  BY decision_date DESC NULLS LAST;

-- 적재 후 검증
-- SELECT count(*) FROM law_precedent;
-- SELECT keyword, count(*) FROM law_precedent GROUP BY keyword;
-- SELECT case_name, court, decision_date FROM v_precedent_recent LIMIT 10;
