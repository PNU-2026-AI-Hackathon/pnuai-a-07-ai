from fastapi import APIRouter, HTTPException

from app.data.checklist_items import CHECKLIST_ITEMS
from app.models.risk_schema import RiskPredictRequest, RiskPredictResponse
from app.services import risk_service
from app.services.mappings import UnmappedValueError
from app.services.risk_service import InvalidChecklistItemError

router = APIRouter(tags=["risk"])


@router.post("/predict/risk", response_model=RiskPredictResponse)
def predict_risk(req: RiskPredictRequest) -> RiskPredictResponse:
    try:
        return risk_service.predict_risk(req)
    except UnmappedValueError as e:
        # 프런트/백엔드가 code_industry·code_size_class 마스터에 없는 값을 보낸 경우
        raise HTTPException(status_code=400, detail=str(e)) from e
    except InvalidChecklistItemError as e:
        # v_ref_checklist에 없는 item_code (예: 구 20문항 코드) — 조용히 무시하면
        # 위험도가 실제보다 낮게 계산될 수 있어 명시적으로 거부한다
        raise HTTPException(status_code=400, detail=str(e)) from e


@router.get("/predict/checklist-items")
def list_checklist_items() -> list[dict]:
    """checklist_scores에 쓸 수 있는 item_code 목록.

    ⚠️ 2026-07-28 DB 변경공지로 체크리스트가 835문항(v_ref_checklist)으로 전면
    교체됐지만, 이 스냅샷은 아직 구 20문항 그대로다 — PG 라이브 연결 또는 새
    데이터 export 전까지 신규 item_code는 여기 안 뜨고, /predict/risk도 신규
    코드를 보내면 400으로 거부한다.
    """
    return CHECKLIST_ITEMS
