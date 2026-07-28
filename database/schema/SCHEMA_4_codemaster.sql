-- ============================================================
-- SafeWork AI - SCHEMA_4_CODEMASTER.SQL
-- 코드 마스터 실값 확정 + FK 고정
-- ============================================================
-- 목적
--   실제 적재된 학습 데이터(accident_case 64만, coldstart_baseline,
--   size_injury_rate)에서 추출한 '진짜' 코드값으로 code_* 마스터를 채우고,
--   참조/서비스 테이블에 FK 를 걸어 도메인을 고정한다.
--
--   ※ SCHEMA_2_PATCH.SQL 이 넣었던 시드값(영문 'BUSAN','5_UNDER' 등)은
--     실데이터(한글 '부산','5인 미만')와 불일치하므로 전부 교체한다.
--
-- 도메인 (실데이터에서 자동 추출, 오타 없음)
--   industry       4종  : 건설업 / 운수창고통신업 / 전기가스증기수도사업 / 제조업
--   size_class    10종  : 5인 미만 ~ 1,000인 이상
--   region        16종  : 전국 광역 (부산·경남 = 타깃)
--   accident_type 24종  : 감전 ~ 화학물질누출접촉 (accident_case 기준 상위집합)
--
-- 실행: DBeaver(ai_safework) 에서 이 파일 전체를 Alt+X.
--       재실행해도 안전한 멱등(idempotent) 스크립트.
-- 선행: SCHEMA_3 및 학습 데이터(accident_case 등)가 이미 존재해야 함.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. 마스터 테이블 보장 (SCHEMA_2_PATCH 로 이미 있으면 통과)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_industry (
    industry     VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_high_risk BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS code_size_class (
    size_class   VARCHAR(20) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    min_employee INT,
    max_employee INT,
    sort_order   INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS code_region (
    region       VARCHAR(20) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    is_target    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS code_accident_type (
    accident_type VARCHAR(40) PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL
);

-- ------------------------------------------------------------
-- 2. 기존 FK 제거 (재실행 대비 — 없으면 조용히 통과)
--    마스터를 TRUNCATE 하려면 인바운드 FK 가 없어야 한다.
-- ------------------------------------------------------------
ALTER TABLE accident_case      DROP CONSTRAINT IF EXISTS fk_ac_industry;
ALTER TABLE accident_case      DROP CONSTRAINT IF EXISTS fk_ac_size;
ALTER TABLE accident_case      DROP CONSTRAINT IF EXISTS fk_ac_region;
ALTER TABLE accident_case      DROP CONSTRAINT IF EXISTS fk_ac_atype;
ALTER TABLE coldstart_baseline DROP CONSTRAINT IF EXISTS fk_cb_industry;
ALTER TABLE coldstart_baseline DROP CONSTRAINT IF EXISTS fk_cb_size;
ALTER TABLE coldstart_baseline DROP CONSTRAINT IF EXISTS fk_cb_region;
ALTER TABLE coldstart_baseline DROP CONSTRAINT IF EXISTS fk_cb_atype;
ALTER TABLE size_injury_rate   DROP CONSTRAINT IF EXISTS fk_sir_size;
ALTER TABLE workplace          DROP CONSTRAINT IF EXISTS fk_wp_industry;
ALTER TABLE workplace          DROP CONSTRAINT IF EXISTS fk_wp_size;
ALTER TABLE workplace          DROP CONSTRAINT IF EXISTS fk_wp_region;

-- ------------------------------------------------------------
-- 3. 마스터 재적재 (잘못된 시드 포함 전부 교체)
-- ------------------------------------------------------------
TRUNCATE code_industry, code_size_class, code_region, code_accident_type;

-- code_industry
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('건설업','건설업',TRUE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('운수창고통신업','운수창고통신업',FALSE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('전기가스증기수도사업','전기가스증기수도사업',FALSE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('제조업','제조업',TRUE);

-- code_size_class
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('5인 미만','5인 미만',0,4,1);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('5~9인','5~9인',5,9,2);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('10~19인','10~19인',10,19,3);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('20~29인','20~29인',20,29,4);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('30~49인','30~49인',30,49,5);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('50~99인','50~99인',50,99,6);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('100~299인','100~299인',100,299,7);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('300~499인','300~499인',300,499,8);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('500~999인','500~999인',500,999,9);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('1,000인 이상','1,000인 이상',1000,NULL,10);

-- code_region  (부산·경남 = 타깃)
INSERT INTO code_region(region,display_name,is_target) VALUES ('강원','강원',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경기','경기',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경남','경남',TRUE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경북','경북',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('광주','광주',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('대구','대구',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('대전','대전',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('부산','부산',TRUE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('서울','서울',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('울산','울산',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('인천','인천',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('전남','전남',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('전북','전북',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('제주','제주',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('충남','충남',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('충북','충북',FALSE);

-- code_accident_type (accident_case 24종)
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('감전','감전');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('기타','기타');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('깔림.뒤집힘','깔림.뒤집힘');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('끼임','끼임');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('넘어짐','넘어짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('동물상해','동물상해');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('떨어짐','떨어짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('무너짐','무너짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('물체에맞음','물체에맞음');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('부딪힘','부딪힘');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('분류불능','분류불능');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('불균형및무리한동작','불균형및무리한동작');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('빠짐익사','빠짐익사');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('사업장내교통사고','사업장내교통사고');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('사업장외교통사고','사업장외교통사고');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('산소결핍','산소결핍');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('업무상질병','업무상질병');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('이상온도물체접촉','이상온도물체접촉');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('절단베임찔림','절단베임찔림');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('체육행사','체육행사');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('폭력행위','폭력행위');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('폭발파열','폭발파열');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('화재','화재');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('화학물질누출접촉','화학물질누출접촉');

-- ------------------------------------------------------------
-- 4. FK 고정
--    마스터가 실데이터의 상위집합이므로 검증 통과한다.
--    (하나라도 실패하면 마스터에 없는 값이 데이터에 있다는 뜻 → 즉시 확인)
-- ------------------------------------------------------------
-- 4-1. 참조 데이터 (학습 데이터의 무결성 확인 겸)
ALTER TABLE accident_case ADD CONSTRAINT fk_ac_industry
    FOREIGN KEY (industry)      REFERENCES code_industry(industry);
ALTER TABLE accident_case ADD CONSTRAINT fk_ac_size
    FOREIGN KEY (size_class)    REFERENCES code_size_class(size_class);
ALTER TABLE accident_case ADD CONSTRAINT fk_ac_region
    FOREIGN KEY (region)        REFERENCES code_region(region);
ALTER TABLE accident_case ADD CONSTRAINT fk_ac_atype
    FOREIGN KEY (accident_type) REFERENCES code_accident_type(accident_type);

ALTER TABLE coldstart_baseline ADD CONSTRAINT fk_cb_industry
    FOREIGN KEY (industry)          REFERENCES code_industry(industry);
ALTER TABLE coldstart_baseline ADD CONSTRAINT fk_cb_size
    FOREIGN KEY (size_class)        REFERENCES code_size_class(size_class);
ALTER TABLE coldstart_baseline ADD CONSTRAINT fk_cb_region
    FOREIGN KEY (region)            REFERENCES code_region(region);
ALTER TABLE coldstart_baseline ADD CONSTRAINT fk_cb_atype
    FOREIGN KEY (top_accident_type) REFERENCES code_accident_type(accident_type);

ALTER TABLE size_injury_rate ADD CONSTRAINT fk_sir_size
    FOREIGN KEY (size_class)    REFERENCES code_size_class(size_class);

-- 4-2. 서비스 데이터 (사용자 입력을 참조 도메인으로 강제 — 이게 핵심 목적)
--      workplace 는 사용자가 사업장을 등록하는 테이블. 여기서 업종/규모/지역을
--      마스터 값으로만 받게 강제하면, 콜드스타트 조인이 절대 실패하지 않는다.
ALTER TABLE workplace ADD CONSTRAINT fk_wp_industry
    FOREIGN KEY (industry)   REFERENCES code_industry(industry);
ALTER TABLE workplace ADD CONSTRAINT fk_wp_size
    FOREIGN KEY (size_class) REFERENCES code_size_class(size_class);
ALTER TABLE workplace ADD CONSTRAINT fk_wp_region
    FOREIGN KEY (region)     REFERENCES code_region(region);

COMMIT;

-- ============================================================
-- 검증 (COMMIT 후 실행 — 결과 확인용)
-- ============================================================
-- 마스터 건수: 4 / 10 / 16 / 24 여야 정상
SELECT 'industry' AS m, count(*) FROM code_industry
UNION ALL SELECT 'size_class', count(*) FROM code_size_class
UNION ALL SELECT 'region',     count(*) FROM code_region
UNION ALL SELECT 'accident_type', count(*) FROM code_accident_type;

-- FK 목록 확인 (12개여야 정상)
-- SELECT conname FROM pg_constraint WHERE contype='f' AND conname LIKE 'fk_%' ORDER BY conname;
