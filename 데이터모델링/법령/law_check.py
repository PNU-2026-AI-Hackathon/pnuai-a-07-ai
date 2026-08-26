"""
checklist_with_law.csv에서 랜덤 질문 선택 → 매핑된 법령 텍스트 출력
무결성 확인용
"""

import csv, json, os, sys, random
sys.stdout.reconfigure(encoding='utf-8')

BASE     = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAW_JSON = os.path.join(BASE, "법령", "law_article.json")
CSV_PATH = os.path.join(BASE, "SIF_CSV", "체크리스트", "checklist_with_law.csv")

N = 5  # 랜덤 샘플 수

with open(LAW_JSON, encoding='utf-8') as f:
    laws = {str(l["article_id"]): l for l in json.load(f)}

with open(CSV_PATH, encoding='utf-8-sig') as f:
    rows = [r for r in csv.DictReader(f) if r.get("law_ref")]

samples = random.sample(rows, min(N, len(rows)))

for i, r in enumerate(samples, 1):
    print(f"\n{'='*60}")
    print(f"[{i}] 업종: {r['업종']} | 작업: {r['작업']} | 발생형태: {r['발생형태']}")
    print(f"질문: {r['질문']}")
    print(f"가중치: {r['가중치']}")
    print(f"law_ref: {r['law_ref']}")
    print(f"{'─'*60}")

    ids = [x.strip() for x in r["law_ref"].split(",") if x.strip()]
    for aid in ids:
        law = laws.get(aid)
        if not law:
            print(f"  ⚠ article_id={aid} → 존재하지 않음 (무결성 오류)")
            continue
        print(f"  [{aid}] {law['law_name']} {law['article_no']} {law.get('clause_no','') or ''}")
        print(f"  제목: {law['title']}")
        print(f"  내용: {(law.get('content') or '')[:200]}...")
        print()
