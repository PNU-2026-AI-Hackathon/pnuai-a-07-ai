-- SCHEMA_25_severe_criteria.sql
-- 중대재해 판단기준 구조화 (산업안전보건법 시행규칙 제3조)
-- 목적: 사고대처 화면에서 "직접 대조하세요" 대신 앱이 자동 판정·안내.
-- 선행: SCHEMA_3. DBeaver Alt+X. 재실행 안전.

CREATE TABLE IF NOT EXISTS severe_accident_criteria (
    criteria_id     INT PRIMARY KEY,
    label           VARCHAR(30)  NOT NULL,   -- 사망 / 중상 / 다수재해
    description      TEXT         NOT NULL,   -- 기준 원문
    legal_basis      VARCHAR(60)  NOT NULL,   -- 근거 법령 표시용
    ref_law_name    VARCHAR(60),             -- law_article 조인용
    ref_article_no  VARCHAR(20)
);

TRUNCATE severe_accident_criteria;
INSERT INTO severe_accident_criteria
 (criteria_id, label, description, legal_basis, ref_law_name, ref_article_no)
VALUES
 (1, '사망',     '사망자가 1명 이상 발생한 재해',
  '산업안전보건법 시행규칙 제3조', '산업안전보건법 시행규칙', '제3조'),
 (2, '중상',     '3개월 이상의 요양이 필요한 부상자가 동시에 2명 이상 발생한 재해',
  '산업안전보건법 시행규칙 제3조', '산업안전보건법 시행규칙', '제3조'),
 (3, '다수재해', '부상자 또는 직업성 질병자가 동시에 10명 이상 발생한 재해',
  '산업안전보건법 시행규칙 제3조', '산업안전보건법 시행규칙', '제3조');

-- 자동 판정: 피해 규모 3개 숫자를 넣으면 중대재해 여부 + 해당 기준 반환
CREATE OR REPLACE FUNCTION fn_check_severe(
    p_death              INT DEFAULT 0,   -- 사망자 수
    p_serious_injury     INT DEFAULT 0,   -- 3개월 이상 요양 부상자 수
    p_injury_or_disease  INT DEFAULT 0)   -- 부상자+직업성질병자 수(동시)
RETURNS TABLE(is_severe BOOLEAN, matched TEXT[])
LANGUAGE sql AS $$
    SELECT
        (COALESCE(p_death,0) >= 1
         OR COALESCE(p_serious_injury,0) >= 2
         OR COALESCE(p_injury_or_disease,0) >= 10) AS is_severe,
        array_remove(ARRAY[
            CASE WHEN COALESCE(p_death,0) >= 1            THEN '사망자 1명 이상' END,
            CASE WHEN COALESCE(p_serious_injury,0) >= 2  THEN '3개월 이상 부상자 2명 이상' END,
            CASE WHEN COALESCE(p_injury_or_disease,0) >= 10 THEN '부상·질병자 10명 이상' END
        ], NULL) AS matched;
$$;

COMMENT ON FUNCTION fn_check_severe(INT,INT,INT) IS
  '피해 규모로 중대재해 여부와 충족 기준을 판정 (산안법 시행규칙 제3조).';

-- 기준 + 근거 조문 원문 함께 보기
CREATE OR REPLACE VIEW v_severe_criteria AS
SELECT c.criteria_id, c.label, c.description, c.legal_basis,
       la.content AS 근거조문원문
FROM   severe_accident_criteria c
LEFT   JOIN LATERAL (
    SELECT content FROM law_article l
    WHERE l.law_name = c.ref_law_name AND l.article_no = c.ref_article_no
    LIMIT 1
) la ON TRUE
ORDER  BY c.criteria_id;

-- ============================================================
-- 데모
-- ============================================================
-- 기준 3가지
SELECT criteria_id AS 번호, label AS 구분, description AS 기준 FROM severe_accident_criteria;

-- 프레스 끼임 사망 1명 → 중대재해?
SELECT * FROM fn_check_severe(1, 0, 0);   -- is_severe=true, {사망자 1명 이상}

-- 경미(부상 1명) → 중대재해 아님
SELECT * FROM fn_check_severe(0, 1, 0);   -- is_severe=false
