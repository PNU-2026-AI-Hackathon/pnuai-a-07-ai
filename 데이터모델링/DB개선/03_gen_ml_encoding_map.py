"""
ML모델/kosha_encodings.py → output/ml_encoding_map_create_insert.sql 생성

테이블 신규 생성 + 전체 인코딩 매핑 INSERT
원핫 인코딩(성별·지역·산업)은 컬럼 목록 주석으로 명시
"""

import os, sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "ML모델"))
import kosha_encodings as enc

BASE     = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_PATH = os.path.join(BASE, "DB개선", "output", "ml_encoding_map_create_insert.sql")


def esc(s):
    return "'" + str(s).replace("'", "''") + "'"


def build_rows(feature_name, mapping, enc_type):
    rows = []
    for original, encoded in mapping.items():
        if encoded == -1 or encoded == -2:
            continue  # drop 처리 값 제외
        rows.append(
            f"    ({esc(feature_name)}, {esc(str(original))}, {encoded}, {esc(enc_type)})"
        )
    return rows


def main():
    lines = [
        "-- ============================================================",
        "-- ml_encoding_map: ML 모델 인코딩 매핑 테이블 생성 + 데이터 삽입",
        "-- 출처: ML모델/kosha_encodings.py",
        "-- 생성일: " + __import__('datetime').date.today().isoformat(),
        "-- ============================================================",
        "",
        "-- 테이블 생성 (없을 때만)",
        "CREATE TABLE IF NOT EXISTS public.ml_encoding_map (",
        "    map_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,",
        "    feature_name VARCHAR(50)  NOT NULL,  -- 피처 이름 (규모, 연령, 발생형태 ...)",
        "    original_val VARCHAR(200) NOT NULL,  -- 원본 한글 값",
        "    encoded_val  INTEGER      NOT NULL,  -- 정수 인코딩 값",
        "    enc_type     VARCHAR(20)  NOT NULL   -- ordinal / label",
        ");",
        "",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_ml_enc_map_unique",
        "    ON public.ml_encoding_map (feature_name, original_val);",
        "",
        "-- 기존 데이터 삭제 후 재삽입 (idempotent)",
        "DELETE FROM public.ml_encoding_map;",
        "",
        "INSERT INTO public.ml_encoding_map (feature_name, original_val, encoded_val, enc_type)",
        "VALUES",
    ]

    all_rows = []
    all_rows += build_rows("규모",        enc.SIZE_ORDER,          "ordinal")
    all_rows += build_rows("연령",        enc.AGE_ORDER,           "ordinal")
    all_rows += build_rows("근무기간",    enc.WORK_PERIOD_ORDER,   "ordinal")
    all_rows += build_rows("건설공사금액", enc.CONST_AMT_ORDER,    "ordinal")
    all_rows += build_rows("재해정도",    enc.INJURY_ORDER,        "ordinal")
    all_rows += build_rows("발생형태",    enc.ACCIDENT_TYPE_MAP,   "label")
    all_rows += build_rows("질병종류",    enc.DISEASE_MAP,         "label")
    all_rows += build_rows("세부질병종류", enc.DISEASE_DETAIL_MAP,  "label")
    all_rows += build_rows("종업종",      enc.SUBJOB_MAP,          "label")

    lines.append(",\n".join(all_rows) + ";")
    lines += [
        "",
        "-- ============================================================",
        "-- 원핫 인코딩 컬럼 목록 (별도 매핑 테이블 없이 컬럼명 규칙으로 처리)",
        "-- 성별:  성별_남(1/0) — 성별_여 드롭(reference)",
        "-- 지역:  지역_강원·경기·경남·경북·광주·대구·대전·부산·서울·울산·인천·전남·전북·제주·충남 — 지역_충북 드롭",
        "-- 산업:  산업_농업·어업·임업·전기가스 — 산업_금융 드롭 (소규모통합 CSV 전용)",
        "-- ============================================================",
        "",
        f"-- 총 {len(all_rows)}개 매핑 값 삽입",
    ]

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding='utf-8') as f:
        f.write("\n".join(lines))

    print(f"완료: {len(all_rows)}개 매핑 → output/ml_encoding_map_create_insert.sql")


if __name__ == "__main__":
    main()
