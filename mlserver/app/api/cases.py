from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services import case_service

router = APIRouter(tags=["cases"])


class AnalyzeCasesRequest(BaseModel):
    industry: str = Field(..., description="code_industry.industry (예: 제조업)")
    sub_industry: str = Field(..., description="KOSHA 종업종 문자열")
    top_n: int = Field(5, ge=1, le=20)
    query_context: str | None = Field(None, description="진단 결과·미비 항목을 합친 검색 문맥")


class SimilarCase(BaseModel):
    sif_id: int
    summary: str
    countermeasure: str | None = None
    score: float = Field(..., description="코사인 유사도 (1에 가까울수록 관련도 높음)")


class AnalyzeCasesResponse(BaseModel):
    top_keywords: list[str]
    similar_cases: list[SimilarCase]


@router.post("/analyze/cases", response_model=AnalyzeCasesResponse)
def analyze_cases(req: AnalyzeCasesRequest) -> dict:
    """유사 재해사례 검색 (sif_case 6,032건, 임베딩 기반).

    sif_case는 실데이터상 건설업/제조업만 있어서, 다른 업종으로 요청해도 제조업 사례로
    폴백된다 (아래 README 참고). 첫 호출 시 인덱스가 없으면 자동으로 구축한다.
    """
    return case_service.analyze_similar_cases(
        req.industry, req.sub_industry, req.top_n, req.query_context
    )
