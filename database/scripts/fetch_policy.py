#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
정부24 공공서비스 API → policy_service 적재 (산재·안전 관련 정책/지원).
선행: SCHEMA_11_POLICY.SQL
실행:
  set GOV_KEY=발급받은_serviceKey
  set PGHOST=localhost PGDATABASE=ai_safework PGUSER=postgres PGPASSWORD=...
  python fetch_policy.py                 # 수집·적재
  python fetch_policy.py --dry-run       # DB 미적재, 통계만
  python fetch_policy.py --key <키>      # 환경변수 대신 직접 지정
"""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from collections import OrderedDict

BASE = "https://api.odcloud.kr/api/gov24/v3/serviceList"
KEYWORDS = ["산재", "안전보건", "위험성평가", "재해예방", "산업안전"]  # 서비스명 LIKE


def fetch_keyword(key, kw, per=100):
    rows, page = [], 1
    while True:
        params = {"page": page, "perPage": per, "returnType": "JSON",
                  "serviceKey": key, "cond[서비스명::LIKE]": kw}
        url = BASE + "?" + urllib.parse.urlencode(params, encoding="utf-8")
        with urllib.request.urlopen(url, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        rows += data.get("data", [])
        if data.get("currentCount", 0) < per:
            break
        page += 1
        time.sleep(0.3)
    return rows


def is_employer(user_type, title):
    ut = user_type or ""
    if any(k in ut for k in ("법인", "시설", "단체", "기업", "사업")):
        return True
    return any(k in (title or "") for k in ("사업주", "사업장", "예방", "요율"))


def map_row(r):
    return {
        "service_id": r.get("서비스ID"),
        "title": r.get("서비스명"),
        "summary": r.get("서비스목적요약"),
        "support_type": r.get("지원유형"),
        "field": r.get("서비스분야"),
        "user_type": r.get("사용자구분"),
        "is_employer": is_employer(r.get("사용자구분"), r.get("서비스명")),
        "target": r.get("지원대상"),
        "criteria": r.get("선정기준"),
        "content": r.get("지원내용"),
        "apply_method": r.get("신청방법"),
        "apply_deadline": r.get("신청기한"),
        "agency": r.get("소관기관명"),
        "dept": r.get("부서명"),
        "receive_agency": r.get("접수기관"),
        "phone": r.get("전화문의"),
        "detail_url": r.get("상세조회URL"),
    }


UPSERT = """
INSERT INTO policy_service
 (service_id,title,summary,support_type,field,user_type,is_employer,
  target,criteria,content,apply_method,apply_deadline,agency,dept,
  receive_agency,phone,detail_url)
VALUES
 (%(service_id)s,%(title)s,%(summary)s,%(support_type)s,%(field)s,%(user_type)s,%(is_employer)s,
  %(target)s,%(criteria)s,%(content)s,%(apply_method)s,%(apply_deadline)s,%(agency)s,%(dept)s,
  %(receive_agency)s,%(phone)s,%(detail_url)s)
ON CONFLICT (service_id) DO UPDATE SET
  title=EXCLUDED.title, summary=EXCLUDED.summary, support_type=EXCLUDED.support_type,
  field=EXCLUDED.field, user_type=EXCLUDED.user_type, is_employer=EXCLUDED.is_employer,
  target=EXCLUDED.target, criteria=EXCLUDED.criteria, content=EXCLUDED.content,
  apply_method=EXCLUDED.apply_method, apply_deadline=EXCLUDED.apply_deadline,
  agency=EXCLUDED.agency, dept=EXCLUDED.dept, receive_agency=EXCLUDED.receive_agency,
  phone=EXCLUDED.phone, detail_url=EXCLUDED.detail_url, fetched_at=now();
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", default=os.getenv("GOV_KEY", ""))
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    if not args.key:
        sys.exit("serviceKey 없음: set GOV_KEY=... 또는 --key <키>")

    merged = OrderedDict()   # 서비스ID 기준 중복 제거
    for kw in KEYWORDS:
        found = fetch_keyword(args.key, kw)
        for r in found:
            sid = r.get("서비스ID")
            if sid and sid not in merged:
                merged[sid] = map_row(r)
        print(f"  '{kw}' {len(found)}건")
        time.sleep(0.3)

    rows = list(merged.values())
    emp = sum(1 for r in rows if r["is_employer"])
    print(f"\n중복 제거 후 {len(rows)}건 (사업주 대상 {emp}건)")

    if args.dry_run:
        for r in rows[:8]:
            tag = "[사업주]" if r["is_employer"] else "        "
            print(f"  {tag} {r['title']} / {r['agency']} / {r['support_type']}")
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
            psycopg2.extras.execute_batch(cur, UPSERT, rows, page_size=100)
            cur.execute("SELECT count(*), count(*) FILTER (WHERE is_employer) FROM policy_service;")
            total, emp_db = cur.fetchone()
        print(f"적재 완료. policy_service 총 {total}건 (사업주 대상 {emp_db}).")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
