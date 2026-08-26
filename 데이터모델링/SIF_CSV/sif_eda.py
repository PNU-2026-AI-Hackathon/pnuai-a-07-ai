"""
SIF 정규화 데이터 EDA
- 발생형태 분포
- 공종/작업명별 빈도 (건설업)
- 중분류별 빈도 (제조업)
- 기인물 Top N
- 고위험상황 Top N
- 공종×발생형태 크로스탭 (건설업)
- 중분류×발생형태 크로스탭 (제조업)
결과: SIF_CSV/EDA/ 폴더에 CSV 저장
"""

import csv, os, sys
sys.stdout.reconfigure(encoding='utf-8')
from collections import Counter

BASE    = os.path.dirname(os.path.abspath(__file__))
NOR_DIR = os.path.join(BASE, "정규화")
OUT_DIR = os.path.join(BASE, "EDA")
os.makedirs(OUT_DIR, exist_ok=True)

CON_PATH = os.path.join(NOR_DIR, "건설업", "건설업_normalized.csv")
MFG_PATH = os.path.join(NOR_DIR, "제조업", "제조업_normalized.csv")

TOP_N = 20


# ── 유틸 ──────────────────────────────────────────────────────

def load(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def print_counter(title, counter, n=TOP_N):
    print(f"\n  [{title}] (top {n})")
    for k, v in counter.most_common(n):
        bar = "■" * (v // 5)
        print(f"    {v:4d}  {(k or '(빈값)'):<30s}  {bar}")


def save_csv(filename, headers, rows):
    path = os.path.join(OUT_DIR, filename)
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(headers)
        w.writerows(rows)
    print(f"  → 저장: EDA/{filename}")


def crosstab(rows, row_key, col_key, top_rows=15, top_cols=10):
    """row_key × col_key 크로스탭 → (headers, data_rows)"""
    from collections import defaultdict
    counts = defaultdict(Counter)
    for r in rows:
        rk = r.get(row_key, "").strip() or "(빈값)"
        ck = r.get(col_key, "").strip() or "(빈값)"
        counts[rk][ck] += 1

    # 상위 col 선택
    total_col = Counter()
    for c in counts.values():
        total_col.update(c)
    top_col_labels = [k for k, _ in total_col.most_common(top_cols)]

    # 상위 row 선택 (총합 기준)
    row_totals = {rk: sum(c.values()) for rk, c in counts.items()}
    top_row_labels = [k for k, _ in Counter(row_totals).most_common(top_rows)]

    headers = [row_key] + top_col_labels + ["합계"]
    data = []
    for rk in top_row_labels:
        row_data = [rk] + [counts[rk].get(ck, 0) for ck in top_col_labels]
        row_data.append(row_totals[rk])
        data.append(row_data)

    return headers, data


# ── 건설업 EDA ────────────────────────────────────────────────

def eda_construction(rows):
    print(f"\n{'='*60}")
    print(f"  건설업  ({len(rows):,}행)")
    print(f"{'='*60}")

    # 발생형태 분포
    acc_cnt = Counter(r.get("accident_type_mapped", "") or "(빈값)" for r in rows)
    print_counter("발생형태 분포", acc_cnt, n=25)
    save_csv("건설업_발생형태.csv",
             ["발생형태", "건수"],
             [(k, v) for k, v in acc_cnt.most_common()])

    # 공종 빈도
    gong_cnt = Counter(r.get("gong_jong", "").strip() or "(빈값)" for r in rows)
    print_counter("공종 빈도", gong_cnt)
    save_csv("건설업_공종.csv",
             ["공종", "건수"],
             [(k, v) for k, v in gong_cnt.most_common()])

    # 작업명 빈도
    jak_cnt = Counter(r.get("jak_up_myung", "").strip() or "(빈값)" for r in rows)
    print_counter("작업명 빈도", jak_cnt)
    save_csv("건설업_작업명.csv",
             ["작업명", "건수"],
             [(k, v) for k, v in jak_cnt.most_common()])

    # 기인물 빈도
    obj_cnt = Counter(r.get("causal_object", "").strip() or "(빈값)" for r in rows)
    print_counter("기인물 빈도", obj_cnt)
    save_csv("건설업_기인물.csv",
             ["기인물", "건수"],
             [(k, v) for k, v in obj_cnt.most_common()])

    # 공종 × 발생형태 크로스탭
    headers, data = crosstab(rows, "gong_jong", "accident_type_mapped", top_rows=15, top_cols=10)
    print(f"\n  [공종 × 발생형태 크로스탭] (상위 15공종 × 상위 10발생형태)")
    header_str = f"  {'공종':<22s}" + "".join(f"{h:<10s}" for h in headers[1:])
    print(header_str)
    for row in data:
        row_str = f"  {str(row[0]):<22s}" + "".join(f"{str(v):<10s}" for v in row[1:])
        print(row_str)
    save_csv("건설업_공종×발생형태.csv", headers, data)


# ── 제조업 EDA ────────────────────────────────────────────────

def eda_manufacturing(rows):
    print(f"\n{'='*60}")
    print(f"  제조업  ({len(rows):,}행)")
    print(f"{'='*60}")

    # 발생형태 분포
    acc_cnt = Counter(r.get("accident_type_mapped", "") or "(빈값)" for r in rows)
    print_counter("발생형태 분포", acc_cnt, n=25)
    save_csv("제조업_발생형태.csv",
             ["발생형태", "건수"],
             [(k, v) for k, v in acc_cnt.most_common()])

    # 중분류 빈도
    mid_cnt = Counter(r.get("mid_industry", "").strip() or "(빈값)" for r in rows)
    print_counter("산재업종 중분류 빈도", mid_cnt)
    save_csv("제조업_중분류.csv",
             ["중분류", "건수"],
             [(k, v) for k, v in mid_cnt.most_common()])

    # 고위험작업·상황 빈도
    hi_cnt = Counter(r.get("high_risk_situation", "").strip() or "(빈값)" for r in rows)
    print_counter("고위험작업·상황 빈도", hi_cnt)
    save_csv("제조업_고위험상황.csv",
             ["고위험작업·상황", "건수"],
             [(k, v) for k, v in hi_cnt.most_common()])

    # 기인물 빈도
    obj_cnt = Counter(r.get("causal_object", "").strip() or "(빈값)" for r in rows)
    print_counter("기인물 빈도", obj_cnt)
    save_csv("제조업_기인물.csv",
             ["기인물", "건수"],
             [(k, v) for k, v in obj_cnt.most_common()])

    # 중분류 × 발생형태 크로스탭
    headers, data = crosstab(rows, "mid_industry", "accident_type_mapped", top_rows=15, top_cols=10)
    print(f"\n  [중분류 × 발생형태 크로스탭] (상위 15중분류 × 상위 10발생형태)")
    header_str = f"  {'중분류':<28s}" + "".join(f"{h:<10s}" for h in headers[1:])
    print(header_str)
    for row in data:
        row_str = f"  {str(row[0]):<28s}" + "".join(f"{str(v):<10s}" for v in row[1:])
        print(row_str)
    save_csv("제조업_중분류×발생형태.csv", headers, data)


# ── 메인 ──────────────────────────────────────────────────────

if __name__ == "__main__":
    con_rows = load(CON_PATH)
    mfg_rows = load(MFG_PATH)

    eda_construction(con_rows)
    eda_manufacturing(mfg_rows)

    print(f"\n{'='*60}")
    print(f"완료. EDA 결과: SIF_CSV/EDA/ 폴더 확인")
