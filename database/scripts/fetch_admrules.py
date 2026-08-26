#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
국가법령정보 행정규칙 API → law_admin_rule 적재 (산재·안전 고시·예규).
선행: SCHEMA_13_ADMINRULE.SQL, 현행 행정규칙 목록/본문 API 승인.
실행:
  python fetch_admrules.py --oc YOUR_OC --inspect "산업안전보건"   # 구조 점검
  set PGHOST=localhost PGDATABASE=ai_safework PGUSER=postgres PGPASSWORD=...
  python fetch_admrules.py --oc YOUR_OC                            # 수집·적재
  python fetch_admrules.py --oc YOUR_OC --dry-run                  # 통계만
"""

import argparse
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from urllib.parse import urlencode
from urllib.request import urlopen, Request

BASE = "https://www.law.go.kr/DRF"
SEARCH = BASE + "/lawSearch.do"
SERVICE = BASE + "/lawService.do"
KEYWORDS = ["산업안전보건", "산업재해", "중대재해", "안전보건"]
GAP = 0.4


def http_get(url, params):
    full = url + "?" + urlencode(params, encoding="utf-8")
    req = Request(full, headers={"User-Agent": "SafeWorkAI/1.0"})
    with urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", errors="replace"), full


def _all_text(el):
    return "".join(el.itertext())


def _find(elem, names):
    for n in names:
        f = elem.find(n)
        if f is not None:
            t = _all_text(f)
            if t and t.strip():
                return t.strip()
    for n in names:
        for sub in elem.iter(n):
            t = _all_text(sub)
            if t and t.strip():
                return t.strip()
    return None


def _clean(s):
    if not s:
        return None
    s = re.sub(r"<\s*br\s*/?\s*>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    s = re.sub(r"&nbsp;", " ", s)
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip() or None


def _date(s):
    if not s:
        return None
    m = re.search(r"(\d{4})\D?(\d{2})\D?(\d{2})", s)
    return f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else None


def search_list(oc, keyword, display=20):
    """행정규칙 목록 조회 → 메타데이터 dict 리스트."""
    params = {"OC": oc, "target": "admrul", "type": "XML",
              "query": keyword, "display": display}
    text, _ = http_get(SEARCH, params)
    root = ET.fromstring(text)
    out = []
    for a in root.iter("admrul"):
        aid = _find(a, ["행정규칙일련번호", "행정규칙ID"])
        if aid and aid.isdigit():
            out.append({
                "admrul_id": int(aid),
                "rule_name": _clean(_find(a, ["행정규칙명"])),
                "rule_type": _find(a, ["행정규칙종류"]),
                "ministry": _find(a, ["소관부처명"]),
                "issue_date": _date(_find(a, ["발령일자"])),
                "issue_no": _find(a, ["발령번호"]),
                "keyword": keyword,
            })
    return out


def fetch_content(oc, aid):
    text, url = http_get(SERVICE, {"OC": oc, "target": "admrul", "type": "XML", "ID": aid})
    root = ET.fromstring(text)
    parts = [_clean(_all_text(e)) for e in root.iter("조문내용")]
    parts = [p for p in parts if p]
    content = "\n".join(parts) if parts else _clean(_find(root, ["조문내용", "내용", "본문"]))
    return content, url


def inspect(oc, keyword):
    print(f"[inspect] '{keyword}' 행정규칙 목록 조회 ...")
    lst = search_list(oc, keyword, display=5)
    print(f"  목록 {len(lst)}건")
    for r in lst[:5]:
        print(f"   - {r['admrul_id']} | [{r['rule_type']}] {r['rule_name']} ({r['ministry']}, {r['issue_date']})")
    if not lst:
        print("  목록 0건 → 키워드/승인상태 확인")
        return
    print("\n[inspect] 첫 행정규칙 본문 태그 ...")
    text, _ = http_get(SERVICE, {"OC": oc, "target": "admrul", "type": "XML", "ID": lst[0]["admrul_id"]})
    root = ET.fromstring(text)
    print("  태그:", ", ".join(sorted({e.tag for e in root.iter()})))
    content, _ = fetch_content(oc, lst[0]["admrul_id"])
    print(f"  본문 미리보기: {(content or '(없음)')[:100]}")
    print("  ※ 본문이 비면 fetch_content 태그명 조정.")


UPSERT = """
INSERT INTO law_admin_rule
 (admrul_id,rule_name,rule_type,ministry,issue_date,issue_no,content,keyword,source_url)
VALUES
 (%(admrul_id)s,%(rule_name)s,%(rule_type)s,%(ministry)s,%(issue_date)s,%(issue_no)s,
  %(content)s,%(keyword)s,%(source_url)s)
ON CONFLICT (admrul_id) DO UPDATE SET
  rule_name=EXCLUDED.rule_name, content=EXCLUDED.content, fetched_at=now();
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--oc", required=True)
    ap.add_argument("--inspect", metavar="KW")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--display", type=int, default=20)
    args = ap.parse_args()

    if args.inspect:
        inspect(args.oc, args.inspect)
        return

    seen, rows = set(), []
    for kw in KEYWORDS:
        lst = search_list(args.oc, kw, args.display)
        print(f"  '{kw}' 목록 {len(lst)}건")
        for r in lst:
            if r["admrul_id"] in seen:
                continue
            seen.add(r["admrul_id"])
            try:
                content, url = fetch_content(args.oc, r["admrul_id"])
                r["content"], r["source_url"] = content, url
                rows.append(r)
            except Exception as e:
                print(f"    [오류] {r['admrul_id']}: {e}", file=sys.stderr)
            time.sleep(GAP)
        time.sleep(GAP)

    print(f"\n중복 제거 후 {len(rows)}건")
    if args.dry_run:
        for r in rows[:8]:
            print(f"  {r['admrul_id']} | [{r['rule_type']}] {r['rule_name']} / {r['ministry']}")
        print("--dry-run: DB 미적재.")
        return

    try:
        import psycopg2, psycopg2.extras
    except ImportError:
        sys.exit("psycopg2 미설치: pip install psycopg2-binary")
    conn = psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"), port=os.getenv("PGPORT", "5432"),
        dbname=os.getenv("PGDATABASE", "ai_safework"), user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", ""))
    try:
        with conn, conn.cursor() as cur:
            psycopg2.extras.execute_batch(cur, UPSERT, rows, page_size=50)
            cur.execute("SELECT count(*) FROM law_admin_rule;")
            print(f"적재 완료. law_admin_rule 총 {cur.fetchone()[0]}건.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
