from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services import rag_service

router = APIRouter(tags=["rag"])


class LawSearchRequest(BaseModel):
    query: str = Field(..., description="자연어 질문 또는 키워드 (예: '추락 방지 조치')")
    top_k: int = Field(5, ge=1, le=20)


class LawSearchResult(BaseModel):
    chunk_id: int
    article_id: int
    law_name: str
    article_no: str
    title: str | None = None
    content: str
    score: float = Field(..., description="코사인 유사도 (1에 가까울수록 관련도 높음)")


@router.post("/rag/search-law", response_model=list[LawSearchResult])
def search_law(req: LawSearchRequest) -> list[dict]:
    """법령 조문 검색 (임베딩 기반). 첫 호출 시 FAISS 인덱스가 없으면 자동으로 구축한다(시간 걸림)."""
    return rag_service.search_law(req.query, req.top_k)
