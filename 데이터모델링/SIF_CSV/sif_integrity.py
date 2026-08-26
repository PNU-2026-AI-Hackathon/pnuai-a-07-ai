"""
정규화 CSV 데이터 무결성 체크
- 총 행수, 결측치 개수, 분류 현황
"""

import csv, os, sys
sys.stdout.reconfigure(encoding='utf-8')
from collections import Counter

BASE    = os.path.dirname(os.path.abspath(__file__))
NOR_DIR = os.path.join(BASE, "정규화")

TARGETS = [
    os.path.join(NOR_DIR, "건설업", "건설업_normalized.csv"),
    os.path.join(NOR_DIR, "제조업", "제조업_normalized.csv"),
]

KEY_COLS = [
    "industry_div", "accident_summary", "causal_object",
    "high_risk_situation", "causal_factor", "countermeasure",
    "accident_type_mapped",
]


def check(path):
    fname = os.path.basename(path)
    print(f"\n{'='*55}")
    print(f"  {fname}")
    print(f"{'='*55}")

    with open(path, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        print("  파일 비어 있음")
        return

    total = len(rows)
    print(f"  총 행수: {total:,}")

    # 결측치 (빈 문자열)
    print(f"\n  [결측치]")
    any_missing = False
    for col in KEY_COLS:
        missing = sum(1 for r in rows if not r.get(col, "").strip())
        if missing:
            pct = missing / total * 100
            print(f"    {col:<25s}: {missing:4d}행 ({pct:.1f}%)")
            any_missing = True
    if not any_missing:
        print("    없음")

    # accident_type_mapped 분포
    print(f"\n  [accident_type_mapped 분포]")
    cnt = Counter(r.get("accident_type_mapped", "").strip() or "(빈값)" for r in rows)
    for label, n in cnt.most_common():
        bar = "■" * (n // 20)
        print(f"    {n:4d}  {label:<20s}  {bar}")

    # 분류 요약
    unclassified = cnt.get("분류불가", 0) + cnt.get("(빈값)", 0)
    classified   = total - unclassified
    print(f"\n  분류 완료: {classified:,} ({classified/total*100:.1f}%)")
    print(f"  미분류:   {unclassified:,} ({unclassified/total*100:.1f}%)")


if __name__ == "__main__":
    for path in TARGETS:
        if os.path.exists(path):
            check(path)
        else:
            print(f"\n파일 없음: {path}")
    print(f"\n{'='*55}")
    print("완료.")
