"""
패턴 미분류 건만 LLM으로 분류 → normalized CSV 업데이트
대상: 정규화/*_미분류.csv (건설업 155 + 제조업 161 = 316건)
"""

import csv, json, time, os, sys
sys.stdout.reconfigure(encoding='utf-8')
import anthropic

BASE    = os.path.dirname(os.path.abspath(__file__))
NOR_DIR = os.path.join(BASE, "정규화")

TARGETS = [
    (os.path.join(NOR_DIR, "건설업"), "건설업_normalized.csv",  "건설업_normalized_미분류.csv"),
    (os.path.join(NOR_DIR, "제조업"), "제조업_normalized.csv",  "제조업_normalized_미분류.csv"),
]

ACCIDENT_LABELS = [
    "감전", "기타", "깔림.뒤집힘", "끼임", "넘어짐",
    "떨어짐", "무너짐", "물체에맞음", "부딪힘",
    "빠짐익사", "사업장내교통사고", "산소결핍",
    "이상온도물체접촉", "절단베임찔림",
    "폭발파열", "화재", "화학물질누출접촉", "분류불가"
]

SYSTEM_PROMPT = f"""당신은 산업재해 분류 전문가입니다.
재해개요 텍스트를 읽고 발생형태를 분류하세요.

[허용값]
{', '.join(ACCIDENT_LABELS)}

규칙:
- 반드시 허용값만 사용
- 판단 불가 시 "분류불가"
- JSON 배열로만 응답, 입력 순서 그대로

[{{"idx": 0, "발생형태": "떨어짐"}}, ...]"""

BATCH = 20


def classify_batch(client, batch):
    content = json.dumps(
        [{"idx": r["idx"], "재해개요": r["text"][:500]} for r in batch],
        ensure_ascii=False
    )
    msg = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        temperature=0,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": content}]
    )
    text = msg.content[0].text.strip()
    if "```" in text:
        text = text.split("```")[1]
        if text.startswith("json"):
            text = text[4:]
    return {r["idx"]: r.get("발생형태", "분류불가") for r in json.loads(text)}


def process(subdir, norm_file, miss_file):
    norm_path = os.path.join(subdir, norm_file)
    miss_path = os.path.join(subdir, miss_file)

    if not os.path.exists(miss_path):
        print(f"  미분류 파일 없음: {miss_file}")
        return

    # 미분류 로드
    with open(miss_path, encoding="utf-8-sig") as f:
        miss_rows = list(csv.DictReader(f))

    print(f"\n{miss_file}: {len(miss_rows)}건 LLM 분류 시작")

    client = anthropic.Anthropic()
    results = {}

    batch_input = [{"idx": i, "text": r.get("accident_summary", "")}
                   for i, r in enumerate(miss_rows)]

    for start in range(0, len(batch_input), BATCH):
        batch = batch_input[start:start + BATCH]
        end = start + len(batch)
        print(f"  [{start+1}~{end}/{len(batch_input)}] ...", end=" ", flush=True)
        try:
            # LLM엔 항상 0부터 보내고, 돌아온 결과를 global idx로 역매핑
            local_batch = [{"idx": j, "text": b["text"]} for j, b in enumerate(batch)]
            local_results = classify_batch(client, local_batch)
            for j, b in enumerate(batch):
                results[b["idx"]] = local_results.get(j, "분류불가")
            print("완료")
        except Exception as e:
            print(f"오류: {e}")
            for b in batch:
                results[b["idx"]] = "분류불가"
        time.sleep(0.3)

    # miss_rows 순서(=global idx)로 normalized CSV 업데이트
    # accident_summary 중복 가능성 대비해 idx 기반으로 매핑
    # 전체 normalized CSV 업데이트 (miss_rows 순서 = global idx 기준)
    with open(norm_path, encoding="utf-8-sig") as f:
        all_rows = list(csv.DictReader(f))
        fields = list(all_rows[0].keys())

    miss_lookup = {r.get("accident_summary", ""): (i, results.get(i, "분류불가"))
                   for i, r in enumerate(miss_rows)}

    updated = 0
    for r in all_rows:
        if r.get("accident_type_mapped", "") in ("", "분류불가"):
            key = r.get("accident_summary", "")
            if key in miss_lookup:
                r["accident_type_mapped"] = miss_lookup[key][1]
                updated += 1

    with open(norm_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(all_rows)

    from collections import Counter
    cnt = Counter(results.values())
    print(f"  업데이트: {updated}행")
    print(f"  분류 결과:")
    for k, v in cnt.most_common():
        print(f"    {v:3d}  {k}")


if __name__ == "__main__":
    for subdir, norm_file, miss_file in TARGETS:
        process(subdir, norm_file, miss_file)
    print("\n완료. 정규화 CSV 업데이트됨.")
