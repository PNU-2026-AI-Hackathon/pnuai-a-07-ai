-- ============================================================
-- SafeWork AI - SCHEMA_7_CHECKLIST.SQL
-- SIF 기반 체크리스트 문항 시드 (건설 10 + 제조 10 = 20개)
-- ============================================================
-- 근거
--   적재된 sif_case 6,032건에서 업종별 빈발 고위험 패턴을 추출해 문항화.
--   - 건설업: 재해종류×기인물 (추락/비계·사다리·개구부 …) 상위
--   - 제조업: 고위험상황 (비정형작업·컨베이어·지게차 …) 상위
--   각 문항은 대표 SIF 사례(sif_id)에 연결 → "이 문항의 근거가 무엇이냐"에 답 가능.
--
-- 문항 규칙
--   question 은 'YES=조치됨(안전) / NO=미비(위험)' 로 답하도록 서술.
--   콜드스타트 스코어(fn_coldstart_score)는 'NO' 응답의 risk_weight 를 가산하고,
--   is_critical 항목은 ×2 가중한다.
--
-- 실행: DBeaver(ai_safework) Alt+X. 재실행 안전(ON CONFLICT 갱신).
-- 선행: SCHEMA_3(checklist_item), sif_case 적재, SCHEMA_6(fn_coldstart_score, 데모용).
--
-- ※ law_ref 주의: 아래 조문 번호 중 널리 알려진 것(제42조 추락의 방지,
--   제43조 개구부 방호)은 확실하나, 일부 세부 조문 번호는 추정치다.
--   맨 끝 'law_ref 교차검증' 쿼리로 law_article 과 대조해 확인할 것.
-- ============================================================

INSERT INTO checklist_item
   (item_code, category, question, description,
    target_industry, risk_weight, is_critical, sif_id, law_ref, display_order)
VALUES
-- ===== 건설업 (추락 중심) =====
 ('CON-FALL-SCAF','추락',
  '비계 작업발판에 안전난간(상부·중간)을 설치했습니까?',
  '작업발판 위 거푸집 조립 등 작업 시 충분한 강도의 안전난간, 곤란하면 안전대 부착설비를 설치해야 합니다.',
  '건설업', 3.0, TRUE, 2787, '산업안전보건기준에 관한 규칙 제42조(추락의 방지)', 10),

 ('CON-FALL-ROOF','추락',
  '노후·채광판 지붕 작업 시 안전덮개(발판)와 추락방호망을 설치했습니까?',
  '강도가 약한 지붕재(선라이트·슬레이트) 위 작업은 지붕 전용 안전덮개와 방호망, 안전대가 필요합니다.',
  '건설업', 3.0, TRUE, 4262, '산업안전보건기준에 관한 규칙 제45조(지붕 위에서의 위험 방지)', 11),

 ('CON-FALL-LADDER','추락',
  '사다리 대신 작업발판·고소작업대를 사용하거나 사다리 안전수칙을 준수합니까?',
  '추락위험 작업은 비계·고소작업대를 우선하고, 불가피한 사다리는 안전수칙을 지켜야 합니다.',
  '건설업', 2.5, FALSE, 3959, '산업안전보건기준에 관한 규칙', 12),

 ('CON-FALL-STEEL','추락',
  '철골·보 상부 작업 시 작업발판·고소작업대와 안전대를 사용합니까?',
  '철골보 상부 등 추락위험 장소는 작업발판·고소작업대를 설치하고 안전대를 착용해야 합니다.',
  '건설업', 2.5, FALSE, 3336, '산업안전보건기준에 관한 규칙 제42조(추락의 방지)', 13),

 ('CON-FALL-EDGE','추락',
  '슬래브·작업발판 단부에 안전난간 또는 안전방망을 설치했습니까?',
  '근로자가 떨어질 위험이 있는 단부에는 안전난간, 곤란하면 안전방망을 설치합니다.',
  '건설업', 3.0, TRUE, 2655, '산업안전보건기준에 관한 규칙 제42조(추락의 방지)', 14),

 ('CON-FALL-OPEN','추락',
  '바닥·벽 개구부에 덮개 또는 안전난간을 견고하게 설치했습니까?',
  '추락위험 개구부에는 충분한 강도의 덮개나 안전난간을 튼튼하게 설치해야 합니다.',
  '건설업', 3.0, TRUE, 2574, '산업안전보건기준에 관한 규칙 제43조(개구부 등의 방호 조치)', 15),

 ('CON-FALL-MEWP','추락',
  '고소작업대 작업 시 안전난간과 안전대 부착설비를 갖추고 착용합니까?',
  '고소작업대 단부 추락위험 구간에 안전난간을 설치하고 안전대를 착용합니다.',
  '건설업', 2.5, FALSE, 3235, '산업안전보건기준에 관한 규칙 제42조(추락의 방지)', 16),

 ('CON-FALL-SUSP','추락',
  '달비계 로프를 2개 이상 견고한 고정점에 결속하고 작업 전 점검합니까?',
  '달비계 로프는 2개 이상 고정점에 풀리지 않게 결속하고, 작업 전 로프·작업대 손상을 점검합니다.',
  '건설업', 2.5, FALSE, 3481, '산업안전보건기준에 관한 규칙', 17),

 ('CON-FALL-MOBILE','추락',
  '이동식비계 작업발판·단부 안전난간·바퀴 고정(아웃트리거)을 했습니까?',
  '이동식비계는 발판을 밀실하게 깔고 단부 안전난간과 바퀴 고정·아웃트리거로 전도를 방지합니다.',
  '건설업', 2.0, FALSE, 3961, '산업안전보건기준에 관한 규칙', 18),

 ('CON-COLLAPSE-SOIL','붕괴',
  '굴착작업 시 굴착면 기울기 기준을 준수하고 흙막이·사전조사를 했습니까?',
  '지반 사전조사 후 굴착면 기울기 기준을 지키고 흙막이 지보공으로 붕괴를 예방합니다.',
  '건설업', 2.5, TRUE, 2580, '산업안전보건기준에 관한 규칙', 19),

-- ===== 제조업 (비정형작업·설비 중심) =====
 ('MFG-LOTO-MAINT','끼임',
  '정비·수리·교체 등 비정형 작업 시 전원 차단 후 잠금·표지(LOTO)를 합니까?',
  '설비 정비 시 운전을 정지하고 기동장치에 잠금·표지를 하여 예상치 못한 기동을 막습니다.',
  '제조업', 3.0, TRUE, 15, '산업안전보건기준에 관한 규칙 제92조(정비 등의 작업 시의 운전정지)', 20),

 ('MFG-CLEAN-STOP','끼임',
  '이물질 제거·청소 시 설비를 정지한 후 작업합니까?',
  '가동 중 청소·이물질 제거는 끼임 위험이 크므로 반드시 정지 후 작업합니다.',
  '제조업', 2.5, TRUE, 27, '산업안전보건기준에 관한 규칙 제92조(정비 등의 작업 시의 운전정지)', 21),

 ('MFG-INSPECT-GUARD','끼임',
  '설비 점검 시 안전장치를 유지하고 비상정지장치가 정상 작동합니까?',
  '점검 중 안전장치를 해제하지 않고, 비상정지장치의 정상 작동을 확인합니다.',
  '제조업', 2.5, FALSE, 2, '산업안전보건기준에 관한 규칙', 22),

 ('MFG-CHEM-MSDS','화학물질',
  '위험물질 취급 장소에 MSDS 비치·환기설비·보호구를 갖췄습니까?',
  '위험물질 취급 장소는 물질안전보건자료(MSDS)를 비치하고 환기와 보호구를 제공합니다.',
  '제조업', 2.0, FALSE, 9, '산업안전보건기준에 관한 규칙', 23),

 ('MFG-CRANE','부딪힘',
  '크레인 사용 시 방호장치(과부하방지 등)와 신호수를 운용합니까?',
  '크레인은 과부하방지장치 등 방호장치를 갖추고 신호수를 배치해 협착·낙하를 예방합니다.',
  '제조업', 2.5, TRUE, 12, '산업안전보건기준에 관한 규칙 제134조(방호장치의 조정)', 24),

 ('MFG-FORKLIFT','부딪힘',
  '지게차에 후진경보·헤드가드·좌석안전띠를 갖추고 유도자를 배치합니까?',
  '지게차는 후진경보기·헤드가드·좌석안전띠를 갖추고, 작업구역에 유도자를 배치합니다.',
  '제조업', 2.5, TRUE, 11, '산업안전보건기준에 관한 규칙 제179조(전조등 등)', 25),

 ('MFG-CONVEYOR','끼임',
  '컨베이어에 방호울과 비상정지장치를 설치했습니까?',
  '컨베이어 벨트·풀리 사이 끼임을 막는 방호울과 비상정지장치를 설치합니다.',
  '제조업', 2.5, TRUE, 2, '산업안전보건기준에 관한 규칙 제191조(비상정지장치)', 26),

 ('MFG-WELD-FIRE','화재폭발',
  '용접·절단 작업 시 화기감시자·소화기를 배치하고 가연물을 제거합니까?',
  '화기작업은 주변 가연물을 제거하고 화기감시자와 소화기를 배치합니다.',
  '제조업', 2.0, FALSE, 99, '산업안전보건기준에 관한 규칙 제241조(화재위험작업 시의 준수사항)', 27),

 ('MFG-ROBOT','끼임',
  '산업용로봇 작업영역에 방책(울)·인터록을 설치했습니까?',
  '산업용로봇 가동범위에 방책을 설치하고, 출입 시 정지되는 인터록을 둡니다.',
  '제조업', 2.5, TRUE, 262, '산업안전보건기준에 관한 규칙 제223조(운전 중 위험 방지)', 28),

 ('MFG-PASSAGE','전도',
  '통로·작업장 바닥을 정리정돈하고 안전통로를 확보했습니까?',
  '통로에 장애물·유해물이 없도록 정리정돈하고 안전통로를 확보해 전도·부딪힘을 예방합니다.',
  '제조업', 1.5, FALSE, 10, '산업안전보건기준에 관한 규칙 제22조(통로의 설치)', 29)

ON CONFLICT (item_code) DO UPDATE SET
    category      = EXCLUDED.category,
    question      = EXCLUDED.question,
    description   = EXCLUDED.description,
    target_industry = EXCLUDED.target_industry,
    risk_weight   = EXCLUDED.risk_weight,
    is_critical   = EXCLUDED.is_critical,
    sif_id        = EXCLUDED.sif_id,
    law_ref       = EXCLUDED.law_ref,
    display_order = EXCLUDED.display_order;

-- ============================================================
-- 검증
-- ============================================================
-- 업종별·중대여부 문항 수
SELECT target_industry, count(*) AS 문항수,
       count(*) FILTER (WHERE is_critical) AS 중대문항
FROM checklist_item GROUP BY target_industry;

-- 각 문항이 실제 SIF 사례에 연결됐는지 (근거 확인)
SELECT ci.item_code, ci.category, left(ci.question,30) AS question,
       ci.risk_weight, ci.is_critical, s.sif_id IS NOT NULL AS sif연결
FROM checklist_item ci
LEFT JOIN sif_case s ON s.sif_id = ci.sif_id
ORDER BY ci.display_order;

-- law_ref 교차검증: law_ref 의 '제N조'가 실제 law_article 에 있는지 확인.
--   found=false 인 문항은 조문 번호를 수정해야 함(law_article 기준).
SELECT ci.item_code, ci.law_ref,
       substring(ci.law_ref from '제[0-9]+조(?:의[0-9]+)?') AS 추출조문,
       EXISTS (
         SELECT 1 FROM law_article la
         WHERE la.law_name = '산업안전보건기준에 관한 규칙'
           AND la.article_no = substring(ci.law_ref from '제[0-9]+조(?:의[0-9]+)?')
       ) AS found
FROM checklist_item ci
WHERE ci.law_ref ~ '제[0-9]+조'
ORDER BY found, ci.item_code;


-- ============================================================
-- 데모 : 체크리스트 응답이 콜드스타트 점수에 반영되는지 확인
--   제조업×5인미만×부산 사업장이 3개 위험문항에 'NO'(미비) 응답
--   기대: base≈32.8 + checklist(LOTO 3.0×2 + 컨베이어 2.5×2 + 통로 1.5×1 = 12.5)
--         ≈ 45.3 (MEDIUM)
-- ============================================================
BEGIN;

INSERT INTO app_user(email,password_hash,name)
VALUES ('_demo_chk@test','x','데모');

INSERT INTO workplace(owner_user_id,name,industry,size_class,region)
SELECT user_id,'데모공장','제조업','5인 미만','부산'
FROM app_user WHERE email='_demo_chk@test';

INSERT INTO checklist_submission(workplace_id, submitted_by, total_items, answered_items)
SELECT w.workplace_id, u.user_id, 3, 3
FROM workplace w JOIN app_user u ON u.user_id=w.owner_user_id
WHERE u.email='_demo_chk@test';

-- 3개 문항에 'NO'(미비) 응답
INSERT INTO checklist_response(submission_id, item_id, answer)
SELECT s.submission_id, ci.item_id, 'NO'::answer_t
FROM checklist_submission s
JOIN workplace w ON w.workplace_id=s.workplace_id
JOIN app_user u ON u.user_id=w.owner_user_id
JOIN checklist_item ci ON ci.item_code IN ('MFG-LOTO-MAINT','MFG-CONVEYOR','MFG-PASSAGE')
WHERE u.email='_demo_chk@test';

SELECT *
FROM fn_coldstart_score(
    (SELECT w.workplace_id FROM workplace w
     JOIN app_user u ON u.user_id=w.owner_user_id
     WHERE u.email='_demo_chk@test'));

ROLLBACK;   -- 데모 데이터 원복
