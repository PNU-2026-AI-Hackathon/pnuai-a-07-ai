"""
학습전데이터/target-발생형태/*.csv
학습전데이터/target-질병종류/*.csv
→ output/ml_training_data_create_insert.sql 생성

⚠️ 주의: 전체 데이터 삽입 시 SQL 파일이 수백 MB가 될 수 있음
         DB 팀과 용량 협의 후 COPY 방식 또는 청크 삽입 권장
         테이블 구조만 필요하면 INSERT 없이 CREATE만 사용 가능
"""

import csv, os, sys, glob
sys.stdout.reconfigure(encoding='utf-8')

BASE       = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_BASE  = os.path.join(BASE, "학습전데이터")
OUT_PATH   = os.path.join(BASE, "DB개선", "output", "ml_training_data_create_insert.sql")

# INSERT 행 제한 (None = 전체, 숫자 = 샘플링 — 용량 이슈 시 조정)
MAX_ROWS_PER_FILE = None


def esc(v):
    if v is None or v == '':
        return "NULL"
    try:
        float(v)
        return str(v)
    except ValueError:
        return "'" + str(v).replace("'", "''") + "'"


def main():
    lines = [
        "-- ============================================================",
        "-- ml_training_data: ML 학습 전처리 완료 데이터 (인코딩 적용)",
        "-- 출처: 학습전데이터/target-발생형태/*.csv",
        "--       학습전데이터/target-질병종류/*.csv",
        "-- 생성일: " + __import__('datetime').date.today().isoformat(),
        "--",
        "-- ⚠️  용량 경고: 전체 삽입 시 대용량 SQL 파일 발생 가능",
        "--    DB 팀과 협의하여 COPY 명령 또는 스트리밍 삽입 방식 권장",
        "-- ============================================================",
        "",
        "-- 테이블 생성 (없을 때만)",
        "CREATE TABLE IF NOT EXISTS public.ml_training_data (",
        "    train_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,",
        "    dataset_type VARCHAR(20)  NOT NULL, -- 'accident'(발생형태) / 'disease'(질병종류)",
        "    industry     VARCHAR(50)  NOT NULL, -- 건설업·제조업·광업 등",
        "    year         INTEGER,               -- 통계기준년월 앞 4자리",
        "    features     JSONB        NOT NULL, -- 인코딩된 feature 컬럼 전체",
        "    target_enc   INTEGER      NOT NULL, -- 인코딩된 target 값",
        "    target_col   VARCHAR(50)  NOT NULL  -- 'accident_type' / 'disease_type'",
        ");",
        "",
        "CREATE INDEX IF NOT EXISTS idx_ml_train_industry",
        "    ON public.ml_training_data (industry, dataset_type);",
        "",
        "DELETE FROM public.ml_training_data;",
        "",
    ]

    total = 0

    for dataset_type, folder, target_col in [
        ("accident", "target-발생형태", "accident_type"),
        ("disease",  "target-질병종류",  "disease_type"),
    ]:
        pattern = os.path.join(DATA_BASE, folder, "*.csv")
        files = sorted(glob.glob(pattern))

        for fpath in files:
            industry = os.path.splitext(os.path.basename(fpath))[0]
            industry_clean = industry.replace("_", "/")

            with open(fpath, encoding='utf-8-sig') as f:
                reader = csv.DictReader(f)
                rows = list(reader)

            if MAX_ROWS_PER_FILE:
                rows = rows[:MAX_ROWS_PER_FILE]

            if not rows:
                continue

            # target 컬럼명 탐지 (발생형태_enc or 질병종류_enc)
            header = list(rows[0].keys())
            target_key = header[-2]  # 뒤에서 두번째가 target (마지막은 재해정도_enc)

            lines.append(f"-- [{dataset_type}] {industry_clean} ({len(rows)}행)")
            lines.append(
                "INSERT INTO public.ml_training_data "
                "(dataset_type, industry, year, features, target_enc, target_col) VALUES"
            )

            value_rows = []
            for r in rows:
                year_raw = r.get("통계기준년월", "")
                year = int(str(year_raw)[:4]) if year_raw else "NULL"

                # features: target 컬럼 제외한 나머지
                feat = {k: v for k, v in r.items()
                        if k != target_key and k != "통계기준년월" and k != "재해정도_enc"}
                feat_json = "'" + str(feat).replace("'", "''").replace('"', '\\"') + "'"
                # 실제론 json.dumps 사용
                import json as _json
                feat_json = "'" + _json.dumps(feat, ensure_ascii=False).replace("'", "''") + "'::jsonb"

                target_val = r.get(target_key, "NULL")
                target_enc = int(float(target_val)) if target_val not in ("", "NULL") else "NULL"

                value_rows.append(
                    f"    ({esc(dataset_type)}, {esc(industry_clean)}, {year}, "
                    f"{feat_json}, {target_enc}, {esc(target_col)})"
                )

            lines.append(",\n".join(value_rows) + ";")
            lines.append("")
            total += len(value_rows)

    lines.append(f"-- 총 {total}개 학습 데이터 행 삽입")

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding='utf-8') as f:
        f.write("\n".join(lines))

    print(f"완료: {total}행 → output/ml_training_data_create_insert.sql")
    size_mb = os.path.getsize(OUT_PATH) / 1024 / 1024
    print(f"파일 크기: {size_mb:.1f} MB")


if __name__ == "__main__":
    main()
