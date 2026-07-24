"""
POST /predict/risk 핵심 로직.

두 부분으로 구성된다.
1) 콜드스타트 위험점수 — DB의 fn_coldstart_score() SQL 로직(베이스라인 백분위 60점 +
   체크리스트 가감점 40점)을 Python으로 그대로 재현. coldstart_baseline 스냅샷
   (app/data/coldstart_baseline.tsv, 609행)을 사용하며, PG 접속정보가 확정되면
   _load_baseline()만 라이브 쿼리로 바꾸면 된다.
2) LightGBM top_risks — predict.py(_safe_ind/_build_row/_top_k)를 그대로 재사용해
   발생형태·재해정도를 예측하고, shap.TreeExplainer로 예측 근거를 덧붙인다.
"""

import logging
from functools import lru_cache
from pathlib import Path

import numpy as np
import pandas as pd
import shap

from app.core import model_loader
from app.core.config import settings
from app.data.checklist_items import CHECKLIST_ITEM_BY_CODE
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


# ── 1) 콜드스타트 위험점수 (fn_coldstart_score 재현) ──────────────────────

@lru_cache(maxsize=1)
def _load_baseline() -> pd.DataFrame:
    path = Path(settings.REFERENCE_DATA_DIR) / "coldstart_baseline.tsv"
    return pd.read_csv(path, sep="\t")


def _match_baseline(industry: str, size_class: str, region: str) -> tuple[float, str | None, str]:
    df = _load_baseline()

    exact = df[(df.industry == industry) & (df.size_class == size_class) & (df.region == region)]
    if not exact.empty:
        row = exact.iloc[0]
        return float(row.serious_ratio), row.top_accident_type, "EXACT"

    ind_size = df[(df.industry == industry) & (df.size_class == size_class)]
    if not ind_size.empty:
        top = ind_size.top_accident_type.mode()
        return float(ind_size.serious_ratio.mean()), (top.iloc[0] if not top.empty else None), "INDUSTRY_SIZE"

    ind = df[df.industry == industry]
    if not ind.empty:
        top = ind.top_accident_type.mode()
        return float(ind.serious_ratio.mean()), (top.iloc[0] if not top.empty else None), "INDUSTRY"

    return 0.0, None, "NONE"


def _base_component(serious_ratio: float) -> float:
    df = _load_baseline()
    if df.empty:
        return 0.0
    percentile = (df.serious_ratio <= serious_ratio).sum() / len(df)
    return round(percentile * 60, 2)


def _checklist_component(checklist_scores: dict[str, bool]) -> float:
    total = 0.0
    for item_code, answered_yes in checklist_scores.items():
        if answered_yes:
            continue  # YES(안전조치 완료) → 가점 없음
        item = CHECKLIST_ITEM_BY_CODE.get(item_code)
        if item is None:
            logger.warning("알 수 없는 checklist item_code: %s (무시)", item_code)
            continue
        total += item["risk_weight"] * (2 if item["is_critical"] else 1)
    return round(min(40.0, total), 2)


def compute_coldstart_score(industry: str, size_class: str, region: str, checklist_scores: dict[str, bool]) -> dict:
    serious_ratio, top_accident_type, match_level = _match_baseline(industry, size_class, region)
    base = _base_component(serious_ratio)
    checklist = _checklist_component(checklist_scores)
    score = round(min(100.0, max(0.0, base + checklist)), 2)

    if score >= 75:
        grade = "CRITICAL"
    elif score >= 50:
        grade = "HIGH"
    elif score >= 25:
        grade = "MEDIUM"
    else:
        grade = "LOW"

    return {
        "risk_score": score,
        "risk_grade": grade,
        "base_component": base,
        "checklist_component": checklist,
        "match_level": match_level,
        "top_accident_type": top_accident_type,
    }


# ── 2) LightGBM 예측 + SHAP ────────────────────────────────────────────

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
        explainer = shap.TreeExplainer(model)
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


# ── 3) 진입점 ───────────────────────────────────────────────────────────

def predict_risk(req: RiskPredictRequest) -> RiskPredictResponse:
    industry_model = map_industry(req.industry)
    size_class_model = map_size_class(req.size_class)

    coldstart = compute_coldstart_score(industry_model, size_class_model, req.region, req.checklist_scores)

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
        risk_score=coldstart["risk_score"],
        risk_grade=coldstart["risk_grade"],
        base_component=coldstart["base_component"],
        checklist_component=coldstart["checklist_component"],
        match_level=coldstart["match_level"],
        top_risks=top_risks,
        severity_prediction=severity_prediction,
    )
