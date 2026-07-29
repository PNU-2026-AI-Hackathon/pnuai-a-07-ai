# DB 변경 공지 (데이터베이스 파트 → 전체)

작성: 강주호 (DB) · 대상: 백엔드·ML·프론트
요약: **체크리스트가 20 → 835문항으로 전면 교체(item_code 전부 변경)**, 진단 결과 저장 스키마 확장, 참조 도메인 뷰 추가. 각 파트 확인 필요.

---

## ⚠️ 가장 중요 — 체크리스트 item_code 전면 변경

기존 수기 20문항(`MFG-LOTO-MAINT` 등)을 **SIF→LLM 생성 835문항으로 교체**했습니다.

- 건설 450 + 제조 385 = **835문항**
- item_code 형식: **`CON-0001`~`CON-0450`, `MFG-0001`~`MFG-0385`**
- 기존 20개 코드는 **전부 삭제됨** (더 이상 유효하지 않음)
- 새 차원 추가: **`work_type`(작업)** — 굴착작업·거푸집 등 68종
- 각 문항이 실제 재해개요에 근거 (`evidence_cases` JSONB)

**→ item_code를 참조하는 모든 곳(ML 입력, 프론트 문항 렌더링)을 새 목록으로 교체해야 합니다.**
전체 목록은 `SELECT * FROM v_ref_checklist;` 로 확인.

---

## 파트별 할 일

### ML 파트

1. **`checklist_scores` 입력 키를 새 item_code 835개로 교체.** `GET /predict/checklist-items` 응답을 `v_ref_checklist` 기준으로 갱신.
2. **진단 응답 저장 컬럼 준비됨** — `RiskPredictResponse`의 아래 필드가 `risk_assessment`에 그대로 저장 가능:
   - `risk_score / risk_grade` — **NULL 허용으로 변경** (NONE 매칭 시)
   - `base_component / checklist_component / match_level` — 실제 컬럼 추가됨
   - `top_risks[] → predicted_accident_types` (JSONB), `severity_prediction[] → predicted_severity` (JSONB)
   - `method` enum에 **`LIGHTGBM` 추가**
3. **아직 ML이 줘야 할 것 (계약 완성 위해):**
   - **sub_industry 58→44 정규화 매핑** — DB `code_sub_industry`에 58종 스켈레톤 만들어 뒀음. ML의 44종 규칙 주면 `model_category` 채움.
   - **`construction_amount` 출처** — accident_case에 없는 컬럼. 어느 데이터로 학습했는지 확인 필요.
   - **`size_class` 매핑 반영** — DB 10종, 모델 9종. `code_size_class.model_size_class`에 `20~29인·30~49인 → 20~49인` 매핑 넣어둠. predict 전 이 값 사용.

### 백엔드 파트

1. **진단 결과 저장** — ML 응답을 `risk_assessment`에 INSERT (컬럼 매핑은 위 ML 항목 참고, `SCHEMA_8_APICONTRACT.SQL` 하단 예시 있음).
2. **참조 도메인 GET 엔드포인트** — 프론트가 유효값을 하드코딩하지 않게, 아래 뷰를 API로 노출:
   - `v_ref_industry` (업종), `v_ref_size_class` (규모, model_size_class 포함)
   - `v_ref_region` (지역), `v_ref_checklist` (체크리스트 835), `v_ref_work_type` (작업 목록)
3. **`workplace` 등록 시** 업종·규모·지역은 FK로 마스터 값만 허용됨 (잘못된 값은 DB가 거부).

### 프론트 파트

1. **체크리스트 화면** — 835문항 전부 묻지 말 것. 사업장이 하는 **작업(work_type)을 먼저 선택** → 그 작업 문항만 표시. (`v_ref_work_type`로 작업 목록, 선택된 작업으로 `v_ref_checklist` 필터)
2. **입력값** — 업종/규모/지역은 `v_ref_*` 뷰가 주는 값 그대로 사용 (임의 문자열 금지).
3. **답변 형식** — 문항당 예(안전조치 완료)/아니오(미비). '아니오'가 위험 가감점 대상.

---

## 진단 스코어링 방식 (참고)

콜드스타트 위험점수 = **베이스(0~60) + 체크리스트(0~40)**
- 베이스: 업종×규모×지역 중대재해비율의 백분위
- 체크리스트: **(미비 항목 가중치합 / 응답 가중치합) × 40** — 비율 기반이라 문항 수와 무관하게 안정. 중대문항은 가중치 ×2.
- 실측 검증: 제조업·5인미만·부산 + 문항 20개 응답 → base 32.81 + checklist 12.97 = **risk_score 45.78 (MEDIUM)**

---

## 확인/논의 필요 (팀 공통)

| # | 안건 | 관련 |
|---|---|---|
| 1 | sub_industry 58→44 매핑표 제공 | ML → DB |
| 2 | construction_amount 학습 출처 | ML |
| 3 | 체크리스트 NA(해당 설비 없음) 처리 — API는 bool이라 표현 불가 | ML·백엔드 |
| 4 | 발표 지적 5·6 반영 — 응답에 우선순위 조치(recommended_actions) 필드 추가할지 | 전체 |

> 4번은 스키마 락 걸기 전이 추가 비용이 가장 쌉니다. 지금 정하는 걸 권장.

---

## 적용된 스크립트 (실행 완료)

- `SCHEMA_8_APICONTRACT.SQL` — risk_assessment 확장, method enum, 참조 뷰, size_class/sub_industry 매핑
- `SCHEMA_9_CHECKLIST_V2.SQL` — checklist_item 확장, 비율 스코어링
- `load_checklist.py` — 835문항 적재 완료
