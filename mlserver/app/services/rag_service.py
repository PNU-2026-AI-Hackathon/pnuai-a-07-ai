"""
법령(law_chunk) 임베딩 + FAISS 검색.

OpenAI API 대신 sentence-transformers 로컬 모델 사용 (2026-07-28 팀 결정 — API 키/카드 없이
바로 시작 가능, 호출 제한 없음). 법령 텍스트가 전부 한국어라 한국어 특화 모델 채택
(config.py의 EMBEDDING_MODEL_NAME).

law_chunk/law_article은 아직 PG 라이브 연결 전이라 SQL 덤프 스냅샷(app/data/*.tsv)을 쓴다.
인덱스는 한 번 만들면 app/data/faiss/에 캐싱해서 재시작할 때마다 다시 임베딩하지 않는다 —
스냅샷이나 임베딩 모델이 바뀌면 build_law_index(force=True)로 재생성해야 한다.
"""

import logging
from functools import lru_cache
from pathlib import Path

import faiss
import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer

from app.core.config import settings

logger = logging.getLogger(__name__)

_INDEX_FILE = "law_chunk.index"
_META_FILE = "law_chunk_meta.csv"


@lru_cache(maxsize=1)
def _get_model() -> SentenceTransformer:
    logger.info("임베딩 모델 로딩: %s", settings.EMBEDDING_MODEL_NAME)
    return SentenceTransformer(settings.EMBEDDING_MODEL_NAME)


def _load_law_chunks() -> pd.DataFrame:
    chunk = pd.read_csv(Path(settings.REFERENCE_DATA_DIR) / "law_chunk.tsv", sep="\t", na_values=["\\N"])
    article = pd.read_csv(Path(settings.REFERENCE_DATA_DIR) / "law_article.tsv", sep="\t", na_values=["\\N"])
    return chunk.merge(
        article[["article_id", "law_name", "article_no", "clause_no", "title"]],
        on="article_id",
        how="left",
    )


def _encode(texts: list[str]) -> np.ndarray:
    model = _get_model()
    embeddings = model.encode(texts, normalize_embeddings=True, show_progress_bar=len(texts) > 1, batch_size=32)
    return np.asarray(embeddings, dtype="float32")


def build_law_index(force: bool = False) -> None:
    """law_chunk 전체를 임베딩해서 FAISS 인덱스를 만들고 디스크에 캐싱한다."""
    index_path = settings.FAISS_INDEX_DIR / _INDEX_FILE
    meta_path = settings.FAISS_INDEX_DIR / _META_FILE

    if index_path.exists() and meta_path.exists() and not force:
        logger.info("기존 FAISS 인덱스 재사용: %s", index_path)
        return

    settings.FAISS_INDEX_DIR.mkdir(parents=True, exist_ok=True)

    df = _load_law_chunks()
    # law_chunk.content에 이미 "[법령명 조문(제목)]" 헤더가 붙어있어서 그대로 임베딩에 사용.
    texts = df["content"].fillna("").tolist()
    embeddings = _encode(texts)

    dim = embeddings.shape[1]
    index = faiss.IndexFlatIP(dim)  # normalize_embeddings=True 했으니 내적 = 코사인 유사도
    index.add(embeddings)

    faiss.write_index(index, str(index_path))
    df.to_csv(meta_path, index=False)
    logger.info("FAISS 인덱스 구축 완료: %d개 청크, dim=%d", len(df), dim)
    _load_index.cache_clear()


@lru_cache(maxsize=1)
def _load_index() -> tuple[faiss.Index, pd.DataFrame]:
    index_path = settings.FAISS_INDEX_DIR / _INDEX_FILE
    meta_path = settings.FAISS_INDEX_DIR / _META_FILE
    if not index_path.exists() or not meta_path.exists():
        build_law_index()
    index = faiss.read_index(str(index_path))
    meta = pd.read_csv(meta_path)
    return index, meta


def search_law(query: str, top_k: int = 5) -> list[dict]:
    index, meta = _load_index()
    query_vec = _encode([query])
    scores, idxs = index.search(query_vec, top_k)

    results = []
    for score, idx in zip(scores[0], idxs[0]):
        if idx == -1:
            continue
        row = meta.iloc[int(idx)]
        results.append(
            {
                "chunk_id": int(row["chunk_id"]),
                "article_id": int(row["article_id"]),
                "law_name": row["law_name"],
                "article_no": row["article_no"],
                "title": row.get("title"),
                "content": row["content"],
                "score": float(score),
            }
        )
    return results
