"""
SIF CSV 컬럼 정규화 → SIF_CSV/정규화/ 저장
건설업.csv, 제조업등.csv → DB sif_case_enriched 스키마에 맞춤
LLM 분류(accident_type_mapped)는 후에 별도 진행
"""

import csv, os

BASE    = os.path.dirname(os.path.abspath(__file__))
CON_DIR = os.path.join(BASE, "정규화", "건설업")
MFG_DIR = os.path.join(BASE, "정규화", "제조업")
os.makedirs(CON_DIR, exist_ok=True)
os.makedirs(MFG_DIR, exist_ok=True)

# 출력 컬럼 순서 (DB sif_case_enriched 스키마와 동일)
FIELDS = [
    "industry_div",
    "accident_kind",
    "accident_summary",
    "causal_object",
    "high_risk_situation",
    "causal_factor",
    "countermeasure",
    "content",
    "gong_jong",
    "jak_up_myung",
    "mid_industry",
    "accident_type_mapped",
]


def make_content(summary, factor, measure):
    parts = []
    if summary: parts.append(summary)
    if factor:  parts.append(f"[유발요인] {factor}")
    if measure: parts.append(f"[대책] {measure}")
    return " ".join(parts)


# ── 건설업 정규화 ─────────────────────────────────────────────────
def normalize_construction():
    src = os.path.join(BASE, "건설업.csv")
    dst = os.path.join(CON_DIR, "건설업_normalized.csv")

    with open(src, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    with open(dst, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for r in rows:
            summary = r.get("재해개요", "")
            factor  = r.get("재해유발요인", "")
            measure = r.get("위험성 감소대책(예시)", "")
            w.writerow({
                "industry_div":        "건설업",
                "accident_kind":       "",           # 원본 CSV에 없음
                "accident_summary":    summary,
                "causal_object":       r.get("기인물", ""),
                "high_risk_situation": r.get("공종", ""),
                "causal_factor":       factor,
                "countermeasure":      measure,
                "content":             make_content(summary, factor, measure),
                "gong_jong":           r.get("공종", ""),
                "jak_up_myung":        r.get("작업명", ""),
                "mid_industry":        "",
                "accident_type_mapped": "",          # LLM 분류 후 채울 것
            })

    print(f"건설업 정규화 완료: {len(rows)}행 → {dst}")


# ── 제조업 정규화 ─────────────────────────────────────────────────
def normalize_manufacturing():
    src = os.path.join(BASE, "제조업등.csv")
    dst = os.path.join(MFG_DIR, "제조업_normalized.csv")

    with open(src, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    with open(dst, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for r in rows:
            summary = r.get("재해개요", "")
            factor  = r.get("재해유발요인", "")
            measure = r.get("위험성 감소대책(예시)", "")
            w.writerow({
                "industry_div":        r.get("산재업종(대분류)", "제조업등"),
                "accident_kind":       "",
                "accident_summary":    summary,
                "causal_object":       r.get("기인물", ""),
                "high_risk_situation": r.get("고위험작업·상황", ""),
                "causal_factor":       factor,
                "countermeasure":      measure,
                "content":             make_content(summary, factor, measure),
                "gong_jong":           "",
                "jak_up_myung":        "",
                "mid_industry":        r.get("산재업종(중분류)", ""),
                "accident_type_mapped": "",
            })

    print(f"제조업 정규화 완료: {len(rows)}행 → {dst}")


if __name__ == "__main__":
    normalize_construction()
    normalize_manufacturing()
    print("\n완료. SIF_CSV/정규화/ 폴더 확인.")
