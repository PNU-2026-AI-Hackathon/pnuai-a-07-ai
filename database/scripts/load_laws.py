#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
load_laws.py — laws_staging.json → PostgreSQL law_article 적재
SafeWork AI / 데이터·DB 파트

기능
  - fetch_laws.py 가 만든 laws_staging.json 을 읽어 law_article 에 UPSERT
  - UNIQUE(law_name, article_no, clause_no) 충돌 시 내용 갱신
  - clause_no NULL 을 안전하게 처리 (NULL 은 UNIQUE 에서 서로 충돌 안 하므로
    COALESCE 로 빈 문자열 대체하여 중복 방지)

사용법
  export PGHOST=localhost PGPORT=5432 PGDATABASE=safework PGUSER=postgres PGPASSWORD=...
  python load_laws.py
  python load_laws.py --dry-run     # DB 안 건드리고 검증만

사전
  pip install psycopg2-binary
  SCHEMA_3.SQL 이 이미 적용되어 law_article 이 존재해야 함
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
STAGING = os.path.join(HERE, "laws_staging.json")

# NULL clause_no 를 UNIQUE 제약에서 구별하기 위한 보정.
# law_article 의 UNIQUE(law_name, article_no, clause_no) 에서 clause_no=NULL 은
# Postgres 기본 규칙상 서로 '다름'으로 취급되어 중복 적재가 생길 수 있다.
# 그래서 UPSERT 를 COALESCE(clause_no,'') 기준의 부분 유니크로 잡는다.
DDL_GUARD = """
-- clause_no NULL 중복 방지용 표현식 유니크 인덱스 (없으면 생성)
CREATE UNIQUE INDEX IF NOT EXISTS uq_law_article_key
  ON law_article (law_name, article_no, COALESCE(clause_no, ''));
"""

UPSERT = """
INSERT INTO law_article
  (law_name, article_no, clause_no, title, content, effective_date, source_url)
VALUES
  (%(law_name)s, %(article_no)s, %(clause_no)s, %(title)s,
   %(content)s, %(effective_date)s, %(source_url)s)
ON CONFLICT (law_name, article_no, COALESCE(clause_no, ''))
DO UPDATE SET
  title          = EXCLUDED.title,
  content        = EXCLUDED.content,
  effective_date = EXCLUDED.effective_date,
  source_url     = EXCLUDED.source_url,
  updated_at     = now();
"""


def validate(rows):
    """적재 전 데이터 품질 점검. 문제 행을 리포트하되 치명적이면 중단."""
    errors, warns = [], []
    seen = set()
    for i, r in enumerate(rows):
        if not r.get("law_name"):
            errors.append(f"[{i}] law_name 없음")
        if not r.get("article_no"):
            errors.append(f"[{i}] article_no 없음")
        if not r.get("content"):
            warns.append(f"[{i}] {r.get('article_no')} content 비어있음")
        if not r.get("source_url"):
            warns.append(f"[{i}] {r.get('article_no')} source_url 비어있음")
        key = (r.get("law_name"), r.get("article_no"), r.get("clause_no") or "")
        if key in seen:
            warns.append(f"[{i}] 중복 키 {key} (UPSERT로 병합됨)")
        seen.add(key)
    return errors, warns


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true",
                    help="DB 미접속, 검증만 수행")
    ap.add_argument("--staging", default=STAGING)
    args = ap.parse_args()

    if not os.path.exists(args.staging):
        sys.exit(f"staging 파일 없음: {args.staging}\n먼저 fetch_laws.py 를 실행하세요.")

    with open(args.staging, encoding="utf-8") as f:
        rows = json.load(f)
    print(f"staging 행수: {len(rows)}")

    errors, warns = validate(rows)
    if warns:
        print(f"\n[경고 {len(warns)}건] (상위 10건)")
        for w in warns[:10]:
            print("  ", w)
    if errors:
        print(f"\n[치명 오류 {len(errors)}건] — 적재 중단")
        for e in errors[:20]:
            print("  ", e)
        sys.exit(1)

    # 법령별 집계 리포트
    by_law = {}
    for r in rows:
        by_law[r["law_name"]] = by_law.get(r["law_name"], 0) + 1
    print("\n[법령별 조문행 수]")
    for k, v in by_law.items():
        print(f"  {k:28s} {v:>5}행")

    if args.dry_run:
        print("\n--dry-run: DB 적재 생략. 검증만 완료.")
        return

    try:
        import psycopg2
        import psycopg2.extras
    except ImportError:
        sys.exit("psycopg2 미설치: pip install psycopg2-binary")

    conn = psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=os.getenv("PGPORT", "5432"),
        dbname=os.getenv("PGDATABASE", "safework"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", ""),
    )
    try:
        with conn, conn.cursor() as cur:
            cur.execute(DDL_GUARD)
            psycopg2.extras.execute_batch(cur, UPSERT, rows, page_size=200)
            cur.execute("SELECT count(*) FROM law_article;")
            total = cur.fetchone()[0]
        print(f"\n적재 완료. law_article 총 {total}행.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
