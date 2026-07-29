-- VERIFY_CHECKLIST_V2.SQL — 835문항 적재·비율 스코어링 검증
-- 실행: SCHEMA_9 → load_checklist.py 적재 후 Alt+X

-- 1. 문항 수 (건설 450 / 제조 385)
SELECT target_industry, count(*) AS 문항수,
       count(*) FILTER (WHERE is_critical) AS 중대문항
FROM checklist_item GROUP BY target_industry;

-- 2. 작업별 문항 수
SELECT * FROM v_ref_work_type;

-- 3. 근거(evidence) 적재 확인
SELECT item_code, category, left(question,30) AS 질문,
       jsonb_array_length(evidence_cases) AS 근거사례수
FROM checklist_item
WHERE evidence_cases IS NOT NULL
ORDER BY item_code LIMIT 5;

-- 4. 특정 작업 필터 (프런트가 "굴착 작업" 사업장에 보여줄 문항)
SELECT item_code, category, question, risk_weight
FROM checklist_item
WHERE target_industry='건설업' AND work_type='굴착 작업' AND is_active
ORDER BY display_order;

-- 5. 비율 스코어링 데모 (BEGIN~ROLLBACK)
--    제조업 문항 20개 응답: 가중치>=6 → NO, else YES
--    기대: checklist_component ≈ 12.97, base ≈ 32.81, risk_score ≈ 45.78
--    fn 결과의 checklist_component 와 아래 수동값이 같아야 함
BEGIN;

INSERT INTO app_user(email,password_hash,name)
VALUES ('_demo_v2@test','x','데모V2');

INSERT INTO workplace(owner_user_id,name,industry,size_class,region)
SELECT user_id,'데모공장','제조업','5인 미만','부산'
FROM app_user WHERE email='_demo_v2@test';

INSERT INTO checklist_submission(workplace_id, submitted_by, total_items, answered_items)
SELECT w.workplace_id, u.user_id, 20, 20
FROM workplace w JOIN app_user u ON u.user_id=w.owner_user_id
WHERE u.email='_demo_v2@test';

INSERT INTO checklist_response(submission_id, item_id, answer)
SELECT s.submission_id, ci.item_id,
       (CASE WHEN ci.risk_weight >= 6 THEN 'NO' ELSE 'YES' END)::answer_t
FROM checklist_submission s
JOIN workplace w ON w.workplace_id = s.workplace_id
JOIN app_user u ON u.user_id = w.owner_user_id
JOIN LATERAL (
    SELECT item_id, risk_weight FROM checklist_item
    WHERE target_industry='제조업' AND is_active
    ORDER BY item_code LIMIT 20
) ci ON TRUE
WHERE u.email='_demo_v2@test';

SELECT * FROM fn_coldstart_score(
    (SELECT w.workplace_id FROM workplace w
     JOIN app_user u ON u.user_id=w.owner_user_id
     WHERE u.email='_demo_v2@test'));

SELECT round( sum(ci.risk_weight*CASE WHEN ci.is_critical THEN 2 ELSE 1 END)
                FILTER (WHERE cr.answer='NO')
              / NULLIF(sum(ci.risk_weight*CASE WHEN ci.is_critical THEN 2 ELSE 1 END),0)
              * 40, 2) AS 수동_checklist_component
FROM checklist_response cr
JOIN checklist_item ci ON ci.item_id=cr.item_id
JOIN checklist_submission s ON s.submission_id=cr.submission_id
JOIN workplace w ON w.workplace_id=s.workplace_id
JOIN app_user u ON u.user_id=w.owner_user_id
WHERE u.email='_demo_v2@test';

ROLLBACK;
