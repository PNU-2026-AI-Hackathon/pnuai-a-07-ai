from fastapi import APIRouter, HTTPException

from app.data.checklist_items import CHECKLIST_ITEMS
from app.models.risk_schema import RiskPredictRequest, RiskPredictResponse
from app.services import risk_service
from app.services.mappings import UnmappedValueError

router = APIRouter(tags=["risk"])


@router.post("/predict/risk", response_model=RiskPredictResponse)
def predict_risk(req: RiskPredictRequest) -> RiskPredictResponse:
    try:
        return risk_service.predict_risk(req)
    except UnmappedValueError as e:
        # 프런트/백엔드가 code_industry·code_size_class 마스터에 없는 값을 보낸 경우
        raise HTTPException(status_code=400, detail=str(e)) from e


@router.get("/predict/checklist-items")
def list_checklist_items() -> list[dict]:
    """checklist_scores에 쓸 수 있는 item_code 20개 목록 (건설업 10 + 제조업 10)."""
    return CHECKLIST_ITEMS
