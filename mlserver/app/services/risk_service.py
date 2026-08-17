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
import shap

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

    top_risks = _blend_checklist_signals(top_accident, accident_shap, req)
    severity_prediction = [SeverityPrediction(label=label, probability=prob) for label, prob in top_severity]

    return RiskPredictResponse(
        top_risks=top_risks,
        severity_prediction=severity_prediction,
    )


def _blend_checklist_signals(
    model_risks: list[tuple[str, float]],
    accident_shap: dict[str, float],
    req: RiskPredictRequest,
) -> list[TopRisk]:
    """모델 분포와 실제 현장 미비 신호를 결합해 설명 가능한 위험순위를 만든다.

    새 현장 필드는 기존 학습모델의 특성이 아니므로 학습 확률이라고 주장하지 않는다.
    모델·체크리스트·현장 상세정보를 위험 신호로 정규화하며, 통계적 발생확률로
    사용하지 않는다.
    """
    profile_scores = _profile_risk_signals(req)
    has_checklist = bool(req.risk_signals)
    model_ratio = 0.55 if has_checklist else 0.85
    checklist_ratio = 0.35 if has_checklist else 0.0
    profile_ratio = 0.10 if has_checklist else 0.15

    scores: dict[str, float] = {label: prob * model_ratio for label, prob in model_risks}
    counts: dict[str, int] = {}
    total_weight = sum(signal.weight for signal in req.risk_signals)
    if total_weight <= 0:
        total_weight = float(sum(max(signal.deficient_count, 1) for signal in req.risk_signals))

    for signal in req.risk_signals:
        signal_weight = signal.weight if signal.weight > 0 else float(max(signal.deficient_count, 1))
        scores[signal.category] = scores.get(signal.category, 0.0) + checklist_ratio * signal_weight / total_weight
        counts[signal.category] = signal.deficient_count

    profile_total = sum(profile_scores.values())
    if profile_total > 0:
        for category, score in profile_scores.items():
            scores[category] = scores.get(category, 0.0) + profile_ratio * score / profile_total

    total = sum(scores.values()) or 1.0
    ranked = sorted(scores.items(), key=lambda item: item[1], reverse=True)[: req.top_k]
    return [
        TopRisk(
            type=label,
            probability=score / total,
            shap_value=accident_shap.get(label),
            basis=_risk_basis(label, counts, profile_scores),
        )
        for label, score in ranked
    ]


def _profile_risk_signals(req: RiskPredictRequest) -> dict[str, float]:
    """현장 상세정보를 보정용 위험 신호로 변환한다(학습 확률이 아님)."""
    scores: dict[str, float] = {}
    machine = (req.machine_type or "").lower()
    storage = f"{req.storage_location or ''} {req.storage_method or ''}".lower()
    safety_factor = {
        "INSTALLED": 0.5,
        "PARTIAL": 1.2,
        "NONE": 1.8,
        "UNKNOWN": 1.0,
    }.get(req.safety_device_status or "UNKNOWN", 1.0)
    count_factor = 1.25 if (req.machine_count or 0) >= 5 else 1.0

    mappings = {
        "끼임": ("프레스", "절단", "컨베이어", "롤러", "압축", "성형"),
        "부딪힘": ("지게차", "차량", "운반", "굴착기"),
        "떨어짐": ("사다리", "고소", "비계", "리프트"),
    }
    for category, keywords in mappings.items():
        matches = sum(1 for keyword in keywords if keyword in machine)
        if matches:
            scores[category] = scores.get(category, 0.0) + matches * safety_factor * count_factor

    if any(keyword in storage for keyword in ("2단", "3단", "다단", "높이", "선반", "고층")):
        scores["무너짐"] = scores.get("무너짐", 0.0) + 1.2
    if any(keyword in storage for keyword in ("통로", "출입구", "이동로")):
        scores["부딪힘"] = scores.get("부딪힘", 0.0) + 0.8
        scores["넘어짐"] = scores.get("넘어짐", 0.0) + 0.6
    return scores


def _risk_basis(label: str, counts: dict[str, int], profile_scores: dict[str, float]) -> str:
    bases = []
    if label in counts:
        bases.append(f"체크리스트 미비 {counts[label]}건")
    if label in profile_scores:
        bases.append("기계·안전장치·적재 조건")
    bases.append("동종 사업장 예측")
    return "과 ".join(bases) + "을 함께 반영"
