"""
POST /analyze/cases (유사 재해사례) — sif_case(6,032건) 임베딩 검색.

rag_service.py와 같은 임베딩 인프라(모델 로딩·인코딩)를 재사용한다. sif_case.industry_div는
실데이터 기준 "건설업"/"제조업등" 두 값뿐이라(2026-07-28 확인), industry 요청값을 이 둘 중
하나로 매핑한 뒤 그 안에서만 유사도 순위를 매긴다.
"""

import logging
from collections import Counter
from functools import lru_cache
from pathlib import Path

import faiss
import pandas as pd

from app.core.config import settings
from app.services.rag_service import _encode

logger = logging.getLogger(__name__)

_INDEX_FILE = "sif_case.index"
_META_FILE = "sif_case_meta.csv"

# sif_case.industry_div 실측값은 "건설업"/"제조업등" 두 개뿐 (2026-07-28 확인).
# 그 외 업종(운수창고통신업 등)은 SIF 데이터 자체가 없어 "제조업등"으로 폴백.
_UNAVAILABLE_DIVS = "분류 불가", "분류불가", "미상"


def _map_industry_div(industry: str) -> str:
    return "건설업" if "건설" in industry else "제조업등"


def _load_sif_cases() -> pd.DataFrame:
    return pd.read_csv(Path(settings.REFERENCE_DATA_DIR) / "sif_case.tsv", sep="\t", na_values=["\\N"])


def build_sif_index(force: bool = False) -> None:
    """sif_case 전체(accident_summary 기준)를 임베딩해서 FAISS 인덱스를 캐싱한다."""
    index_path = settings.FAISS_INDEX_DIR / _INDEX_FILE
    meta_path = settings.FAISS_INDEX_DIR / _META_FILE

    if index_path.exists() and meta_path.exists() and not force:
        logger.info("기존 sif_case FAISS 인덱스 재사용: %s", index_path)
        return

    settings.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)

    df = _load_sif_cases()
    texts = df["accident_summary"].fillna("").tolist()
    embeddings = _encode(texts)

    dim = embeddings.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(embeddings)

    faiss.write_index(index, str(index_path))
    df.to_csv(meta_path, index=False)
    logger.info("sif_case FAISS 인덱스 구축 완료: %d건, dim=%d", len(df), dim)
    _load_index.cache_clear()


@lru_cache(maxsize=1)
def _load_index() -> tuple[faiss.Index, pd.DataFrame]:
    index_path = settings.FAISS_INDEX_DIR / _INDEX_FILE
    meta_path = settings.FAISS_INDEX_DIR / _META_FILE
    if not index_path.exists() or not meta_path.exists():
        build_sif_index()
    index = faiss.read_index(str(index_path))
    meta = pd.read_csv(meta_path)
    return index, meta


def analyze_similar_cases(
    industry: str,
    sub_industry: str,
    top_n: int = 5,
    query_context: str | None = None,
) -> dict:
    index, meta = _load_index()
    target_div = _map_industry_div(industry)

    query = query_context.strip() if query_context and query_context.strip() else f"{sub_industry} 관련 사고"
    query_vec = _encode([query])

    # industry_div로 걸러야 해서, 필터링 후에도 top_n이 채워지도록 넉넉히 오버페치한다.
    pool_size = min(len(meta), max(top_n * 20, 100))
    scores, idxs = index.search(query_vec, pool_size)

    matched = []
    for score, idx in zip(scores[0], idxs[0]):
        if idx == -1:
            continue
        row = meta.iloc[int(idx)]
        if row["industry_div"] != target_div:
            continue
        matched.append((score, row))
        if len(matched) >= top_n:
            break

    similar_cases = [
        {
            "sif_id": int(row["sif_id"]),
            "summary": row["accident_summary"],
            "countermeasure": row.get("countermeasure"),
            "score": float(score),
        }
        for score, row in matched
    ]

    keyword_counts = Counter(
        row["causal_object"]
        for _, row in matched
        if pd.notna(row["causal_object"]) and row["causal_object"] not in _UNAVAILABLE_DIVS
    )
    top_keywords = [kw for kw, _ in keyword_counts.most_common(5)]

    return {"top_keywords": top_keywords, "similar_cases": similar_cases}
