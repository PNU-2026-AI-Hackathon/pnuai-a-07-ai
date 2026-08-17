#!/bin/bash
# PostgreSQL 컨테이너가 처음 만들어질 때 한 번 실행된다.
# (docker-entrypoint-initdb.d 는 데이터 디렉터리가 비어 있을 때만 동작한다)
#
# 적재 순서가 중요하다. 스키마 스크립트끼리 선행 관계가 있어서 순서가 틀리면
# 함수가 만들어져도 호출 시점에 깨진다. 특히 checklist 계열은
#   SCHEMA_9 (컬럼 추가) -> 16a (컬럼 추가) -> INSERT -> 16b (정렬)
# 순서를 지켜야 한다.
set -e

DUMP_DIR=/sql/dump
SCHEMA_DIR=/sql/schema
DATA_DIR=/sql/data

run() {
    if [ -f "$1" ]; then
        echo ">> 적재: $(basename "$1")"
        psql -v ON_ERROR_STOP=0 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "$1"
    else
        echo ">> 건너뜀(파일 없음): $1"
    fi
}

echo "=== 1. 확장 ==="
# pgcrypto: chat_session.session_id 의 gen_random_uuid()
# pg_trgm : 법령 검색(/api/laws/search) 의 유사도 연산
psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;"

echo "=== 2. 기초 덤프 (sif_case·accident_case·coldstart_baseline 등 원천 데이터) ==="
# 덤프가 먼저다. SCHEMA_3 부터는 이 테이블들을 참조하므로 순서를 바꾸면 줄줄이 깨진다.
# 용량이 커서 git 에 올리지 않으니 database/dump/ 에 직접 두어야 한다.
if ls "$DUMP_DIR"/*.sql >/dev/null 2>&1; then
    for f in "$DUMP_DIR"/*.sql; do run "$f"; done
else
    echo "!! 덤프가 없습니다. database/dump/README.md 를 참고하세요."
    echo "   덤프 없이는 sif_case 등 원천 테이블이 없어 이후 스키마가 대부분 실패하고,"
    echo "   백엔드는 Hibernate 스키마 검증에서 기동에 실패합니다."
fi

echo "=== 3. 서비스 스키마 ==="
run "$SCHEMA_DIR/SCHEMA_3_service.sql"    # app_user, workplace, checklist_*, risk_assessment, report ...
run "$SCHEMA_DIR/SCHEMA_4_codemaster.sql" # code_industry, code_size_class, code_region, code_accident_type
run "$SCHEMA_DIR/SCHEMA_5_benchmark.sql"
run "$SCHEMA_DIR/SCHEMA_6_coldstart.sql"  # coldstart_baseline, fn_coldstart_score
run "$SCHEMA_DIR/SCHEMA_8_apicontract.sql"

echo "=== 4. 스키마 확장 ==="
run "$SCHEMA_DIR/SCHEMA_9_checklist_v2.sql"        # work_type, evidence_cases 컬럼
run "$SCHEMA_DIR/SCHEMA_15_predict.sql"            # accident_type_dist, fn_predict_accidents
run "$SCHEMA_DIR/SCHEMA_16a_checklist_sif_pre.sql" # law_ref, description 컬럼 + NOT NULL 해제

echo "=== 5. 체크리스트 문항 적재 ==="
run "$DATA_DIR/checklist_item_insert.sql"          # law_article + SIF 문항 835건

echo "=== 6. 후처리 및 함수 ==="
run "$SCHEMA_DIR/SCHEMA_16b_checklist_sif_post.sql" # 컬럼 의미 정렬, 구코드 제거
run "$SCHEMA_DIR/SCHEMA_17_lawbasis.sql"            # fn_accident_law_basis, fn_diagnosis_law_basis
run "$SCHEMA_DIR/SCHEMA_18_prevention_guide.sql"    # fn_prevention_guide
run "$SCHEMA_DIR/SCHEMA_19_ml_features.sql"
run "$SCHEMA_DIR/SCHEMA_20_hybrid_enum.sql"
run "$SCHEMA_DIR/SCHEMA_21_fix_submission_id.sql"   # fn_coldstart_assess 가 submission_id 채움
run "$SCHEMA_DIR/SCHEMA_22_fix_assess_columns.sql"  # 21 이 쓰는 컬럼 추가 (21 없이 실행하면 진단 실패)
run "$SCHEMA_DIR/SCHEMA_25_diagnosis_flow.sql"      # 현장 세부정보 저장 컬럼

echo "=== 적재 완료 ==="
psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
SELECT 'checklist_item' AS t, count(*) FROM checklist_item
UNION ALL SELECT 'law_article', count(*) FROM law_article
UNION ALL SELECT 'sif_case', count(*) FROM sif_case
UNION ALL SELECT 'accident_case', count(*) FROM accident_case;" || true
