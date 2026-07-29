"""
checklist_filtered.json → checklist_filtered.csv
컬럼: 업종, 작업, 발생형태, 질문, 가중치
"""

import json, csv, os, sys, re
sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(os.path.abspath(__file__))
SRC  = os.path.join(BASE, "checklist_filtered.json")
OUT_DIR = os.path.join(BASE, "체크리스트")
os.makedirs(OUT_DIR, exist_ok=True)
DST  = os.path.join(OUT_DIR, "checklist_filtered.csv")

with open(SRC, encoding='utf-8') as f:
    data = json.load(f)

rows = []
for industry, groups in data.items():
    for work_type, acc_dict in groups.items():
        for acc_type, items in acc_dict.items():
            for item in items:
                clean_work = re.sub(r'^\d+\.\d+\s+', '', work_type)
                rows.append({
                    "업종":    industry,
                    "작업":    clean_work,
                    "발생형태": acc_type,
                    "질문":    item["질문"],
                    "가중치":  item["가중치"],
                })

with open(DST, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.DictWriter(f, fieldnames=["업종", "작업", "발생형태", "질문", "가중치"])
    w.writeheader()
    w.writerows(rows)

print(f"완료: {len(rows)}행 → checklist_filtered.csv")
