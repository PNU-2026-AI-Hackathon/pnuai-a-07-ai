"""
[1] law_article INSERT (article_id 완전 보존)
[2] law_article 인덱스 추가
[3] checklist_item INSERT (law_ref → article_id 참조)
→ output/checklist_item_insert.sql 하나로 출력

실행 순서: law_article → 인덱스 → checklist_item
article_id가 오염되면 law_ref 참조가 전부 깨지므로
OVERRIDING SYSTEM VALUE로 원본 ID 그대로 보존
"""

import csv, json, os, sys, re
sys.stdout.reconfigure(encoding='utf-8')

BASE       = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAW_JSON   = os.path.join(BASE, "법령", "law_article.json")
CSV_PATH   = os.path.join(BASE, "SIF_CSV", "체크리스트", "checklist_with_law.csv")
JSON_PATH  = os.path.join(BASE, "SIF_CSV", "checklist_filtered.json")
OUT_PATH   = os.path.join(BASE, "DB개선", "output", "checklist_item_insert.sql")

INDUSTRY_PREFIX = {"건설업": "CON", "제조업": "MFG"}


def esc(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("'", "''") + "'"


def load_json_index(path):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    index = {}
    for industry, groups in data.items():
        for work_raw, acc_dict in groups.items():
            work = re.sub(r'^\d+\.\d+\s+', '', work_raw)
            for acc_type, items in acc_dict.items():
                for item in items:
                    key = (industry, work, acc_type, item["질문"])
                    index[key] = item.get("기준_재해개요", [])
    return index


def gen_law_article(laws):
    """law_article INSERT 블록 생성 — article_id 원본 그대로 보존"""
    lines = [
        "-- ============================================================",
        "-- [1] law_article INSERT",
        "-- 출처: 법령/law_article.json (DB/ai_safework_full.sql COPY 블록에서 추출)",
        "-- article_id를 원본 그대로 보존 (OVERRIDING SYSTEM VALUE)",
        "-- ON CONFLICT DO NOTHING: 이미 존재하는 행은 건드리지 않음",
        "-- ⚠️  checklist_item.law_ref가 이 article_id를 참조하므로 절대 변경 금지",
        "-- ============================================================",
        "",
        "INSERT INTO public.law_article",
        "    (article_id, law_name, article_no, clause_no,",
        "     title, content, effective_date, source_url)",
        "OVERRIDING SYSTEM VALUE",
        "VALUES",
    ]

    rows = []
    for l in laws:
        rows.append(
            f"    ({l['article_id']}, {esc(l['law_name'])}, {esc(l['article_no'])}, {esc(l.get('clause_no'))},\n"
            f"     {esc(l.get('title'))}, {esc(l.get('content'))},\n"
            f"     {esc(l.get('effective_date'))}, {esc(l.get('source_url'))})"
        )

    lines.append(",\n".join(rows) + "\nON CONFLICT (article_id) DO NOTHING;")

    # 시퀀스를 max(article_id)+1로 리셋 (이후 자동증가 충돌 방지)
    max_id = max(l['article_id'] for l in laws)
    lines += [
        "",
        f"-- 시퀀스 리셋: 다음 INSERT가 기존 ID와 충돌하지 않도록",
        f"SELECT setval('public.law_article_article_id_seq', {max_id}, true);",
    ]
    return lines


def gen_indexes():
    return [
        "",
        "-- ============================================================",
        "-- [2] law_article 인덱스 추가",
        "-- ============================================================",
        "",
        "CREATE INDEX IF NOT EXISTS idx_law_article_name_no",
        "    ON public.law_article (law_name, article_no);",
        "",
        "CREATE INDEX IF NOT EXISTS idx_law_article_law_name",
        "    ON public.law_article (law_name);",
        "",
        "CREATE INDEX IF NOT EXISTS idx_law_article_title_trgm",
        "    ON public.law_article USING gin (title public.gin_trgm_ops);",
        "",
        "CREATE INDEX IF NOT EXISTS idx_law_article_content_trgm",
        "    ON public.law_article USING gin (content public.gin_trgm_ops);",
    ]


def gen_checklist(rows, json_index):
    lines = [
        "",
        "-- ============================================================",
        "-- [3] checklist_item INSERT",
        "-- 출처: SIF_CSV/체크리스트/checklist_with_law.csv",
        "--       SIF_CSV/checklist_filtered.json",
        "-- law_ref: 위 [1]에서 삽입한 article_id 참조",
        "-- ============================================================",
        "",
        "DELETE FROM public.checklist_item WHERE item_code LIKE 'SIF-%';",
        "",
        "INSERT INTO public.checklist_item",
        "    (item_code, category, question, description,",
        "     target_industry, risk_weight, law_ref, is_active)",
        "VALUES",
    ]

    counters = {}
    value_rows = []

    for r in rows:
        industry = r["업종"]
        work     = r["작업"]
        acc_type = r["발생형태"]
        question = r["질문"]
        weight   = float(r["가중치"])
        law_ref  = r.get("law_ref", "") or ""

        prefix = INDUSTRY_PREFIX.get(industry, "ETC")
        counters[prefix] = counters.get(prefix, 0) + 1
        item_code = f"SIF-{prefix}-{counters[prefix]:04d}"

        category = work[:50]

        key = (industry, work, acc_type, question)
        summaries = json_index.get(key, [])
        desc_parts = [f"[{acc_type}]"]
        if summaries:
            desc_parts.append(summaries[0][:200])
        description = " ".join(desc_parts)

        value_rows.append(
            f"    ({esc(item_code)}, {esc(category)}, {esc(question)}, {esc(description)},\n"
            f"     {esc(industry)}, {weight},\n"
            f"     {esc(law_ref if law_ref else None)}, TRUE)"
        )

    lines.append(",\n".join(value_rows) + ";")
    lines.append(f"\n-- 총 {len(value_rows)}개 체크리스트 항목 삽입")
    return lines


def main():
    with open(LAW_JSON, encoding='utf-8') as f:
        laws = json.load(f)

    json_index = load_json_index(JSON_PATH)

    with open(CSV_PATH, encoding='utf-8-sig') as f:
        csv_rows = list(csv.DictReader(f))

    today = __import__('datetime').date.today().isoformat()
    header = [
        "-- ============================================================",
        f"-- SafeWork AI — 법령 + 체크리스트 통합 INSERT",
        f"-- 생성일: {today}",
        "-- 실행 순서: [1] law_article → [2] 인덱스 → [3] checklist_item",
        "-- ============================================================",
    ]

    all_lines = (
        header
        + gen_law_article(laws)
        + gen_indexes()
        + gen_checklist(csv_rows, json_index)
    )

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding='utf-8') as f:
        f.write("\n".join(all_lines))

    size_mb = os.path.getsize(OUT_PATH) / 1024 / 1024
    print(f"완료: law_article {len(laws)}개 + checklist_item {len(csv_rows)}개")
    print(f"→ output/checklist_item_insert.sql ({size_mb:.1f} MB)")


if __name__ == "__main__":
    main()
