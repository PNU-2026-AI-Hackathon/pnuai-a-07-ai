#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
국가법령정보 판례 API → law_precedent 적재 (산재·중대재해 판례).
선행: SCHEMA_12_PRECEDENT.SQL, 판례 목록/본문 API 승인.
실행:
  python fetch_precedents.py --oc YOUR_OC --inspect "중대재해"   # 구조 점검
  set PGHOST=localhost PGDATABASE=ai_safework PGUSER=postgres PGPASSWORD=...
  python fetch_precedents.py --oc YOUR_OC                        # 수집·적재
  python fetch_precedents.py --oc YOUR_OC --dry-run              # 통계만

주의: 응답 태그가 아래 가정과 다르면 --inspect 로 확인 후 parse_* 태그명 조정.
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
KEYWORDS = ["중대재해", "산업안전보건법", "산업재해"]
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


def _date(s):
    if not s:
        return None
    m = re.search(r"(\d{4})\D?(\d{2})\D?(\d{2})", s)
    return f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else None


def _clean(s):
    """판례 텍스트의 HTML 마크업(<br/> 등) 정리."""
    if not s:
        return None
    s = re.sub(r"<\s*br\s*/?\s*>", "\n", s, flags=re.I)   # <br/> → 줄바꿈
    s = re.sub(r"<[^>]+>", " ", s)                          # 기타 태그 제거
    s = re.sub(r"&nbsp;", " ", s)
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip() or None


def search_ids(oc, keyword, display=20):
    """판례 목록 조회 → (판례일련번호, 사건명) 리스트."""
    params = {"OC": oc, "target": "prec", "type": "XML",
              "query": keyword, "display": display}
    text, _ = http_get(SEARCH, params)
    root = ET.fromstring(text)
    out = []
    for p in root.iter("prec"):
        pid = _find(p, ["판례일련번호", "판례정보일련번호"])
        name = _find(p, ["사건명"])
        if pid and pid.isdigit():
            out.append((pid, name))
    return out


def fetch_detail(oc, pid, keyword):
    params = {"OC": oc, "target": "prec", "type": "XML", "ID": pid}
    text, url = http_get(SERVICE, params)
    root = ET.fromstring(text)
    return {
        "prec_id": int(pid),
        "case_name": _clean(_find(root, ["사건명"])),
        "case_no": _clean(_find(root, ["사건번호"])),
        "court": _clean(_find(root, ["법원명"])),
        "decision_date": _date(_find(root, ["선고일자"])),
        "case_type": _clean(_find(root, ["사건종류명"])),
        "holding": _clean(_find(root, ["판시사항"])),
        "summary": _clean(_find(root, ["판결요지"])),
        "ref_articles": _clean(_find(root, ["참조조문"])),
        "content": _clean(_find(root, ["판례내용"])),
        "keyword": keyword,
        "source_url": url,
    }


def inspect(oc, keyword):
    print(f"[inspect] '{keyword}' 판례 목록 조회 ...")
    ids = search_ids(oc, keyword, display=5)
    print(f"  목록 {len(ids)}건")
    for pid, name in ids[:5]:
        print(f"   - {pid} | {name}")
    if not ids:
        print("  목록 0건 → 키워드/승인상태 확인")
        return
    print("\n[inspect] 첫 판례 본문 구조 ...")
    text, _ = http_get(SERVICE, {"OC": oc, "target": "prec", "type": "XML", "ID": ids[0][0]})
    root = ET.fromstring(text)
    seen = set()
    for el in root.iter():
        if el.tag not in seen:
            seen.add(el.tag)
    print("  본문 태그:", ", ".join(sorted(seen)))
    d = fetch_detail(oc, ids[0][0], keyword)
    print(f"\n  사건명: {d['case_name']}")
    print(f"  법원/일자: {d['court']} / {d['decision_date']}")
    print(f"  판결요지: {(d['summary'] or '')[:80]}")
    print("  ※ 값이 비면 parse 태그명을 위 '본문 태그'에 맞춰 수정하세요.")


UPSERT = """
INSERT INTO law_precedent
 (prec_id,case_name,case_no,court,decision_date,case_type,
  holding,summary,ref_articles,content,keyword,source_url)
VALUES
 (%(prec_id)s,%(case_name)s,%(case_no)s,%(court)s,%(decision_date)s,%(case_type)s,
  %(holding)s,%(summary)s,%(ref_articles)s,%(content)s,%(keyword)s,%(source_url)s)
ON CONFLICT (prec_id) DO UPDATE SET
  summary=EXCLUDED.summary, holding=EXCLUDED.holding, content=EXCLUDED.content,
  ref_articles=EXCLUDED.ref_articles, fetched_at=now();
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
        ids = search_ids(args.oc, kw, args.display)
        print(f"  '{kw}' 목록 {len(ids)}건")
        for pid, _name in ids:
            if pid in seen:
                continue
            seen.add(pid)
            try:
                rows.append(fetch_detail(args.oc, pid, kw))
            except Exception as e:
                print(f"    [오류] {pid}: {e}", file=sys.stderr)
            time.sleep(GAP)
        time.sleep(GAP)

    print(f"\n중복 제거 후 {len(rows)}건")
    if args.dry_run:
        for r in rows[:8]:
            print(f"  {r['prec_id']} | {r['court']} {r['decision_date']} | {r['case_name']}")
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
            cur.execute("SELECT count(*) FROM law_precedent;")
            print(f"적재 완료. law_precedent 총 {cur.fetchone()[0]}건.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
