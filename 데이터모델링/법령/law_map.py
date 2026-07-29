"""
체크리스트 질문 → 관련 law_article 매핑 (임베딩 + LLM)
1단계: 법령 2547개 임베딩 (최초 1회, 이후 캐시 재사용)
2단계: 질문 임베딩 → 코사인 유사도 → 상위 TOP_CAND개 후보
3단계: LLM이 후보 중 가장 관련된 조항 선택
결과: SIF_CSV/체크리스트/checklist_with_law.csv
"""

import csv, json, os, sys, time
sys.stdout.reconfigure(encoding='utf-8')
import numpy as np
import anthropic
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity

BASE         = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAW_JSON     = os.path.join(BASE, "법령", "law_article.json")
EMB_NPY      = os.path.join(BASE, "법령", "law_embeddings.npy")   # 캐시
SRC_CSV      = os.path.join(BASE, "SIF_CSV", "체크리스트", "checklist_filtered.csv")
OUT_CSV      = os.path.join(BASE, "SIF_CSV", "체크리스트", "checklist_with_law.csv")

MODEL_NAME   = "paraphrase-multilingual-MiniLM-L12-v2"  # 한국어 지원, ~500MB
TOP_CAND     = 20   # LLM에 넘길 후보 수
BATCH        = 5    # 한번에 처리할 질문 수


def law_text(law: dict) -> str:
    """법령 조항 → 임베딩용 텍스트 (제목 + 조번호 + 내용 앞부분)"""
    parts = [
        law.get("law_name", ""),
        law.get("article_no", ""),
        law.get("clause_no", "") or "",
        law.get("title", ""),
        (law.get("content", "") or "")[:500],
    ]
    return " ".join(p for p in parts if p).strip()


def load_laws_and_embeddings(model: SentenceTransformer):
    with open(LAW_JSON, encoding='utf-8') as f:
        laws = json.load(f)

    if os.path.exists(EMB_NPY):
        print("캐시된 임베딩 로드 중...", flush=True)
        embeddings = np.load(EMB_NPY)
        if embeddings.shape[0] == len(laws):
            print(f"  완료: {len(laws)}개 조항")
            return laws, embeddings

    print(f"법령 {len(laws)}개 임베딩 생성 중 (최초 1회)...", flush=True)
    texts = [law_text(l) for l in laws]
    embeddings = model.encode(texts, batch_size=64, show_progress_bar=True,
                               normalize_embeddings=True)
    np.save(EMB_NPY, embeddings)
    print(f"  완료 → 법령/law_embeddings.npy")
    return laws, embeddings


def get_candidates(laws, embeddings, work_type: str, acc_type: str, question: str, model: SentenceTransformer):
    """작업 + 발생형태 + 질문 합쳐서 임베딩 → 코사인 유사도 → 상위 TOP_CAND 후보"""
    query = f"{work_type} {acc_type} {question}"
    q_emb = model.encode([query], normalize_embeddings=True)
    scores = cosine_similarity(q_emb, embeddings)[0]  # shape: (2547,)
    top_idx = np.argsort(scores)[::-1][:TOP_CAND]
    return [(laws[i], float(scores[i])) for i in top_idx]


def call_llm(client, questions_with_candidates: list):
    """질문 배치 + 후보 법령 → LLM → article_id 반환"""
    lines = ["다음 체크리스트 질문들에 가장 관련된 산업안전보건 법령 조항을 각각 골라주세요.\n"]

    for i, (work, q, acc, candidates) in enumerate(questions_with_candidates):
        lines.append(f"[질문 {i}] 작업: {work} | 발생형태: {acc}")
        lines.append(f"  {q}")
        lines.append("후보 조항 (코사인 유사도 순):")
        for law, score in candidates:
            lines.append(
                f"  ID:{law['article_id']} [{score:.3f}] "
                f"{law['law_name']} {law['article_no']} "
                f"{law.get('clause_no','') or ''} - {law['title']}"
            )
        lines.append("")

    lines += [
        "각 질문에 가장 관련된 조항 ID를 1~3개 선택하세요.",
        "관련 없으면 빈 배열.",
        "JSON으로만 응답 (다른 텍스트 없이):",
        '[{"idx": 0, "article_ids": [38, 42]}, ...]',
    ]

    msg = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=4096,
        temperature=0,
        messages=[{"role": "user", "content": "\n".join(lines)}],
    )
    text = msg.content[0].text.strip()
    if "```" in text:
        text = text.split("```")[1]
        if text.startswith("json"):
            text = text[4:]
    s, e = text.find("["), text.rfind("]")
    if s != -1 and e != -1:
        text = text[s:e+1]
    return json.loads(text)


def main():
    print(f"모델 로드: {MODEL_NAME}")
    model = SentenceTransformer(MODEL_NAME)

    laws, embeddings = load_laws_and_embeddings(model)
    valid_ids = {str(l["article_id"]) for l in laws}  # 무결성 검증용
    client = anthropic.Anthropic()

    with open(SRC_CSV, encoding='utf-8-sig') as f:
        rows = list(csv.DictReader(f))

    print(f"\n총 {len(rows)}개 질문 처리 시작\n")
    results = {}  # 질문 → "id1,id2"

    for start in range(0, len(rows), BATCH):
        batch = rows[start:start + BATCH]
        end_i = min(start + BATCH, len(rows))
        print(f"  [{start+1}~{end_i}/{len(rows)}] ...", end=" ", flush=True)

        batch_data = []
        for r in batch:
            q    = r["질문"]
            acc  = r["발생형태"]
            work = r["작업"]
            candidates = get_candidates(laws, embeddings, work, acc, q, model)
            batch_data.append((work, q, acc, candidates))

        try:
            items = call_llm(client, batch_data)
            for item in items:
                idx = item.get("idx", -1)
                if 0 <= idx < len(batch):
                    q = batch[idx]["질문"]
                    ids = [str(i) for i in item.get("article_ids", []) if str(i) in valid_ids]
                    results[q] = ",".join(ids)
                    if not ids:
                        results[q] = ""
            print("완료")
        except Exception as e:
            print(f"오류: {e}")
            for r in batch:
                results[r["질문"]] = ""
        time.sleep(0.3)

    with open(OUT_CSV, "w", encoding='utf-8-sig', newline="") as f:
        fields = ["업종", "작업", "발생형태", "질문", "가중치", "law_ref"]
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for r in rows:
            r["law_ref"] = results.get(r["질문"], "")
            w.writerow(r)

    mapped = sum(1 for v in results.values() if v)
    print(f"\n완료: {mapped}/{len(rows)}개 질문 매핑됨")
    print(f"→ SIF_CSV/체크리스트/checklist_with_law.csv")


if __name__ == "__main__":
    main()
