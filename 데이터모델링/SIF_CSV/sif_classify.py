"""
SIF 발생형태 패턴 매칭 분류
정규화/건설업_normalized.csv + 제조업_normalized.csv
→ accident_type_mapped 컬럼 채우기
→ 미분류 건 별도 저장 (LLM 후처리용)
"""

import csv, os, re, sys
sys.stdout.reconfigure(encoding='utf-8')
from collections import Counter

BASE    = os.path.dirname(os.path.abspath(__file__))
NOR_DIR = os.path.join(BASE, "정규화")
CON_DIR = os.path.join(NOR_DIR, "건설업")
MFG_DIR = os.path.join(NOR_DIR, "제조업")

# ── 패턴 우선순위 순서 (위에 있을수록 먼저 매칭) ─────────────────
# (레이블, [키워드 목록])
PATTERNS = [
    ("감전",              ["감전", "전기에 접촉", "누전"]),
    ("폭발파열",          ["폭발", "파열"]),
    ("화재",              ["화재", "화염", "불이 붙"]),
    ("산소결핍",          ["질식", "산소결핍", "산소 결핍", "유해가스", "일산화탄소"]),
    ("화학물질누출접촉",  ["중독", "화학물질", "누출", "유해물질", "화학가스", "가스 누출", "독성"]),
    ("빠짐익사",          ["빠져", "익사", "물에 빠", "수몰"]),
    ("이상온도물체접촉",  ["화상", "고온", "이상온도", "뜨거운", "열에 의"]),
    ("절단베임찔림",      ["절단", "베여", "베임", "찔려", "찔림", "절단되"]),
    ("무너짐",            ["무너져", "무너지", "붕괴", "토사 붕", "사면 붕", "흙막이 붕"]),
    ("깔림.뒤집힘",       ["깔려", "깔림", "전복", "뒤집혀", "뒤집힘"]),
    ("끼임",              ["끼여", "끼어", "말려", "협착", "끼임"]),
    ("절단베임찔림",      ["프레스", "금형"]),   # 프레스 작업 = 끼임/절단
    ("물체에맞음",        ["맞아", "낙하물", "낙석", "날아와", "맞음", "물체에 맞", "낙하"]),
    ("사업장내교통사고",  ["차량에", "차에 치", "트럭에", "지게차에 치", "건설기계에 치"]),
    ("부딪힘",            ["부딪혀", "부딪힘", "충돌", "치어", "들이받"]),
    ("넘어짐",            ["넘어져", "넘어짐", "미끄러져", "미끄러짐"]),
    ("떨어짐",            ["떨어져", "떨어짐", "추락", "낙하하여 사망"]),  # 낙하 단독은 물체에맞음이라 조건 추가
]


def classify(text: str) -> str:
    t = text.replace(" ", "").replace("\n", "")  # 공백 제거 후 매칭
    for label, keywords in PATTERNS:
        for kw in keywords:
            if kw.replace(" ", "") in t:
                return label
    return ""  # 미분류


def process(filename: str):
    subdir = CON_DIR if filename.startswith("건설업") else MFG_DIR
    src = os.path.join(subdir, filename)
    dst = os.path.join(subdir, filename)
    miss_dst = os.path.join(subdir, filename.replace(".csv", "_미분류.csv"))

    with open(src, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
        fields = list(rows[0].keys()) if rows else []

    classified = []
    missed     = []
    counter    = Counter()

    for r in rows:
        label = classify(r.get("accident_summary", ""))
        r["accident_type_mapped"] = label
        counter[label if label else "미분류"] += 1
        if label:
            classified.append(r)
        else:
            missed.append(r)

    # 전체 덮어쓰기
    with open(dst, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)

    # 미분류만 별도 저장
    if missed:
        with open(miss_dst, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(missed)

    total = len(rows)
    n_cls = len(classified)
    n_mis = len(missed)
    print(f"\n{'='*50}")
    print(f"  {filename}")
    print(f"  총 {total}행 | 분류 {n_cls}행 ({n_cls/total*100:.1f}%) | 미분류 {n_mis}행 ({n_mis/total*100:.1f}%)")
    print(f"{'='*50}")
    print("  발생형태 분포:")
    for k, v in counter.most_common():
        bar = "█" * (v // 10)
        print(f"    {v:4d}  {k:<20s}  {bar}")
    if missed:
        print(f"\n  미분류 저장: {miss_dst}")


if __name__ == "__main__":
    process("건설업_normalized.csv")
    process("제조업_normalized.csv")
