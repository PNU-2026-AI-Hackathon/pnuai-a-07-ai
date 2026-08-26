"""
checklist_raw.json 필터링
- 공종당 발생형태 top 3 (케이스 건수 기준)
- 발생형태당 질문 top 5 (가중치 기준)
→ checklist_filtered.json
"""

import json, os, sys
sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(os.path.abspath(__file__))
RAW  = os.path.join(BASE, "checklist_raw.json")
OUT  = os.path.join(BASE, "checklist_filtered.json")

TOP_ACC  = 3   # 발생형태 top N
TOP_Q    = 5   # 질문 top N

with open(RAW, encoding='utf-8') as f:
    data = json.load(f)

result = {}
total_q = 0

for industry, groups in data.items():
    result[industry] = {}

    for k1, k2_dict in groups.items():
        # 발생형태 중 질문 있는 것만, 가중치 합계 기준 top 3
        ranked = sorted(
            [(k2, items) for k2, items in k2_dict.items() if items],
            key=lambda x: sum(i["가중치"] for i in x[1]),
            reverse=True
        )[:TOP_ACC]

        if not ranked:
            continue

        result[industry][k1] = {}
        for k2, items in ranked:
            # 가중치 내림차순 top 5
            top_items = sorted(items, key=lambda x: x["가중치"], reverse=True)[:TOP_Q]
            result[industry][k1][k2] = top_items
            total_q += len(top_items)

with open(OUT, "w", encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

# 결과 요약
print(f"총 질문 수: {total_q}개\n")
for industry, groups in result.items():
    print(f"[{industry}]")
    for k1, k2_dict in groups.items():
        n = sum(len(v) for v in k2_dict.values())
        accs = ", ".join(k2_dict.keys())
        print(f"  {k1:<35s}: {n:2d}개  ({accs})")
    print()

print(f"저장 완료 → checklist_filtered.json")
