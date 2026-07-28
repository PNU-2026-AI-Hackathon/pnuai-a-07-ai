# 🗄️ Database — SafeWork AI

> 데이터베이스 & 데이터 전처리 파트 (담당: 강주호)
> PostgreSQL 18 · DB명 `ai_safework`

산재 위험도 진단 서비스의 **데이터 기반 전체**입니다. 학습 데이터 적재부터 위험도 산출
로직까지, 전부 실데이터로 동작하는 상태로 구축돼 있습니다.

---

## 📦 폴더 구성

```
database/
├── schema/          DDL·함수 (SQL, 실행 순서대로)
│   └── _archive/    대체된 구버전 (참고용, 실행하지 마세요)
├── scripts/         Python 파이프라인 (법령 수집·청킹·체크리스트 적재)
├── exports/         타 파트 전달용 (ML 체크리스트 목록, 업종 대조표)
├── data/            원본·중간 산출물 (checklist_filtered.json, 법령 raw XML)
└── docs/            변경 공지, 작업 보고서
```

> ⚠️ 학습 데이터 덤프 `ai_safework.sql`(64만 행, 약 100MB)은 용량이 커서 git 에
> 올리지 않았습니다. 공유 드라이브 / Git LFS 로 따로 받으세요.

---

## ⚙️ 처음부터 DB 세우는 순서

번호 순서대로 실행하면 됩니다. `.sql` 은 DBeaver(Alt+X), `.py` 는 명령창.

| # | 파일 | 내용 |
|---|---|---|
| 1 | `ai_safework.sql` (별도 보관) | 학습·참조 데이터 5테이블 (psql 로 적재) |
| 2 | `schema/SCHEMA_3_service.sql` | 서비스 32테이블 + 뷰 |
| 3 | `schema/SCHEMA_4_codemaster.sql` | 코드 마스터 + FK 12개 |
| 4 | `scripts/` fetch_laws → load_laws → build_chunks | 법령 수집·적재·청킹 |
| 5 | `schema/SCHEMA_5_benchmark.sql` | 업종 벤치마크 집계 |
| 6 | `schema/SCHEMA_6_coldstart.sql` | 콜드스타트 스코어링 함수 |
| 7 | `schema/SCHEMA_8_apicontract.sql` | ML 응답 저장 컬럼 + 참조 뷰 |
| 8 | `schema/SCHEMA_9_checklist_v2.sql` | 체크리스트 스키마 + 비율 스코어링 |
| 9 | `scripts/load_checklist.py` | 체크리스트 835문항 적재 |
| 10 | `schema/VERIFY_CHECKLIST_V2.sql` | 최종 검증 |

---

## 📊 주요 산출물

| 항목 | 규모 / 내용 |
|---|---|
| 학습 데이터 | accident_case 64만 + SIF·베이스라인·재해율·관서통계 (총 64.7만행) |
| 법령 데이터 | law_article 2,547 조문 + law_chunk (RAG 청킹) |
| 서비스 스키마 | 32 테이블 + 3 뷰 |
| 코드 마스터 | 업종4 · 규모10 · 지역16 · 재해유형24 + FK 12개 |
| 업종 벤치마크 | industry_benchmark 4,748행 |
| 진단 로직 | `fn_coldstart_score` (베이스 0~60 + 체크리스트 0~40, 비율 기반) |
| 체크리스트 | 835문항 (건설450·제조385, 작업68), 실제 재해개요 근거 |

---

## 🐍 Python 스크립트 실행

```
set PGHOST=localhost
set PGDATABASE=ai_safework
set PGUSER=postgres
set PGPASSWORD=본인비번
"C:\Users\사용자\AppData\Local\Programs\Python\Python313\python.exe" <스크립트>.py
```

> 📌 `load_laws.py`·`load_checklist.py` 는 기본적으로 스크립트와 같은 폴더의 JSON을
> 찾습니다. 폴더가 나뉘어 있으면 `--json ..\data\checklist_filtered.json` 처럼 경로를
> 지정하거나 JSON을 스크립트 옆에 두세요.

---

## 🔗 다른 파트 연동

- **ML**: `exports/checklist_export.csv` (835 item_code), `exports/sub_industry_db_58.csv` (업종 매핑 대조)
- **백엔드**: 진단 결과는 `risk_assessment` 에 저장, 참조 도메인은 `v_ref_*` 뷰
- 자세한 변경 내역은 `docs/DB_변경공지.md` 참고
