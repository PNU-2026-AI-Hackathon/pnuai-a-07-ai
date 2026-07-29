-- SCHEMA_10_ADMIN.SQL
-- 사고 후 행정절차 + 법률대응 로드맵
-- 선행: SCHEMA_3(서비스), law_article 적재. DBeaver Alt+X, 재실행 안전.

CREATE TABLE IF NOT EXISTS admin_procedure (
    procedure_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proc_code        VARCHAR(30) UNIQUE NOT NULL,
    category         VARCHAR(20) NOT NULL,          -- 보고 / 현장대응 / 신청 / 법률대응
    title            VARCHAR(100) NOT NULL,
    trigger_condition VARCHAR(60) NOT NULL,         -- 발동 조건
    is_critical_only BOOLEAN NOT NULL DEFAULT FALSE,-- 중대재해 전용 여부
    action_summary   TEXT NOT NULL,                 -- 사업주가 할 일 한 줄
    deadline_text    VARCHAR(50),                   -- '지체 없이', '발생일부터 1개월 이내'
    deadline_days    INT,                           -- 0=즉시, 30=1개월, NULL=기한없음 (정렬·알림용)
    agency           VARCHAR(50),                   -- 담당 기관
    form_name        VARCHAR(60),
    form_url         VARCHAR(300),
    legal_basis      VARCHAR(120),                  -- 근거 법령 표시용
    ref_law_name     VARCHAR(60),                   -- law_article 조인용
    ref_article_no   VARCHAR(20),
    penalty          VARCHAR(100),                  -- 미이행 시
    priority         INT NOT NULL,                  -- 1=최우선(즉시)
    disclaimer       TEXT,                          -- 법률대응 항목 면책 문구
    is_active        BOOLEAN NOT NULL DEFAULT TRUE
);

TRUNCATE admin_procedure RESTART IDENTITY;

INSERT INTO admin_procedure
 (proc_code, category, title, trigger_condition, is_critical_only, action_summary,
  deadline_text, deadline_days, agency, form_name, form_url,
  legal_basis, ref_law_name, ref_article_no, penalty, priority, disclaimer)
VALUES
-- ===== 중대재해 즉시 대응 (priority 1) =====
('ADM-STOP','현장대응','작업 중지·근로자 대피','중대재해',TRUE,
 '중대재해 발생 즉시 작업을 중지하고 근로자를 안전한 곳으로 대피시킨다.',
 '즉시',0,'-',NULL,NULL,
 '산업안전보건법 제54조','산업안전보건법','제54조',NULL,1,NULL),

('ADM-REPORT-SEVERE','보고','중대재해 발생 보고','중대재해',TRUE,
 '재해 개요·피해상황·조치사항을 관할 지방고용노동관서에 전화·팩스 등으로 즉시 보고한다.',
 '지체 없이',0,'관할 지방고용노동관서',NULL,NULL,
 '산업안전보건법 제54조 제2항 / 시행규칙 제67조','산업안전보건법 시행규칙','제67조',
 '미보고 시 과태료 3,000만원',1,NULL),

('ADM-PRESERVE','현장대응','사고 현장 보존','중대재해',TRUE,
 '원인조사가 끝나기 전까지 사고 현장을 훼손하거나 조사를 방해하지 않는다.',
 '조사 종료 시까지',0,'-',NULL,NULL,
 '산업안전보건법 제56조','산업안전보건법','제56조',NULL,1,NULL),

-- ===== 신고·제출 (priority 2) =====
('ADM-INVEST-FORM','보고','산업재해조사표 제출','사망 또는 3일 이상 휴업',FALSE,
 '산업재해조사표를 작성해 관할 지방고용노동관서에 제출한다.',
 '발생일부터 1개월 이내',30,'관할 지방고용노동관서','산업재해조사표',
 'https://www.moel.go.kr/policy/policydata/view.do?bbs_seq=20210600265',
 '산업안전보건법 제57조 제3항 / 시행규칙 제73조','산업안전보건법 시행규칙','제73조',
 '미제출 시 과태료 1,500만원 이하',2,NULL),

-- ===== 산재 보상 (priority 3) =====
('ADM-WC-CLAIM','신청','산재보험 요양급여 신청','재해 근로자 치료 필요',FALSE,
 '재해 근로자(또는 진료 의료기관 대행)가 요양급여신청서를 근로복지공단에 제출한다. 공단은 접수 7일 이내 결정. 사업주는 증명에 협조한다.',
 '치료 개시 후',NULL,'근로복지공단','요양급여신청서',
 'https://www.gov.kr/mw/AA020InfoCappView.do?HighCtgCD=A05007&CappBizCD=14900000261',
 '산업재해보상보험법 제41조',NULL,NULL,NULL,3,NULL),

('ADM-PREVENT-PLAN','현장대응','재발방지대책 수립','노동관서 요구 또는 중대재해',FALSE,
 '동종·유사 재해 재발을 막기 위한 개선대책을 수립하고, 노동관서 요구 시 제출한다.',
 '노동관서 지정 기한',NULL,'관할 지방고용노동관서',NULL,NULL,
 '산업안전보건법 제57조','산업안전보건법','제57조',NULL,3,NULL),

-- ===== 법률 대응 (priority 2, 면책 포함) =====
('ADM-LEGAL-DEFENSE','법률대응','수사·처벌 대응 준비','중대재해',TRUE,
 '안전·보건 조치를 이행했음을 입증할 증빙자료(점검일지·교육기록·위험성평가·본 서비스 진단 리포트 등)를 확보하고, 노무사·변호사 등 전문가 상담을 받는다.',
 '수사 착수 시',NULL,'-',NULL,NULL,
 '중대재해 처벌 등에 관한 법률 / 산업안전보건법','중대재해 처벌 등에 관한 법률','제4조',NULL,2,
 '본 안내는 일반 정보이며 법률 자문이 아닙니다. 구체적 사안은 반드시 노무사·변호사 등 전문가와 상담하시기 바랍니다.');

-- law_article 조인 뷰: 근거 조문의 실제 원문을 함께 제공
CREATE OR REPLACE VIEW v_admin_with_law AS
SELECT ap.proc_code, ap.category, ap.title, ap.trigger_condition,
       ap.is_critical_only, ap.action_summary, ap.deadline_text, ap.deadline_days,
       ap.agency, ap.form_name, ap.form_url, ap.legal_basis, ap.penalty,
       ap.priority, ap.disclaimer,
       la.title AS 근거조문제목, left(la.content, 200) AS 근거조문요약
FROM   admin_procedure ap
LEFT   JOIN LATERAL (
    SELECT title, content FROM law_article l
    WHERE l.law_name = ap.ref_law_name AND l.article_no = ap.ref_article_no
    ORDER BY l.clause_no NULLS FIRST LIMIT 1
) la ON TRUE
WHERE  ap.is_active
ORDER  BY ap.priority, ap.deadline_days NULLS LAST;

-- ============================================================
-- 검증
-- ============================================================
-- 절차 수 (7)
SELECT count(*) AS 절차수 FROM admin_procedure;

-- 중대재해 상황 로드맵 (우선순위·기한순) — "지금 당장" 부터
SELECT priority AS 순위, title AS 할일, deadline_text AS 기한,
       agency AS 기관, penalty AS 미이행시
FROM   admin_procedure
WHERE  is_active AND (is_critical_only = TRUE OR trigger_condition LIKE '%휴업%')
ORDER  BY priority, deadline_days NULLS LAST;

-- 근거 조문이 law_article 에 실제로 연결되는지 (found=근거요약 존재)
SELECT proc_code, legal_basis, (근거조문요약 IS NOT NULL) AS 조문연결
FROM   v_admin_with_law
WHERE  legal_basis IS NOT NULL
ORDER  BY proc_code;
