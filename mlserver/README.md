# SafeWork AI — ML 서버

산재 위험도 예측 + 콜드스타트 점수 계산을 담당하는 FastAPI 서버. 백엔드(Spring)가 이 서버를
호출해서 위험도 진단 결과를 받아간다.

## 아키텍처

```
프론트 → 백엔드(Spring) → ML서버(FastAPI, 여기)
                              ├─ ① 콜드스타트 위험점수 (통계 기반)
                              │    DB팀 SQL 함수 fn_coldstart_score()의 공식을
                              │    Python으로 재현 (베이스라인 백분위 60점 + 체크리스트 40점)
                              │
                              └─ ② LightGBM 위험유형 예측 (머신러닝 기반)
                                   데이터팀이 학습한 모델(predict.py)을 그대로 import해서 사용
                                   + SHAP으로 예측 근거 첨부
```

`predict.py`, `kosha_encodings.py`, 학습된 모델 파일(`models/*.txt`)은 **복사하지 않고**
`데이터모델링/ML모델/` 경로를 그대로 참조(`sys.path` 등록 후 import)한다. 데이터팀이 인코딩
로직을 바꿔도 mlserver는 코드 수정 없이 최신 버전을 그대로 쓴다.

## API

### `POST /predict/risk`

```json
{
  "industry": "제조업",
  "sub_industry": "기계기구·금속·비금속광물제품제조업",
  "size_class": "10~19인",
  "region": "부산",
  "checklist_scores": { "MFG-LOTO-MAINT": "NO", "MFG-CONVEYOR": "YES" }
}
```

```json
{
  "risk_score": 48.42,
  "risk_grade": "MEDIUM",
  "base_component": 26.6,
  "checklist_component": 21.82,
  "match_level": "EXACT",
  "top_risks": [
    { "type": "끼임", "probability": 0.2425, "shap_value": 0.067 }
  ],
  "severity_prediction": [
    { "label": "6개월 이상", "probability": 0.3337 }
  ],
  "model_version": "lightgbm-2026.07"
}
```

| 필드 | 설명 |
|---|---|
| `industry` / `size_class` / `region` | DB `code_industry` / `code_size_class` / `code_region` 값 그대로 |
| `sub_industry` | KOSHA 종업종 원본 문자열 (또는 정규화된 44개 카테고리 중 하나) |
| `checklist_scores` | `item_code → "YES"(안전조치완료) / "NO"(미비, 감점대상) / "NA"(해당없음, 채점제외)`. 유효 코드는 `GET /predict/checklist-items` 참고. **모르는 item_code가 하나라도 섞이면 400** (조용히 무시하면 위험도가 실제보다 낮게 나올 수 있어서 명시적으로 거부) |
| `gender` / `age_group` / `work_period` | 선택. 안 보내면 기본값 사용 (아래 "알려진 제약사항" 참고) |
| `risk_score`/`risk_grade`/`base_component` | 콜드스타트 공식으로 계산 (모델 예측 아님). 베이스라인 매칭이 전혀 안 되면(`match_level: "NONE"`) 셋 다 `null` |
| `checklist_component` | `(미비 항목 가중치 합 / 응답 항목 가중치 합) × 40`. NA는 분모·분자 모두 제외. 문항 수와 무관한 비율 기반 (2026-07-28 DB 공지로 교체된 공식) |
| `top_risks[].shap_value` | 해당 클래스에 대한 SHAP 기여도 합. **확률과 단조적으로 비례하지 않음** (margin/log-odds 공간 값) |

### `GET /predict/checklist-items`
체크리스트 문항 목록. `item_code`, `question`, `risk_weight`, `is_critical`, `law_ref` 포함.

⚠️ **지금 이 목록은 구버전(20문항)이다.** 2026-07-28 DB 공지로 실제 체크리스트는 SIF/LLM 생성
835문항(건설 `CON-0001`~`CON-0450` 450개 + 제조 `MFG-0001`~`MFG-0385` 385개, `work_type` 68종으로
필터링)으로 전면 교체됐고 구코드는 전부 무효화됐다. 새 데이터(`v_ref_checklist`) 받기 전까지는
이 20개만 유효하게 인식되고, 실제 835문항 코드로 호출하면 400으로 거부된다.

### `POST /rag/search-law`

법령 조문 임베딩 검색. `law_chunk`(2,602건, SQL 덤프 스냅샷) + `law_article`을 OpenAI 대신
**로컬 임베딩 모델**(`jhgan/ko-sroberta-multitask`, sentence-transformers)로 벡터화해서
FAISS로 검색한다. API 키/카드 필요 없음 — 2026-07-28 팀 결정.

```json
{ "query": "컨베이어 끼임 예방", "top_k": 3 }
```
```json
[
  { "chunk_id": 191, "article_id": 191, "law_name": "산업안전보건기준에 관한 규칙",
    "article_no": "제191조", "title": "이탈 등의 방지",
    "content": "...", "score": 0.747 }
]
```

첫 호출 시 인덱스가 없으면 자동으로 구축한다(모델 다운로드 포함 몇 분 소요) — 미리 만들어두려면
`python scripts/build_law_index.py` 실행. 검색 품질은 한국어 키워드에는 잘 반응하지만 "LOTO" 같은
영어 약어가 섞이면 정확도가 떨어짐(확인됨, score 0.45대로 하락) — 참고만 하고 아직 개선 여지 있음.

### `POST /analyze/cases`

유사 재해사례 검색. `sif_case`(6,032건, SQL 덤프 스냅샷)를 같은 로컬 임베딩+FAISS 파이프라인으로
검색한다. `sif_case.industry_div`가 실데이터상 "건설업"/"제조업등" 두 값뿐이라, 다른 업종으로
요청해도 "제조업등"으로 폴백된다.

```json
{ "industry": "제조업", "sub_industry": "금속가공", "top_n": 3 }
```
```json
{
  "top_keywords": ["중량물(금형)", "사출성형기"],
  "similar_cases": [
    { "sif_id": 308, "summary": "2019년 4월 금형공장에서...",
      "countermeasure": "...", "score": 0.727 }
  ]
}
```

첫 호출 시 인덱스가 없으면 자동 구축(`python scripts/build_sif_index.py`로 미리 만들어둘 수 있음).
"금속가공" → 금형/사출성형기 사고, "건축공사업" → 굴착기/붕괴 사고로 실제 관련도 높은 사례가 잘
검색됨(확인함).

### `GET /health`
`{"status": "ok", "models_loaded": true}` — 모델 30개 중 24개(predict.py가 실제 쓰는 태스크만) 로딩 여부 확인용.

## 로컬 실행

```powershell
cd mlserver
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Windows에서는 **Visual C++ Redistributable x64**가 없으면 LightGBM/torch 네이티브 라이브러리가 로드되지
않는다. `lib_lightgbm.dll` 에러만 나면 `vcomp140.dll`/`msvcp140.dll`만 있어도 되는데, torch(임베딩용)는
`vcruntime140.dll`/`vcruntime140_1.dll`까지 추가로 필요해서 설치했는데도 torch만 안 될 수 있다 — 이 경우
[Microsoft 공식 페이지](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170)에서
다시 받아서 "복구(Repair)"로 재설치.

실행 후 `http://localhost:8000/docs`에서 Swagger UI로 바로 테스트 가능.

## 알려진 제약사항 / 열린 질문

### 🔴 지금 막혀있는 것 (835문항 데이터 필요)
- **체크리스트 20→835문항 교체 (2026-07-28 DB 공지)** — `app/data/checklist_items.py`가 아직 구 20문항 스냅샷이라, 실제 새 코드(`CON-0001`~, `MFG-0001`~)로 오는 요청은 전부 400으로 막힌다. `v_ref_checklist`(835행) + `v_ref_work_type`(68행) 데이터를 새로 export 받아야 실사용 가능. 점수 계산 공식 자체(비율 기반)는 이미 반영 완료.
- **sub_industry 58→44 매핑** — DB가 `code_sub_industry`에 실데이터 기준 58개 원본값 스켈레톤을 만들어놨고, 내 44개 정규화 규칙을 요청함. `app/data/sub_industry_mapping_82to44.csv`로 82개(KOSHA 공식 전체 목록) 기준 매핑을 만들어서 넘길 준비는 해뒀는데, **DB의 58개가 이 82개와 문자열이 정확히 일치하는지 확인 안 됨** (전에 업종에서 가운뎃점 표기 차이 났던 것과 같은 문제가 또 있을 수 있음). DB의 58개 원본 리스트를 받아서 대조 필요.

### 🟡 다른 파트 확인 필요 (내가 답 못 함)
- **construction_amount 학습 출처** — DB가 물어본 것: `accident_case`에 없는 컬럼인데 모델이 어느 데이터로 학습했는지. 데이터모델링(학습) 담당자 확인 필요.
- **method enum 태깅** — DB가 `LIGHTGBM`을 추가했는데, `/predict/risk` 응답은 콜드스타트 점수(SQL 공식)+LightGBM 예측을 한 번에 묶어서 주기 때문에 순수 `LIGHTGBM`이 아니라 `HYBRID`로 저장하는 게 맞아 보임 — 백엔드랑 확인 필요.
- **recommended_actions 필드 추가 여부** — DB가 "스키마 락 전에 지금 정하는 게 낫다"고 권고한 전체 안건. 넣기로 하면 체크리스트 NO 항목의 `law_ref`/`evidence_cases`나 SHAP top feature를 근거로 만들 수 있음.

### ✅ 이번에 해결/반영된 것
- **체크리스트 NA 지원** — `checklist_scores`가 `true/false`에서 `"YES"/"NO"/"NA"`로 변경됨. NA는 비율 계산 분모·분자에서 완전히 제외.
- **베이스라인 매칭 없으면 risk_score/grade가 null** — 기존엔 항상 숫자를 냈는데, DB 요구사항대로 `match_level: "NONE"`일 때 `risk_score`/`risk_grade`/`base_component` 전부 `null` 반환하도록 수정.
- **size_class 매핑 재확인됨** — DB가 `code_size_class.model_size_class`에 20~29·30~49인 → 20~49인 매핑을 넣었다고 확인해줌. 내 `services/mappings.py`의 매핑과 정확히 일치.
- **`recommended_actions` 전용 필드 안 만들기로 팀 결정 (A안, 2026-07-28)** — DB에 이미 있는 `fn_prevention_guide`/`fn_accident_advice`/`predicted_accident_types`를 백엔드가 필요 시 LLM으로 실시간 요약. mlserver 쪽 변경 없음.

### 아직 남은 것
- **업종 4개(DB) vs 6개(모델)** — 여전히 미정. 매핑 테이블은 6개+소규모 5개 전부 대응 가능하게 만들어둬서, 스코프가 바뀌어도 코드 수정은 필요 없음.
- **대표 근로자 프로필 기본값** — `gender`/`age_group`/`work_period` 기본값(`남`/`40세~44세`/`10년 이상`) 임시 사용 중. 프론트가 수집할지 기획 확인 필요.
- **PG 라이브 연결 전** — `coldstart_baseline`(609행), `law_chunk`/`law_article`(2,602/2,547건), `sif_case`(6,032건)은 전부 2026-07-23 SQL 덤프 스냅샷을 씀. PG 접속정보 받으면 각 서비스의 `_load_*()`만 라이브 쿼리로 교체하면 됨.

### ✅ RAG 1·2단계 완료 (2026-07-28)
- **법령 임베딩 검색** (`POST /rag/search-law`) + **유사 재해사례 검색** (`POST /analyze/cases`) 둘 다 동작. OpenAI 대신 로컬 임베딩 모델 사용(무료, API 키 불필요, `jhgan/ko-sroberta-multitask`).
- Windows에서 torch 로딩에 VC++ 재배포 패키지의 `vcruntime140_1.dll`이 추가로 필요했던 이슈 해결됨 (위 "로컬 실행" 참고).
- 챗봇(질문→LLM 생성 답변)은 아직 없음 — 지금은 "관련 조문/사례 검색"까지만. 답변 생성 단계는 별도 LLM이 필요해서 이후 과제.
