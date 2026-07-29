#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fetch_laws.py — 국가법령정보 공동활용 API 법령 원문 수집기
SafeWork AI / 데이터·DB 파트

기능
  1) 법령명으로 목록 조회(lawSearch.do) → 법령마스터번호(MST) 획득
  2) MST로 본문 조회(lawService.do) → 조문 XML 획득
  3) 조·항 단위로 파싱 → law_article 적재용 staging JSON 생성
  4) 원본 XML은 raw/ 에 그대로 보관 (재현·검증용)

사용법
  # 0) 먼저 태그 구조 검증 (첫 실행 시 반드시)
  python fetch_laws.py --oc YOUR_ID --inspect "산업안전보건법"

  # 1) 전체 수집
  python fetch_laws.py --oc YOUR_ID

  --oc : 국가법령정보 승인받은 이메일 아이디 (@ 앞부분). 예) hjk774

주의
  - 국가법령정보 API는 인증키가 아니라 'OC=이메일아이디'를 인증값으로 씁니다.
  - 실제 응답의 XML 태그명이 이 스크립트 가정과 다르면 --inspect 로 먼저 확인하고
    parse_body()의 태그명을 조정하세요. (가장 흔한 실패 지점)
"""

import argparse
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from urllib.parse import urlencode
from urllib.request import urlopen, Request

# ------------------------------------------------------------------
# 설정
# ------------------------------------------------------------------
BASE = "http://www.law.go.kr/DRF"
SEARCH_URL = BASE + "/lawSearch.do"
SERVICE_URL = BASE + "/lawService.do"

# 선정목록 문서의 핵심 5개 법령 (정확한 법령명으로 검색)
TARGET_LAWS = [
    "산업안전보건법",
    "산업안전보건법 시행령",
    "산업안전보건법 시행규칙",
    "산업안전보건기준에 관한 규칙",
    "중대재해 처벌 등에 관한 법률",
]

HERE = os.path.dirname(os.path.abspath(__file__))
RAW_DIR = os.path.join(HERE, "raw")
STAGING = os.path.join(HERE, "laws_staging.json")
REQUEST_GAP_SEC = 0.5   # API 예의상 호출 간 간격


# ------------------------------------------------------------------
# HTTP
# ------------------------------------------------------------------
def http_get(url, params):
    full = url + "?" + urlencode(params, encoding="utf-8")
    req = Request(full, headers={"User-Agent": "SafeWorkAI-collector/1.0"})
    with urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8", errors="replace"), full


# ------------------------------------------------------------------
# 1단계: 법령명 → MST
# ------------------------------------------------------------------
def search_law(oc, law_name):
    """법령명 정확일치 1건의 dict(name, mst, eff, link) 반환. 없으면 None."""
    params = {"OC": oc, "target": "law", "type": "XML",
              "query": law_name, "display": "20"}
    text, url = http_get(SEARCH_URL, params)
    root = ET.fromstring(text)

    candidates = []
    for law in root.iter("law"):
        name = _find_text(law, ["법령명한글", "법령명", "법령명_한글"])
        mst = _find_text(law, ["법령일련번호", "MST", "법령마스터번호"])
        eff = _find_text(law, ["시행일자"])
        link = _find_text(law, ["법령상세링크"])
        if name:
            candidates.append({"name": name.strip(), "mst": mst,
                               "eff": eff, "link": link})

    exact = [c for c in candidates if c["name"] == law_name]
    if not exact:
        print(f"  [경고] '{law_name}' 정확일치 없음. 후보: "
              f"{[c['name'] for c in candidates][:5]}", file=sys.stderr)
        return None
    if len(exact) > 1:
        exact.sort(key=lambda c: c["eff"] or "", reverse=True)  # 최신 시행 = 현행
        print(f"  [주의] '{law_name}' 다건({len(exact)}). 최신 시행 선택: "
              f"{exact[0]['eff']}", file=sys.stderr)
    return exact[0]


# ------------------------------------------------------------------
# 2단계: MST → 조문 본문
# ------------------------------------------------------------------
def fetch_body(oc, mst):
    params = {"OC": oc, "target": "law", "type": "XML", "MST": mst}
    return http_get(SERVICE_URL, params)


# ------------------------------------------------------------------
# 파싱: 조문 XML → law_article rows
# ------------------------------------------------------------------
def parse_body(xml_text, law_name_fallback, source_url):
    """
    반환: list of dict (law_article 컬럼과 1:1)
      law_name, article_no, clause_no, title, content, effective_date, source_url
    조문 1개에 항이 여러 개면 항 단위로 행을 나눈다(항 없으면 조 1행).
    """
    root = ET.fromstring(xml_text)

    law_name = _find_text(root, ["법령명_한글", "법령명한글", "법령명"]) or law_name_fallback
    eff = _fmt_date(_find_text(root, ["시행일자"]))

    rows = []
    for jo in root.iter("조문단위"):
        # 편·장·절 제목(조문여부='전문')은 실제 조문이 아니므로 제외
        jo_yn = _find_text(jo, ["조문여부"])
        if jo_yn and jo_yn.strip() == "전문":
            continue
        jo_no_raw = _find_text(jo, ["조문번호"])
        jo_branch = _find_text(jo, ["조문가지번호"])   # 제63조의2 → 가지번호 2
        jo_title = _find_text(jo, ["조문제목"])
        jo_content = _find_text(jo, ["조문내용"])
        article_no = _fmt_article_no(jo_no_raw, jo_branch)
        if not article_no:
            continue

        hang_list = list(jo.iter("항"))
        if not hang_list:
            rows.append(_row(law_name, article_no, None, jo_title,
                             jo_content, eff, source_url))
            continue

        for hang in hang_list:
            hang_no_raw = _find_text(hang, ["항번호"])
            hang_content = _find_text(hang, ["항내용"])
            clause_no = _fmt_clause_no(hang_no_raw)
            ho_texts = [t for t in
                        (_find_text(ho, ["호내용"]) for ho in hang.iter("호"))
                        if t]
            content = hang_content or ""
            if ho_texts:
                content = (content + "\n" + "\n".join(ho_texts)).strip()
            rows.append(_row(law_name, article_no, clause_no, jo_title,
                             content, eff, source_url))
    return rows


def _row(law_name, article_no, clause_no, title, content, eff, url):
    return {
        "law_name": law_name.strip() if law_name else None,
        "article_no": article_no,
        "clause_no": clause_no,
        "title": (title or "").strip() or None,
        "content": _clean(content),
        "effective_date": eff,
        "source_url": url,
    }


# ------------------------------------------------------------------
# 유틸: 태그 탐색·형식 변환
# ------------------------------------------------------------------
def _all_text(el):
    """요소 안의 모든 텍스트(중첩 태그 포함)를 이어붙인다.
    .text 만 쓰면 중간에 인라인 태그가 있을 때 뒷부분이 잘리므로 itertext 사용."""
    return "".join(el.itertext())


def _find_text(elem, names):
    """자식/후손 중 이름이 names 에 있는 첫 요소의 '전체' 텍스트."""
    for n in names:
        found = elem.find(n)
        if found is not None:
            t = _all_text(found)
            if t and t.strip():
                return t
    for n in names:
        for sub in elem.iter(n):
            t = _all_text(sub)
            if t and t.strip():
                return t
    return None


def _clean(s):
    if not s:
        return None
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip() or None


def _fmt_article_no(raw, branch=None):
    """'38' → '제38조'. 가지번호(branch)가 있으면 '제38조의2' 형태로 결합.
    '38의2'처럼 raw 자체에 가지가 붙어 오는 경우도 처리."""
    if not raw:
        return None
    raw = raw.strip()
    if raw.startswith("제"):
        base = raw
    else:
        m = re.match(r"^(\d+)(?:의(\d+))?$", raw)
        if m:
            base = f"제{m.group(1)}조" + (f"의{m.group(2)}" if m.group(2) else "")
        else:
            base = f"제{raw}조"
    # 별도 가지번호 태그가 있고 아직 '의N'이 안 붙었으면 결합 (0/빈값 제외)
    if branch and branch.strip() not in ("", "0") and "의" not in base:
        base = f"{base}의{branch.strip()}"
    return base


def _fmt_clause_no(raw):
    """'1' → '제1항'. 원문자 ①②③ → 숫자 항 변환."""
    if not raw:
        return None
    raw = raw.strip()
    circ = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮"
    if raw and raw[0] in circ:
        return f"제{circ.index(raw[0]) + 1}항"
    if raw.startswith("제"):
        return raw
    m = re.match(r"^(\d+)$", raw)
    return f"제{m.group(1)}항" if m else raw


def _fmt_date(raw):
    """'20240117' → '2024-01-17'."""
    if not raw:
        return None
    raw = raw.strip()
    m = re.match(r"^(\d{4})(\d{2})(\d{2})$", raw)
    return f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else raw


# ------------------------------------------------------------------
# inspect: 실제 응답 구조를 눈으로 확인
# ------------------------------------------------------------------
def inspect(oc, law_name):
    print(f"[inspect] '{law_name}' 목록 조회 ...")
    hit = search_law(oc, law_name)
    if not hit:
        print("  목록에서 못 찾음. 법령명/승인상태 확인.")
        return
    print(f"  MST={hit['mst']}  시행일자={hit['eff']}")
    print("[inspect] 본문 조회 ...")
    xml_text, url = fetch_body(oc, hit["mst"])
    os.makedirs(RAW_DIR, exist_ok=True)
    p = os.path.join(RAW_DIR, "INSPECT_sample.xml")
    with open(p, "w", encoding="utf-8") as f:
        f.write(xml_text)
    print(f"  원본 저장: {p}")
    root = ET.fromstring(xml_text)
    print("\n  [태그 트리 상위 구조] — 아래 태그명이 파서 가정과 같은지 확인:")
    _print_tree(root, max_depth=3)
    print("\n  [첫 조문 파싱 결과 미리보기]")
    rows = parse_body(xml_text, law_name, url)
    for r in rows[:3]:
        print("   -", r["article_no"], r["clause_no"] or "", "|",
              (r["title"] or "")[:20], "|",
              (r["content"] or "")[:40].replace("\n", " "))
    print(f"\n  총 파싱 행수: {len(rows)}")
    print("  ※ 행수가 0이거나 비정상이면 parse_body() 태그명을 위 트리에 맞춰 수정하세요.")


def _print_tree(elem, depth=0, max_depth=3, seen=None):
    if seen is None:
        seen = set()
    if depth > max_depth:
        return
    key = (depth, elem.tag)
    if key not in seen:
        seen.add(key)
        print("   " + "  " * depth + f"<{elem.tag}>")
    for child in list(elem)[:6]:
        _print_tree(child, depth + 1, max_depth, seen)


# ------------------------------------------------------------------
# main
# ------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--oc", required=True, help="국가법령정보 승인 이메일 아이디(@앞부분)")
    ap.add_argument("--inspect", metavar="LAW_NAME",
                    help="한 법령의 응답 구조만 점검하고 종료")
    args = ap.parse_args()

    if args.inspect:
        inspect(args.oc, args.inspect)
        return

    os.makedirs(RAW_DIR, exist_ok=True)
    all_rows = []
    summary = []
    for name in TARGET_LAWS:
        print(f"[수집] {name}")
        try:
            hit = search_law(args.oc, name)
            if not hit:
                summary.append((name, "실패(목록없음)", 0))
                continue
            time.sleep(REQUEST_GAP_SEC)
            xml_text, url = fetch_body(args.oc, hit["mst"])
            safe = re.sub(r"[^\w가-힣]", "_", name)
            with open(os.path.join(RAW_DIR, f"{safe}.xml"), "w",
                      encoding="utf-8") as f:
                f.write(xml_text)
            rows = parse_body(xml_text, name, url)
            all_rows.extend(rows)
            summary.append((name, f"OK (MST={hit['mst']})", len(rows)))
            print(f"       조문행 {len(rows)}건")
            time.sleep(REQUEST_GAP_SEC)
        except Exception as e:
            summary.append((name, f"오류: {e}", 0))
            print(f"       [오류] {e}", file=sys.stderr)

    with open(STAGING, "w", encoding="utf-8") as f:
        json.dump(all_rows, f, ensure_ascii=False, indent=2)

    print("\n===== 수집 요약 =====")
    for name, status, n in summary:
        print(f"  {name:28s} {status:22s} {n:>5}행")
    print(f"  staging 저장: {STAGING} (총 {len(all_rows)}행)")
    print("  다음: python load_laws.py 로 DB 적재")


if __name__ == "__main__":
    main()
