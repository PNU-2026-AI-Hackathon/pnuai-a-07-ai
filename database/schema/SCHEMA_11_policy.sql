-- SCHEMA_11_POLICY.SQL
-- 정책·지원 서비스 (정부24 공공서비스 API)
-- 선행: SCHEMA_3. DBeaver Alt+X. 적재는 fetch_policy.py 로.

CREATE TABLE IF NOT EXISTS policy_service (
    service_id     VARCHAR(30) PRIMARY KEY,   -- 서비스ID (정부24)
    title          VARCHAR(200) NOT NULL,     -- 서비스명
    summary        TEXT,                      -- 서비스목적요약
    support_type   VARCHAR(60),               -- 지원유형 (현금/융자/서비스 등)
    field          VARCHAR(60),               -- 서비스분야
    user_type      VARCHAR(60),               -- 사용자구분 (법인/시설/단체, 개인 등)
    is_employer    BOOLEAN NOT NULL DEFAULT FALSE, -- 사업주 대상 여부
    target         TEXT,                      -- 지원대상
    criteria       TEXT,                      -- 선정기준
    content        TEXT,                      -- 지원내용
    apply_method   VARCHAR(200),              -- 신청방법
    apply_deadline VARCHAR(400),              -- 신청기한
    agency         VARCHAR(100),              -- 소관기관명
    dept           VARCHAR(100),              -- 부서명
    receive_agency VARCHAR(200),              -- 접수기관
    phone          VARCHAR(200),              -- 전화문의
    detail_url     VARCHAR(300),              -- 상세조회URL
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_policy_employer ON policy_service (is_employer, field);

-- 사업주 대상 지원 서비스 (진단 후 "받을 수 있는 지원" 노출용)
CREATE OR REPLACE VIEW v_policy_employer AS
SELECT service_id, title, support_type, summary, target,
       apply_deadline, agency, phone, detail_url
FROM   policy_service
WHERE  is_employer
ORDER  BY title;

-- 적재 후 검증
-- SELECT count(*) FROM policy_service;
-- SELECT count(*) FILTER (WHERE is_employer) AS 사업주대상 FROM policy_service;
-- SELECT title, support_type, agency FROM v_policy_employer;
