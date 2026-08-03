# SafeWork AI — ML 서버

LightGBM 기반 산재 위험유형 예측 + 법령/유사재해사례 임베딩 검색(RAG)을 담당하는 FastAPI 서버.
백엔드(Spring)가 이 서버를 호출해서 예측·검색 결과를 받아간다. 콜드스타트 위험점수(0~100점)는
DB의 `fn_coldstart_assess`가 담당하며 이 서버는 관여하지 않는다 (아래 "아키텍처" 참고).

## 아키텍처

```
프론트 → 백엔드(Spring) → ① DB의 fn_coldstart_assess(workplace_id) 직접 호출 (통계 기반 위험점수)
                        → ② ML서버(FastAPI, 여기) — LightGBM 위험유형 예측 (머신러닝 기반)
                             데이터팀이 학습한 모델(predict.py)을 그대로 import해서 사용
                             + SHAP으로 예측 근거 첨부
                        → 백엔드가 ①②를 합쳐서 risk_assessment에 저장
```

**2026-07-29 변경**: 원래 `/predict/risk`가 콜드스타트 점수(risk_score 등)까지 계산했는데,
백엔드가 이미 DB의 `fn_coldstart_assess(workplace_id)`를 직접 호출하고 있어서 같은 공식이
두 군데(SQL 함수 + 여기 파이썬 복제본)에 따로 존재하는 상태였다. 실제로 체크리스트가
20→835문항으로 바뀔 때 파이썬 복제본만 구버전에 멈춰있는 일이 생겨서, 백엔드와 합의 후
콜드스타트 계산을 이 서버에서 완전히 제거했다 — 채점 공식은 이제 DB 한 곳에만 있다.
`/predict/risk`는 순수하게 LightGBM 예측만 담당한다.

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
  "region": "부산"
}
```

```json
{
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
| `gender` / `age_group` / `work_period` | 선택. 안 보내면 기본값 사용 (아래 "알려진 제약사항" 참고) |
| `top_risks[].shap_value` | 해당 클래스에 대한 SHAP 기여도 합. **확률과 단조적으로 비례하지 않음** (margin/log-odds 공간 값) |

체크리스트/위험점수(`risk_score` 등)는 이 API에 없다 — 백엔드가 DB의 `fn_coldstart_assess`를
직접 호출해서 처리한다. 체크리스트 835문항 목록도 백엔드가 `v_ref_checklist` 뷰로 직접
제공하므로, 여기엔 체크리스트 관련 엔드포인트가 없다.

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

### 🟢 아키텍처 정리 완료 (2026-07-29)
- **채점 로직 이중화 해소** — `/predict/risk`에서 콜드스타트 위험점수 계산을 완전히 제거했다. 백엔드가 이미 `fn_coldstart_assess(workplace_id)`를 직접 호출 중이라 백엔드 쪽 변경 없이 바로 반영됨. 체크리스트(20→835문항 등)는 이제 전적으로 DB/백엔드 소관 — mlserver는 더 이상 체크리스트 데이터를 갖고 있지 않다.
- **risk_score NULL 이슈, 팩트체크됨** — DB의 2026-07-28 공지는 "NONE 매칭 시 NULL 허용"이라고 했지만, 백엔드가 실제로 확인해보니 **아직 DB에 반영 안 됨**: `risk_assessment.risk_score`/`risk_grade`는 여전히 `NOT NULL` 제약이고, `fn_coldstart_score`도 NONE 매칭이면 그냥 0으로 계산한다. 게다가 현재 4개 활성 업종은 전부 베이스라인 데이터가 있어서(132~160건씩) NONE 매칭 자체가 실질적으로 안 일어남. DB의 `ALTER TABLE ... DROP NOT NULL` + 함수 수정이 먼저 있어야 실제로 의미 있는 변경이다 — 지금은 mlserver 응답에서 이 필드 자체가 빠졌으니 나와는 무관해졌지만, 백엔드/DB 간에는 아직 미해결.
- **method enum 태깅 자연 해소** — `/predict/risk`가 이제 순수 LightGBM 예측만 반환하니, 백엔드가 이 결과를 저장할 때 `method='LIGHTGBM'`으로 태깅하면 된다 (콜드스타트는 별도로 `method='COLDSTART'`).

### 🔴 지금 막혀있는 것
- **sub_industry 58→44 매핑** — DB가 `code_sub_industry`에 실데이터 기준 58개 원본값 스켈레톤을 만들어놨고, 내 44개 정규화 규칙을 요청함. `app/data/sub_industry_mapping_82to44.csv`로 82개(KOSHA 공식 전체 목록) 기준 매핑을 만들어서 넘길 준비는 해뒀는데, **DB의 58개가 이 82개와 문자열이 정확히 일치하는지 확인 안 됨** (전에 업종에서 가운뎃점 표기 차이 났던 것과 같은 문제가 또 있을 수 있음). DB의 58개 원본 리스트를 받아서 대조 필요.

### 🟡 다른 파트 확인 필요 (내가 답 못 함)
- **construction_amount 학습 출처** — DB가 물어본 것: `accident_case`에 없는 컬럼인데 모델이 어느 데이터로 학습했는지. 데이터모델링(학습) 담당자 확인 필요.
- **recommended_actions 필드 추가 여부** — DB가 "스키마 락 전에 지금 정하는 게 낫다"고 권고한 전체 안건. **A안(전용 컬럼 안 만들고 백엔드가 필요 시 LLM으로 실시간 요약)으로 팀 결정됨 (2026-07-28)** — mlserver 쪽 변경 없음.

### 아직 남은 것
- **업종 4개(DB) vs 6개(모델)** — 여전히 미정. 매핑 테이블(`services/mappings.py`)은 6개+소규모 5개 전부 대응 가능하게 만들어둬서, 스코프가 바뀌어도 코드 수정은 필요 없음.
- **대표 근로자 프로필 기본값** — `gender`/`age_group`/`work_period` 기본값(`남`/`40세~44세`/`10년 이상`) 임시 사용 중. 프론트가 수집할지 기획 확인 필요.
- **PG 라이브 연결 전** — `law_chunk`/`law_article`(2,602/2,547건), `sif_case`(6,032건)은 전부 2026-07-23 SQL 덤프 스냅샷을 씀. PG 접속정보 받으면 각 서비스의 `_load_*()`만 라이브 쿼리로 교체하면 됨.

### ✅ RAG 1·2단계 완료 (2026-07-28)
- **법령 임베딩 검색** (`POST /rag/search-law`) + **유사 재해사례 검색** (`POST /analyze/cases`) 둘 다 동작. OpenAI 대신 로컬 임베딩 모델 사용(무료, API 키 불필요, `jhgan/ko-sroberta-multitask`).
- Windows에서 torch 로딩에 VC++ 재배포 패키지의 `vcruntime140_1.dll`이 추가로 필요했던 이슈 해결됨 (위 "로컬 실행" 참고).
- 챗봇(질문→LLM 생성 답변)은 아직 없음 — 지금은 "관련 조문/사례 검색"까지만. 답변 생성 단계는 별도 LLM이 필요해서 이후 과제.
