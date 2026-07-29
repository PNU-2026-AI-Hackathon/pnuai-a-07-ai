#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
checklist_filtered.json → checklist_item 적재 (835문항, 기존 교체).
선행: SCHEMA_9_CHECKLIST_V2.SQL
실행:
  set PGHOST=localhost PGDATABASE=ai_safework PGUSER=postgres PGPASSWORD=...
  python load_checklist.py            # 적재
  python load_checklist.py --dry-run  # 통계만
"""

import argparse
import json
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_JSON = os.path.join(HERE, "checklist_filtered.json")
IND_PREFIX = {"건설업": "CON", "제조업": "MFG"}
CRITICAL_WEIGHT = 8


def build_rows(data):
    rows, seq, order = [], {}, {}
    for industry, works in data.items():
        prefix = IND_PREFIX.get(industry, "ETC")
        for work, forms in works.items():
            head = work.split(" ", 1)
            work_clean = head[1] if len(head) == 2 and head[0][0].isdigit() else work
            for form, qs in forms.items():
                for q in qs:
                    seq[industry] = seq.get(industry, 0) + 1
                    order[industry] = order.get(industry, 0) + 1
                    weight = float(q.get("가중치", 1))
                    rows.append({
                        "item_code": f"{prefix}-{seq[industry]:04d}",
                        "category": form,
                        "question": q["질문"],
                        "target_industry": industry,
                        "work_type": work_clean,
                        "risk_weight": weight,
                        "is_critical": weight >= CRITICAL_WEIGHT,
                        "evidence": json.dumps(q.get("기준_재해개요", []), ensure_ascii=False),
                        "display_order": order[industry],
                    })
    return rows


INSERT_SQL = """
INSERT INTO checklist_item
  (item_code, category, question, target_industry, work_type,
   risk_weight, is_critical, evidence_cases, display_order, is_active)
VALUES
  (%(item_code)s, %(category)s, %(question)s, %(target_industry)s, %(work_type)s,
   %(risk_weight)s, %(is_critical)s, %(evidence)s::jsonb, %(display_order)s, TRUE);
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--json", default=DEFAULT_JSON)
    args = ap.parse_args()

    if not os.path.exists(args.json):
        sys.exit(f"JSON 없음: {args.json}")

    data = json.load(open(args.json, encoding="utf-8"))
    rows = build_rows(data)

    by_ind = Counter(r["target_industry"] for r in rows)
    crit = sum(1 for r in rows if r["is_critical"])
    works = len(set((r["target_industry"], r["work_type"]) for r in rows))
    print(f"생성 문항 {len(rows)}개 | 업종별 {dict(by_ind)} | 작업 {works} | 중대 {crit}")

    dup = [c for c, n in Counter(r["item_code"] for r in rows).items() if n > 1]
    if dup:
        sys.exit(f"[오류] 중복 item_code: {dup[:5]}")

    if args.dry_run:
        for r in rows[:3]:
            print(f"  {r['item_code']} [{r['target_industry']}/{r['work_type']}/{r['category']}] "
                  f"w={r['risk_weight']}  {r['question'][:40]}")
        print("--dry-run: DB 미적재.")
        return

    try:
        import psycopg2, psycopg2.extras
    except ImportError:
        sys.exit("psycopg2 미설치: pip install psycopg2-binary")

    conn = psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"), port=os.getenv("PGPORT", "5432"),
        dbname=os.getenv("PGDATABASE", "ai_safework"), user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", ""),
    )
    try:
        with conn, conn.cursor() as cur:
            cur.execute("""SELECT 1 FROM information_schema.columns
                           WHERE table_name='checklist_item' AND column_name='work_type'""")
            if not cur.fetchone():
                sys.exit("work_type 컬럼 없음 → SCHEMA_9_CHECKLIST_V2.SQL 먼저 실행")
            cur.execute("TRUNCATE checklist_item RESTART IDENTITY CASCADE;")
            psycopg2.extras.execute_batch(cur, INSERT_SQL, rows, page_size=200)
            cur.execute("SELECT count(*) FROM checklist_item;")
            total = cur.fetchone()[0]
        print(f"적재 완료. checklist_item 총 {total}문항.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
