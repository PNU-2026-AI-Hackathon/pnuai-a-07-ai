# 🗄️ Database — SafeWork AI

> 데이터베이스 & 데이터 전처리 파트 (담당: 강주호)
> PostgreSQL 18 · DB명 `ai_safework`

산재 위험도 진단 서비스의 **데이터 기반 전체**입니다. 예방(사전 예측) → 진단(위험도 산출)
→ 사후 대응(행정·법률·정책 조언)까지, 전부 실데이터로 동작하는 상태로 구축돼 있습니다.

---

## 📦 폴더 구성

```
database/
├── schema/          DDL·함수 (SQL, 실행 순서대로)
│   └── _archive/    대체된 구버전 (참고용, 실행하지 마세요)
├── scripts/         Python 파이프라인 (법령·판례·정책 수집, 청킹, 체크리스트 적재)
├── exports/         타 파트 전달용 (ML 체크리스트 목록, 업종 대조표)
├── data/            원본·중간 산출물 (checklist_filtered.json, 법령 raw XML)
└── docs/            변경 공지, 작업 보고서
```

> ⚠️ 학습 데이터 덤프 `ai_safework.sql`(64만 행, 약 100MB)은 용량이 커서 git 에
> 올리지 않았습니다. 공유 드라이브 / Git LFS 로 따로 받으세요. (`.gitignore` 처리됨)

---

## ⚙️ 처음부터 DB 세우는 순서

번호 순서대로 실행하면 됩니다. `.sql` 은 DBeaver(Alt+X), `.py` 는 명령창.

| #  | 파일 | 내용 |
|----|------|------|
| 1  | `ai_safework.sql` (별도 보관) | 학습·참조 데이터 (psql 로 적재) |
| 2  | `schema/SCHEMA_3_service.sql` | 서비스 32테이블 + 뷰 |
| 3  | `schema/SCHEMA_4_codemaster.sql` | 코드 마스터 + FK 12개 |
| 4  | `scripts/` fetch_laws → load_laws → build_chunks | 법령 수집·적재·청킹 |
| 5  | `schema/SCHEMA_5_benchmark.sql` | 업종 벤치마크 집계 |
| 6  | `schema/SCHEMA_6_coldstart.sql` | 콜드스타트 스코어링 함수 |
| 7  | `schema/SCHEMA_8_apicontract.sql` | ML 응답 저장 컬럼 + 참조 뷰 |
| 8  | `schema/SCHEMA_9_checklist_v2.sql` | 체크리스트 스키마 + 비율 스코어링 |
| 9  | `scripts/load_checklist.py` | 체크리스트 835문항 적재 |
| 10 | `schema/SCHEMA_10_admin.sql` | 사후 행정절차 테이블·시드 + 법령 조인 뷰 |
| 11 | `schema/SCHEMA_11_policy.sql` + `scripts/fetch_policy.py` | 정책(공공서비스) 테이블·수집 |
| 12 | `schema/SCHEMA_12_precedent.sql` + `scripts/fetch_precedents.py` | 판례 테이블·수집 |
| 13 | `schema/SCHEMA_13_adminrule.sql` + `scripts/fetch_admrules.py` | 행정규칙 테이블·수집 |
| 14 | `schema/SCHEMA_14_roadmap.sql` | 사후 조언 함수 `fn_accident_advice` (행정·법률·정책) |
| 15 | `schema/SCHEMA_15_predict.sql` | 사전 예측 함수 `fn_predict_accidents` + 분포 테이블 |
| 16 | `schema/VERIFY_CHECKLIST_V2.sql` | 최종 검증 |

---

## 🔄 서비스 전체 루프

```
사업장 특성(업종·규모·지역) 입력
   → ① fn_predict_accidents  : 예상 재해유형 top-K 예측  (SCHEMA_15)
   → ② fn_coldstart_score    : 위험도 점수·등급 진단      (SCHEMA_6/9)
   → (사고 발생 시)
   → ③ fn_accident_advice    : 행정·법률·정책 조언         (SCHEMA_14)
```

---

## 📊 주요 산출물

| 항목 | 규모 / 내용 |
|------|-------------|
| 학습 데이터 | accident_case 64만 + SIF·베이스라인·재해율·관서통계 (총 64.7만행) |
| 법령 데이터 | law_article 2,547 조문 + law_chunk (RAG 청킹) |
| 서비스 스키마 | 32 테이블 + 뷰 |
| 코드 마스터 | 업종4 · 규모10 · 지역16 · 재해유형24 + FK 12개 |
| 업종 벤치마크 | industry_benchmark 4,748행 |
| 진단 로직 | `fn_coldstart_score` (베이스 0~60 + 체크리스트 0~40, 비율 기반) |
| 체크리스트 | 835문항 (건설450·제조385, 작업68), 실제 재해개요 근거 |
| 사후 행정 | admin_procedure 7절차 (기한·기관·처벌·법적근거) |
| 정책 | policy_service (사업주 대상 지원사업, gov24 수집) |
| 판례 | law_precedent (산재·중대재해 판례, 국가법령정보 수집) |
| 행정규칙 | law_admin_rule (고시·훈령·예규, 국가법령정보 수집) |
| 사후 조언 | `fn_accident_advice(업종, 재해유형, 중대여부)` → 행정·법률·정책 3계층 |
| 사전 예측 | `fn_predict_accidents(업종, 규모, 지역, top_k)` → 예상 재해유형 |

---

## 🐍 Python 스크립트 실행

```
set PGHOST=localhost
set PGDATABASE=ai_safework
set PGUSER=postgres
set PGPASSWORD=본인비번
"C:\Users\사용자\AppData\Local\Programs\Python\Python313\python.exe" <스크립트>.py
```

> 📌 **API 키는 코드에 넣지 않습니다.** 국가법령정보는 `--oc <본인이메일ID>`,
> gov24 는 `set GOV_KEY=<serviceKey>` 또는 `--key <키>` 로 실행 시 전달하세요.
> (커밋된 파일의 `YOUR_OC` 는 자리표시자입니다.)

> 📌 `load_laws.py`·`load_checklist.py` 는 기본적으로 스크립트와 같은 폴더의 JSON을
> 찾습니다. 폴더가 나뉘어 있으면 `--json ..\data\checklist_filtered.json` 처럼 경로를
> 지정하거나 JSON을 스크립트 옆에 두세요.

---

## 🔗 다른 파트 연동

- **ML**: `exports/checklist_export.csv` (835 item_code), `exports/sub_industry_db_58.csv` (업종 매핑 대조)
- **백엔드**: 진단 결과는 `risk_assessment` 에 저장, 참조 도메인은 `v_ref_*` 뷰
- 자세한 변경 내역은 `docs/DB_변경공지.md` 참고
