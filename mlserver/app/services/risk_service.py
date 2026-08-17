"""
POST /predict/risk 핵심 로직.

predict.py(_safe_ind/_build_row/_top_k)를 그대로 재사용해 발생형태·재해정도를 예측하고,
shap.TreeExplainer로 예측 근거를 덧붙인다.

콜드스타트 위험점수(risk_score 등)는 여기서 계산하지 않는다 — 백엔드가 DB의
fn_coldstart_assess(workplace_id)를 직접 호출해서 처리한다 (2026-07-29, 백엔드와 합의).
"""

import logging

import numpy as np
import pandas as pd

from app.core import model_loader
from app.models.risk_schema import (
    DEFAULT_AGE_GROUP,
    DEFAULT_GENDER,
    DEFAULT_WORK_PERIOD,
    RiskPredictRequest,
    RiskPredictResponse,
    SeverityPrediction,
    TopRisk,
)
from app.services.mappings import map_industry, map_size_class

logger = logging.getLogger(__name__)

# predict.py / kosha_encodings.py는 risk_service 모듈이 import되는 시점(라우터 등록 시,
# FastAPI startup 이벤트보다 먼저)에 바로 필요하므로 여기서도 등록해준다.
model_loader.ensure_ml_source_on_path()
from predict import _build_row, _safe_ind, _top_k  # noqa: E402
from kosha_encodings import ACCIDENT_TYPE_INV, INJURY_INV  # noqa: E402


def _predict_with_shap(
    task_name: str, inv_map: dict[int, str], safe_industry: str, row: pd.DataFrame, top_k: int
) -> tuple[list[tuple[str, float]], dict[str, float]]:
    model = model_loader.get_model(task_name, safe_industry)
    if model is None:
        return [], {}

    X = row.reindex(columns=model.feature_name(), fill_value=0)
    probs = model.predict(X)[0]
    top = _top_k(probs, inv_map, top_k)

    shap_by_label: dict[str, float] = {}
    try:
        explainer = model_loader.get_explainer(task_name, safe_industry)
        shap_values = explainer.shap_values(X)
        label_to_enc = {v: k for k, v in inv_map.items()}
        for label, _ in top:
            enc = label_to_enc.get(label)
            if enc is None:
                continue
            if isinstance(shap_values, list):
                # 구버전 shap: 클래스별 리스트, 각 원소 shape=(n_samples, n_features)
                contrib = float(np.sum(shap_values[enc][0]))
            elif shap_values.ndim == 3:
                # 신버전 shap: shape=(n_samples, n_features, n_classes)
                contrib = float(np.sum(shap_values[0, :, enc]))
            else:
                # 이진/단일 출력 fallback: shape=(n_samples, n_features)
                contrib = float(np.sum(shap_values[0]))
            shap_by_label[label] = contrib
    except Exception:
        logger.warning("SHAP 계산 실패 (task=%s, industry=%s)", task_name, safe_industry, exc_info=True)

    return top, shap_by_label


def predict_risk(req: RiskPredictRequest) -> RiskPredictResponse:
    industry_model = map_industry(req.industry)
    size_class_model = map_size_class(req.size_class)

    safe = _safe_ind(industry_model)
    row = _build_row(
        대업종=industry_model,
        종업종=req.sub_industry,
        성별=req.gender or DEFAULT_GENDER,
        연령=req.age_group or DEFAULT_AGE_GROUP,
        근무기간=req.work_period or DEFAULT_WORK_PERIOD,
        규모=size_class_model,
        지역=req.region,
        건설공사금액=req.construction_amount,
        년도=req.year,
    )

    top_accident, accident_shap = _predict_with_shap("발생형태", ACCIDENT_TYPE_INV, safe, row, req.top_k)
    top_severity, _ = _predict_with_shap("재해정도_발생형태기반", INJURY_INV, safe, row, req.top_k)

    top_risks = [
        TopRisk(type=label, probability=prob, shap_value=accident_shap.get(label))
        for label, prob in top_accident
    ]
    severity_prediction = [SeverityPrediction(label=label, probability=prob) for label, prob in top_severity]

    return RiskPredictResponse(
        top_risks=top_risks,
        severity_prediction=severity_prediction,
    )
