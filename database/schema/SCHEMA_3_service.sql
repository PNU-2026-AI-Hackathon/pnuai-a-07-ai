-- ============================================================
-- SafeWork AI - SCHEMA_3.SQL (서비스 계층)
-- DBMS : PostgreSQL 15+
-- 선행  : SCHEMA_2.SQL (참조 데이터 계층) 실행 후 적용
-- ============================================================
-- [계층 구조]
--   SCHEMA_2 = 참조 데이터 (공공데이터 적재, 읽기 전용, ML 학습/조회용)
--   SCHEMA_3 = 서비스 데이터 (사용자가 생성, 읽기/쓰기, 애플리케이션용)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
-- 주의: 벡터 임베딩은 DB가 아니라 FAISS가 담당한다 (계획서 기준).
--       pgvector 를 쓰지 않으며, law_chunk 는 FAISS 인덱스와 텍스트를 잇는 역할만 한다.


-- ============================================================
-- 0. 공통 ENUM 타입
-- ============================================================
CREATE TYPE user_role_t      AS ENUM ('OWNER', 'SAFETY_MANAGER', 'ADMIN');
CREATE TYPE risk_grade_t     AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE assess_method_t  AS ENUM ('XGBOOST', 'COLDSTART', 'HYBRID');
CREATE TYPE answer_t         AS ENUM ('YES', 'NO', 'NA');
CREATE TYPE chat_role_t      AS ENUM ('USER', 'ASSISTANT', 'SYSTEM');
CREATE TYPE report_status_t  AS ENUM ('PENDING', 'GENERATING', 'DONE', 'FAILED');


-- ============================================================
-- 1. 계정 / 인증
-- ============================================================
CREATE TABLE app_user (
    user_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,          -- BCrypt
    name           VARCHAR(50)  NOT NULL,
    phone          VARCHAR(20),
    role           user_role_t  NOT NULL DEFAULT 'OWNER',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  app_user IS '서비스 사용자 (사업주/안전담당자). "user"는 예약어라 app_user 사용';
COMMENT ON COLUMN app_user.password_hash IS 'Spring Security BCryptPasswordEncoder 결과';

-- JWT Refresh Token (Access Token은 무상태로 저장하지 않음)
CREATE TABLE refresh_token (
    token_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL,            -- 원문 저장 금지
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    user_agent   VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user   ON refresh_token (user_id);
CREATE INDEX idx_refresh_expire ON refresh_token (expires_at);


-- ============================================================
-- 2. 사업장 (서비스의 중심 엔티티)
-- ============================================================
CREATE TABLE workplace (
    workplace_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id    BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    biz_reg_no       VARCHAR(20) UNIQUE,                 -- 사업자등록번호
    -- 아래 3개는 SCHEMA_2 참조 테이블과 조인되는 핵심 키. 값 도메인 반드시 통일할 것
    industry         VARCHAR(50)  NOT NULL,              -- accident_case.industry 와 동일 도메인
    sub_industry     VARCHAR(100),                       -- accident_case.sub_industry
    size_class       VARCHAR(20)  NOT NULL,              -- size_injury_rate.size_class
    region           VARCHAR(20)  NOT NULL,              -- accident_case.region (부산/경남 등)
    employee_count   INT CHECK (employee_count >= 0),
    address          VARCHAR(255),
    labor_office     VARCHAR(30),                        -- office_stat.labor_office (관할 노동관서)
    latitude         NUMERIC(9,6),                       -- 기상청 API 격자 변환용
    longitude        NUMERIC(9,6),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wp_owner    ON workplace (owner_user_id);
CREATE INDEX idx_wp_ind_size ON workplace (industry, size_class, region);
COMMENT ON COLUMN workplace.industry IS
  'SCHEMA_2 조인 키. accident_case / coldstart_baseline 의 industry 와 문자열이 정확히 일치해야 함';

-- 한 사업장에 여러 담당자를 붙이는 경우 (권장 기능)
CREATE TABLE workplace_member (
    workplace_id  BIGINT NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    role          user_role_t NOT NULL DEFAULT 'SAFETY_MANAGER',
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workplace_id, user_id)
);


-- ============================================================
-- 3. SIF 체크리스트
-- ============================================================
-- 마스터: 관리자가 사전 정의. sif_case(SCHEMA_2)에서 도출한 문항
CREATE TABLE checklist_item (
    item_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_code       VARCHAR(30) NOT NULL UNIQUE,     -- 예: 'MFG-GUARD-01'
    category        VARCHAR(50) NOT NULL,            -- 끼임/추락/화재폭발/밀폐공간 ...
    question        TEXT        NOT NULL,
    description     TEXT,                            -- 비전공 사업주용 쉬운 설명
    -- 적용 범위: NULL = 전체 공통
    target_industry VARCHAR(50),
    target_region   VARCHAR(20),                     -- 부산·경남 특화 문항 표시용
    risk_weight     NUMERIC(5,2) NOT NULL DEFAULT 1.0, -- 콜드스타트 가감점 가중치
    is_critical     BOOLEAN NOT NULL DEFAULT FALSE,  -- 중대재해 직결 문항
    sif_id          BIGINT REFERENCES sif_case(sif_id), -- 근거 SIF 사례
    law_ref         VARCHAR(200),                    -- 근거 법령 조항
    display_order   INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_item_scope ON checklist_item (target_industry, target_region, is_active);

-- 응답 세션 (한 번의 체크리스트 작성 = 1행)
CREATE TABLE checklist_submission (
    submission_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workplace_id   BIGINT NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    submitted_by   BIGINT REFERENCES app_user(user_id) ON DELETE SET NULL,
    total_items    INT NOT NULL DEFAULT 0,
    answered_items INT NOT NULL DEFAULT 0,           -- 완료율 측정 (UX 검증 지표)
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sub_wp ON checklist_submission (workplace_id, submitted_at DESC);

CREATE TABLE checklist_response (
    submission_id  BIGINT NOT NULL REFERENCES checklist_submission(submission_id) ON DELETE CASCADE,
    item_id        BIGINT NOT NULL REFERENCES checklist_item(item_id),
    answer         answer_t NOT NULL,
    note           TEXT,
    PRIMARY KEY (submission_id, item_id)
);


-- ============================================================
-- 4. 위험도 진단 결과 + XAI
-- ============================================================
CREATE TABLE risk_assessment (
    assessment_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workplace_id      BIGINT NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    submission_id     BIGINT UNIQUE REFERENCES checklist_submission(submission_id) ON DELETE SET NULL,
    method            assess_method_t NOT NULL,      -- 콜드스타트 여부 구분
    risk_score        NUMERIC(6,3) NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_grade        risk_grade_t  NOT NULL,
    baseline_score    NUMERIC(6,3),                  -- 업종 평균 대비 비교용 (벤치마크)
    confidence_low    NUMERIC(6,3),                  -- 95% 신뢰구간 하한 (통계 검증)
    confidence_high   NUMERIC(6,3),
    model_version     VARCHAR(30) NOT NULL,          -- 재현성: 어느 모델이 낸 값인지
    top_accident_type VARCHAR(40),                   -- 예측 주요 재해유형
    raw_features      JSONB,                         -- 모델 입력 피처 스냅샷 (재현/디버깅)
    assessed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 시계열 이력 조회(월별 추이 차트)의 주 인덱스
CREATE INDEX idx_assess_wp_time ON risk_assessment (workplace_id, assessed_at DESC);
CREATE INDEX idx_assess_grade   ON risk_assessment (risk_grade);
COMMENT ON TABLE risk_assessment IS
  '진단 1건 = 1행. 별도 이력 테이블 없이 이 테이블의 시계열 조회로 월별 추이 제공';

-- SHAP 기여도: 진단 1건 : N개 피처
CREATE TABLE risk_factor_contribution (
    assessment_id  BIGINT NOT NULL REFERENCES risk_assessment(assessment_id) ON DELETE CASCADE,
    feature_name   VARCHAR(100) NOT NULL,
    feature_label  VARCHAR(200),                     -- 사용자 친화 문구 ("방호장치 미설치")
    feature_value  VARCHAR(100),
    shap_value     NUMERIC(10,6) NOT NULL,           -- 부호 유지 (+위험증가 / -감소)
    abs_rank       INT NOT NULL,                     -- |SHAP| 기준 순위
    item_id        BIGINT REFERENCES checklist_item(item_id),  -- 개선 액션 연결
    PRIMARY KEY (assessment_id, feature_name)
);
CREATE INDEX idx_shap_rank ON risk_factor_contribution (assessment_id, abs_rank);


-- ============================================================
-- 5. PDF 리포트
-- ============================================================
CREATE TABLE report (
    report_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id  BIGINT NOT NULL REFERENCES risk_assessment(assessment_id) ON DELETE CASCADE,
    workplace_id   BIGINT NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    status         report_status_t NOT NULL DEFAULT 'PENDING',
    file_path      VARCHAR(500),                     -- 스토리지 경로 (BLOB 저장 지양)
    file_size      INT,
    checksum       VARCHAR(64),                      -- 증빙 무결성 (노동청 제출 대비)
    generated_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_wp ON report (workplace_id, created_at DESC);


-- ============================================================
-- 6. 법령 RAG 챗봇
-- ============================================================
CREATE TABLE law_article (
    article_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    law_name       VARCHAR(200) NOT NULL,            -- 산업안전보건법, 중대재해처벌법 ...
    article_no     VARCHAR(50)  NOT NULL,            -- 제38조
    clause_no      VARCHAR(50),                      -- 제1항
    title          VARCHAR(300),
    content        TEXT NOT NULL,
    effective_date DATE,
    source_url     VARCHAR(500),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (law_name, article_no, clause_no)
);
CREATE INDEX idx_law_content_trgm ON law_article USING gin (content gin_trgm_ops);

-- RAG 청크 원문 보관.
-- 임베딩 벡터 자체는 FAISS 인덱스에 저장하고, 여기서는 그 인덱스 위치만 참조한다.
-- FAISS 검색 결과(정수 ID) -> faiss_idx 로 조회 -> 원문/출처 복원 하는 흐름.
CREATE TABLE law_chunk (
    chunk_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id   BIGINT NOT NULL REFERENCES law_article(article_id) ON DELETE CASCADE,
    chunk_index  INT  NOT NULL,
    content      TEXT NOT NULL,
    faiss_idx    BIGINT UNIQUE,                      -- FAISS 인덱스 내 vector id
    embed_model  VARCHAR(50),                        -- 예: text-embedding-3-small
    indexed_at   TIMESTAMPTZ,                        -- NULL = 아직 임베딩 안 됨
    UNIQUE (article_id, chunk_index)
);
CREATE INDEX idx_law_chunk_pending ON law_chunk (indexed_at) WHERE indexed_at IS NULL;
COMMENT ON COLUMN law_chunk.faiss_idx IS
  'FAISS 인덱스 재생성 시 이 값도 함께 갱신해야 함. 동기화 책임은 ML 서버에 있음';

CREATE TABLE chat_session (
    session_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    workplace_id  BIGINT REFERENCES workplace(workplace_id) ON DELETE SET NULL,
    title         VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_sess_user ON chat_session (user_id, created_at DESC);

CREATE TABLE chat_message (
    message_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     UUID NOT NULL REFERENCES chat_session(session_id) ON DELETE CASCADE,
    role           chat_role_t NOT NULL,
    content        TEXT NOT NULL,
    -- Hallucination 방어: 답변 근거를 반드시 남긴다
    cited_articles BIGINT[],                          -- law_article.article_id 배열
    cited_sif      BIGINT[],                          -- sif_case.sif_id 배열
    model_name     VARCHAR(50),
    token_usage    INT,
    latency_ms     INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_msg_sess ON chat_message (session_id, created_at);
COMMENT ON COLUMN chat_message.cited_articles IS
  '답변 근거 조항. 출처 없는 답변 방지 및 사후 검증용';


-- ============================================================
-- 7. 유사 재해사례 검색 이력
-- ============================================================
CREATE TABLE similar_case_result (
    assessment_id  BIGINT NOT NULL REFERENCES risk_assessment(assessment_id) ON DELETE CASCADE,
    sif_id         BIGINT NOT NULL REFERENCES sif_case(sif_id),
    similarity     NUMERIC(6,4) NOT NULL,
    rank_no        INT NOT NULL CHECK (rank_no BETWEEN 1 AND 5),
    PRIMARY KEY (assessment_id, sif_id)
);


-- ============================================================
-- 8. 맞춤 채용 추천 (위험요인 → NCS → 워크넷)
-- ============================================================
CREATE TABLE ncs_code (
    ncs_code     VARCHAR(20) PRIMARY KEY,           -- 워크넷 직무데이터사전
    ncs_name     VARCHAR(200) NOT NULL,
    major_cat    VARCHAR(100),
    middle_cat   VARCHAR(100),
    minor_cat    VARCHAR(100),
    description  TEXT
);

-- 위험요인(체크리스트 문항 / 재해유형) ↔ NCS 매핑
CREATE TABLE risk_ncs_mapping (
    mapping_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id        BIGINT REFERENCES checklist_item(item_id) ON DELETE CASCADE,
    accident_type  VARCHAR(40),                     -- item_id 없이 재해유형만으로 매핑 가능
    ncs_code       VARCHAR(20) NOT NULL REFERENCES ncs_code(ncs_code),
    relevance      NUMERIC(4,3) NOT NULL DEFAULT 1.0,
    CHECK (item_id IS NOT NULL OR accident_type IS NOT NULL)
);
CREATE INDEX idx_rnm_item ON risk_ncs_mapping (item_id);
CREATE INDEX idx_rnm_type ON risk_ncs_mapping (accident_type);

-- 워크넷 API 수집 채용공고 캐시
CREATE TABLE job_posting (
    posting_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worknet_id     VARCHAR(50) NOT NULL UNIQUE,     -- 외부 원본 ID (중복 수집 방지)
    title          VARCHAR(300) NOT NULL,
    company_name   VARCHAR(200),
    ncs_code       VARCHAR(20) REFERENCES ncs_code(ncs_code),
    region         VARCHAR(20),
    salary_text    VARCHAR(100),
    career_type    VARCHAR(50),
    detail_url     VARCHAR(500),
    posted_at      DATE,
    closing_at     DATE,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_ncs_region ON job_posting (ncs_code, region, closing_at);

CREATE TABLE job_recommendation (
    assessment_id  BIGINT NOT NULL REFERENCES risk_assessment(assessment_id) ON DELETE CASCADE,
    posting_id     BIGINT NOT NULL REFERENCES job_posting(posting_id) ON DELETE CASCADE,
    match_score    NUMERIC(5,3) NOT NULL,
    reason         VARCHAR(300),                    -- "추락 위험 상위 → 건설안전 NCS 매칭"
    rank_no        INT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (assessment_id, posting_id)
);


-- ============================================================
-- 9. 권장 기능
-- ============================================================
-- 9-1. 지원금 안내 (부산·경남 안전시설 개선)
CREATE TABLE subsidy (
    subsidy_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(300) NOT NULL,
    agency          VARCHAR(100),                   -- KOSHA, 부산시, 경남도
    target_region   VARCHAR(20),
    target_industry VARCHAR(50),
    max_size_class  VARCHAR(20),                    -- 지원 대상 규모 상한
    support_amount  VARCHAR(100),
    summary         TEXT,
    apply_url       VARCHAR(500),
    apply_start     DATE,
    apply_end       DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_subsidy_scope ON subsidy (target_region, target_industry, is_active);

-- 9-2. 업종 벤치마크 (동종업종 평균 대비 비교) — 배치 집계 결과 캐시
CREATE TABLE industry_benchmark (
    industry      VARCHAR(50) NOT NULL,
    size_class    VARCHAR(20) NOT NULL,
    region        VARCHAR(20) NOT NULL,
    year          INT NOT NULL,
    avg_injury_rate NUMERIC(6,3),
    avg_risk_score  NUMERIC(6,3),                   -- 서비스 내부 진단 평균
    sample_count    INT,
    percentile_25   NUMERIC(6,3),
    percentile_75   NUMERIC(6,3),
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (industry, size_class, region, year)
);

-- 9-3. 날씨 위험 알림
CREATE TABLE weather_alert (
    alert_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workplace_id  BIGINT NOT NULL REFERENCES workplace(workplace_id) ON DELETE CASCADE,
    alert_date    DATE NOT NULL,
    alert_type    VARCHAR(50) NOT NULL,             -- 강풍/폭염/한파/강우
    severity      risk_grade_t NOT NULL,
    message       VARCHAR(500),
    related_risk  VARCHAR(200),                     -- "고소작업 중단 권고"
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workplace_id, alert_date, alert_type)
);

-- 9-4. 감사 로그 (증빙 서비스이므로 조작 이력 추적 필요)
CREATE TABLE audit_log (
    log_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT REFERENCES app_user(user_id) ON DELETE SET NULL,
    action       VARCHAR(50) NOT NULL,
    entity_type  VARCHAR(50),
    entity_id    BIGINT,
    detail       JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_time ON audit_log (created_at DESC);


-- ============================================================
-- 10. updated_at 자동 갱신 트리거
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_updated  BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_wp_updated    BEFORE UPDATE ON workplace
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_law_updated   BEFORE UPDATE ON law_article
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- 11. 조회 편의 뷰
-- ============================================================
-- 사업장별 최신 진단 결과
CREATE VIEW v_latest_assessment AS
SELECT DISTINCT ON (workplace_id)
       workplace_id, assessment_id, risk_score, risk_grade,
       method, model_version, assessed_at
FROM   risk_assessment
ORDER  BY workplace_id, assessed_at DESC;

-- 월별 위험도 추이 (시계열 차트용)
CREATE VIEW v_monthly_risk_trend AS
SELECT workplace_id,
       date_trunc('month', assessed_at)::date AS month,
       AVG(risk_score)   AS avg_score,
       MAX(risk_score)   AS max_score,
       COUNT(*)          AS assess_count
FROM   risk_assessment
GROUP  BY workplace_id, date_trunc('month', assessed_at);

-- 사업장 + 업종 벤치마크 비교
CREATE VIEW v_workplace_benchmark AS
SELECT w.workplace_id, w.name, w.industry, w.size_class, w.region,
       la.risk_score, la.risk_grade,
       b.avg_risk_score  AS peer_avg_score,
       la.risk_score - b.avg_risk_score AS gap
FROM   workplace w
JOIN   v_latest_assessment la ON la.workplace_id = w.workplace_id
LEFT   JOIN industry_benchmark b
       ON b.industry = w.industry
      AND b.size_class = w.size_class
      AND b.region = w.region
      AND b.year = EXTRACT(YEAR FROM CURRENT_DATE)::int;
