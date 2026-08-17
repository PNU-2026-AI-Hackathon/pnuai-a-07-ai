"""
서버 시작 시 LightGBM Booster 30개(중 predict.py가 실제로 쓰는 24개)를
메모리에 미리 로드해 캐싱한다.

predict.py는 매 호출마다 파일을 다시 읽어 Booster를 새로 만드는데(_load_model),
그건 CLI/스크립트용으로는 괜찮지만 상시 서비스에서 요청마다 반복하면 느리다.
여기서는 predict.py와 동일한 model_str= 우회(한글 경로 fopen 문제) 방식으로
한 번만 읽어서 dict에 캐싱해두고, risk_service가 재사용한다.
"""

import sys
import logging
from functools import lru_cache
from pathlib import Path

import lightgbm as lgb
import shap

from app.core.config import settings

logger = logging.getLogger(__name__)

# predict.py가 실제로 predict()에서 사용하는 태스크 4개.
# (재해정도_질병기반 모델 파일도 있지만 predict.py가 쓰지 않아 여기서도 제외 — predict.py를 그대로 따른다)
TASK_NAMES: list[str] = ["발생형태", "재해정도_발생형태기반", "질병종류", "세부질병종류"]

# models/{태스크명}_{업종명}.txt 의 업종명 suffix (predict.py._safe_ind()가 만드는 값과 동일)
INDUSTRIES: list[str] = ["건설업", "광업", "기타의사업", "운수_창고_통신업", "제조업", "소규모통합"]

# risk_service._predict_with_shap가 실제로 SHAP을 계산하는 태스크만 워밍업한다.
# (질병종류/세부질병종류는 모델은 로드하지만 predict_risk가 SHAP까지 쓰진 않는다)
SHAP_TASK_NAMES: list[str] = ["발생형태", "재해정도_발생형태기반"]

_model_cache: dict[tuple[str, str], lgb.Booster] = {}


def ensure_ml_source_on_path() -> None:
    """predict.py / kosha_encodings.py를 import할 수 있게 sys.path에 등록.

    risk_service.py도 모듈 최상단에서 predict.py를 import하므로, FastAPI startup
    이벤트(load_all_models)보다 먼저 실행될 수 있다 — 그래서 idempotent한 이
    함수를 양쪽에서 각자 호출한다.
    """
    src_dir = str(settings.ML_MODEL_SOURCE_DIR)
    if src_dir not in sys.path:
        sys.path.insert(0, src_dir)


def load_all_models() -> None:
    """앱 startup 이벤트에서 1회 호출. 없는 모델 파일은 건너뛰고 경고만 남긴다."""
    ensure_ml_source_on_path()
    model_dir = Path(settings.ML_MODEL_SOURCE_DIR) / "models"

    loaded, missing = 0, []
    for task_name in TASK_NAMES:
        for industry in INDUSTRIES:
            path = model_dir / f"{task_name}_{industry}.txt"
            if not path.exists():
                missing.append(path.name)
                continue
            # lgb.Booster(model_file=...)는 C++ fopen() 사용 → 한글 경로 불가.
            # predict.py와 동일하게 Python open()으로 읽어 model_str=로 전달.
            with open(path, "r", encoding="utf-8") as f:
                _model_cache[(task_name, industry)] = lgb.Booster(model_str=f.read())
            loaded += 1

    logger.info("LightGBM 모델 로드 완료: %d개 (누락 %d개)", loaded, len(missing))
    if missing:
        logger.warning("누락된 모델 파일: %s", missing)

    warm_up_explainers()


def warm_up_explainers() -> None:
    """SHAP TreeExplainer를 미리 만들어 캐싱한다.

    lru_cache만 있으면 (task, industry) 조합의 첫 요청이 여전히 느리다(수 초~수십 초).
    시연 중 첫 요청에서 멈추지 않으려면 서버 기동 시 미리 다 만들어 둬야 한다.
    """
    warmed = 0
    for task_name in SHAP_TASK_NAMES:
        for industry in INDUSTRIES:
            if get_explainer(task_name, industry) is not None:
                warmed += 1
    logger.info("SHAP TreeExplainer 워밍업 완료: %d개", warmed)


def get_model(task_name: str, industry_suffix: str) -> lgb.Booster | None:
    return _model_cache.get((task_name, industry_suffix))


@lru_cache(maxsize=None)
def get_explainer(task_name: str, industry_suffix: str) -> shap.TreeExplainer | None:
    """SHAP TreeExplainer 는 트리 구조를 미리 분석해두는 비용이 커서, 요청마다 새로 만들면
    (task, industry) 조합 하나에 수 초~수십 초가 걸린다. 모델은 서버 구동 중 안 바뀌므로
    조합별로 한 번만 만들어 재사용한다."""
    model = get_model(task_name, industry_suffix)
    if model is None:
        return None
    return shap.TreeExplainer(model)


def is_loaded() -> bool:
    return len(_model_cache) > 0
