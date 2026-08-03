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


class InvalidChecklistItemError(ValueError):
    """v_ref_checklist에 없는 item_code가 요청에 포함된 경우.

    2026-07-28 DB 공지로 체크리스트가 수기 20문항 → SIF/LLM 생성 835문항으로 전면
    교체되면서 기존 코드(MFG-LOTO-MAINT 등)는 전부 무효화됐다. checklist_items.py
    스냅샷이 아직 835문항으로 갱신되지 않아, 지금은 구코드만 유효하게 인식된다.
    모르는 코드를 조용히 무시하면 "체크리스트를 다 어겨도 항상 0점"처럼 위험을
    과소평가하는 방향으로 조용히 틀릴 수 있어서, 안전 진단 특성상 무시하지 않고
    명시적으로 막는다 — 새 835문항 데이터가 들어오기 전까지는 신규 코드로 호출하면
    전부 이 예외가 난다(의도된 동작).
    """


def _checklist_component(checklist_scores: dict[str, str]) -> float:
    """(미비 항목 가중치 합 / 응답 항목 가중치 합) × 40. NA는 분모·분자 모두에서 제외.

    835문항 체계에서는 work_type으로 필터된 일부만 응답되므로, 문항 수에 좌우되지
    않는 비율 기반으로 계산한다 (2026-07-28 DB 변경공지, 기존 '가중치 합 그대로
    누적 후 40 cap' 방식에서 교체됨).
    """
    unknown = [code for code in checklist_scores if code not in CHECKLIST_ITEM_BY_CODE]
    if unknown:
        raise InvalidChecklistItemError(
            f"알 수 없는 checklist item_code {len(unknown)}개 (예: {unknown[:5]})"
        )

    answered_weight = 0.0
    no_weight = 0.0
    for item_code, answer in checklist_scores.items():
        if answer == "NA":
            continue
        item = CHECKLIST_ITEM_BY_CODE[item_code]
        weight = item["risk_weight"] * (2 if item["is_critical"] else 1)
        answered_weight += weight
        if answer == "NO":
            no_weight += weight

    if answered_weight == 0:
        return 0.0
    return round(min(40.0, (no_weight / answered_weight) * 40), 2)


def compute_coldstart_score(industry: str, size_class: str, region: str, checklist_scores: dict[str, str]) -> dict:
    serious_ratio, top_accident_type, match_level = _match_baseline(industry, size_class, region)
    checklist = _checklist_component(checklist_scores)  # item_code 검증 포함, 매칭과 무관하게 항상 계산

    if match_level == "NONE":
        # 2026-07-28 DB 변경공지: 베이스라인 매칭 자체가 없으면 risk_score/grade는 NULL
        # (참고할 통계가 전혀 없는데 숫자를 만들어내면 오히려 오해 소지)
        return {
            "risk_score": None,
            "risk_grade": None,
            "base_component": None,
            "checklist_component": checklist,
            "match_level": match_level,
            "top_accident_type": top_accident_type,
        }

    base = _base_component(serious_ratio)
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
