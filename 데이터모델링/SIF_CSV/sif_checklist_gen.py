"""
공종(중분류) × 발생형태 그룹별 LLM 체크리스트 생성
- 가중치 = 해당 질문을 지지하는 실제 케이스 수 (len(기준_재해개요))
- 할루시네이션 방지: LLM이 케이스 인덱스 인용 → 실제 원본 텍스트 매핑
결과: SIF_CSV/checklist_raw.json
"""

import csv, json, os, sys, time
sys.stdout.reconfigure(encoding='utf-8')
import anthropic

BASE    = os.path.dirname(os.path.abspath(__file__))
NOR_DIR = os.path.join(BASE, "정규화")
OUT     = os.path.join(BASE, "checklist_raw.json")

CON_PATH = os.path.join(NOR_DIR, "건설업", "건설업_normalized.csv")
MFG_PATH = os.path.join(NOR_DIR, "제조업", "제조업_normalized.csv")

MIN_CASES = 3   # 이 미만 조합은 스킵
MAX_CASES = 15  # LLM에 넘기는 최대 케이스 수 (토큰 절약)


# ── 유틸 ──────────────────────────────────────────────────────

def load(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def group_by(rows, key1, key2):
    """(key1, key2) 조합으로 그룹핑"""
    groups = {}
    for r in rows:
        k1 = r.get(key1, "").strip()
        k2 = r.get(key2, "").strip()
        if not k1 or not k2 or k2 in ("분류불가", ""):
            continue
        groups.setdefault(k1, {}).setdefault(k2, []).append(r)
    return groups


def build_prompt(k1, k2, cases):
    lines = [
        f"당신은 산업안전 전문가입니다.",
        f"아래는 [{k1}] 작업 중 [{k2}] 사고가 발생한 실제 산재 케이스입니다.\n",
    ]
    for i, r in enumerate(cases):
        summary  = r.get("accident_summary", "").strip()[:300]
        obj      = r.get("causal_object", "").strip()
        factor   = r.get("causal_factor", "").strip()[:200]
        measure  = r.get("countermeasure", "").strip()[:200]
        lines.append(f"[{i}] 재해개요: {summary}")
        if obj:     lines.append(f"    기인물: {obj}")
        if factor:  lines.append(f"    재해유발요인: {factor}")
        if measure: lines.append(f"    위험성감소대책: {measure}")
        lines.append("")

    lines += [
        "위 케이스들에서 공통 위험요인을 파악하고 안전 점검 체크리스트 질문을 생성하세요.",
        "각 질문은 반드시 위 케이스를 근거로 하며, 어떤 케이스 번호([N])가 근거인지 명시하세요.",
        "근거 없이 지어낸 질문은 절대 금지.",
        "",
        "JSON 배열로만 응답 (다른 텍스트 없이):",
        '[{"질문": "...", "근거_케이스": [0, 2, 5]}, ...]',
    ]
    return "\n".join(lines)


def clean_json(text: str) -> str:
    """LLM 응답에서 JSON 추출 및 흔한 오류 정리"""
    if "```" in text:
        text = text.split("```")[1]
        if text.startswith("json"):
            text = text[4:]
    text = text.strip()
    # 배열 부분만 추출
    start = text.find("[")
    end   = text.rfind("]")
    if start != -1 and end != -1:
        text = text[start:end + 1]
    return text


def call_llm(client, prompt):
    msg = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=4096,
        temperature=0,
        messages=[{"role": "user", "content": prompt}]
    )
    text = clean_json(msg.content[0].text)
    return json.loads(text)


def process_group(client, k1, k2, rows):
    cases = rows[:MAX_CASES]
    prompt = build_prompt(k1, k2, cases)

    try:
        items = call_llm(client, prompt)
    except Exception as e:
        print(f"    오류: {e}")
        return []

    result = []
    for item in items:
        indices  = item.get("근거_케이스", [])
        question = item.get("질문", "").strip()
        if not question or not indices:
            continue

        # 인덱스 → 실제 원본 텍스트 매핑
        ref_summaries = []
        ref_measures  = []
        for idx in indices:
            if 0 <= idx < len(cases):
                s = cases[idx].get("accident_summary", "").strip()
                m = cases[idx].get("countermeasure", "").strip()
                if s: ref_summaries.append(s)
                if m: ref_measures.append(m)

        result.append({
            "질문":             question,
            "가중치":           len(ref_summaries),
            "기준_재해개요":    ref_summaries,
            "기준_위험성감소대책": ref_measures,
        })

    # 가중치 내림차순 정렬
    result.sort(key=lambda x: x["가중치"], reverse=True)
    return result


# ── 메인 ──────────────────────────────────────────────────────

def run(industry_label, rows, key1, key2):
    groups = group_by(rows, key1, key2)
    client = anthropic.Anthropic()
    output = {}

    total_groups = sum(
        1 for k1_groups in groups.values()
        for cases in k1_groups.values()
        if len(cases) >= MIN_CASES
    )
    done = 0

    for k1, k2_dict in sorted(groups.items()):
        output[k1] = {}
        for k2, cases in sorted(k2_dict.items(), key=lambda x: -len(x[1])):
            if len(cases) < MIN_CASES:
                continue
            done += 1
            print(f"  [{done}/{total_groups}] {k1} × {k2}  ({len(cases)}건) ...", end=" ", flush=True)
            items = process_group(client, k1, k2, cases)
            output[k1][k2] = items
            print(f"{len(items)}개 질문")
            time.sleep(0.3)

    return output


if __name__ == "__main__":
    con_rows = load(CON_PATH)
    mfg_rows = load(MFG_PATH)

    result = {}

    print(f"\n{'='*55}")
    print(f"  건설업 체크리스트 생성 (공종 × 발생형태)")
    print(f"{'='*55}")
    result["건설업"] = run("건설업", con_rows, "jak_up_myung", "accident_type_mapped")

    print(f"\n{'='*55}")
    print(f"  제조업 체크리스트 생성 (중분류 × 발생형태)")
    print(f"{'='*55}")
    result["제조업"] = run("제조업", mfg_rows, "high_risk_situation", "accident_type_mapped")

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"\n완료 → {OUT}")
