"""
POST /predict/risk 요청/응답 스키마.

2026-07-29: 콜드스타트 위험점수(risk_score/grade/base_component/checklist_component/
match_level)를 이 응답에서 제거했다. 백엔드가 이미 DB의 fn_coldstart_assess(workplace_id)를
직접 호출하고 있어서, 같은 공식을 여기서도 파이썬으로 복제해두면 두 군데가 따로 놀다가
어긋나는 문제가 생긴다(실제로 체크리스트 20→835문항 교체 때 내 파이썬 버전만 구버전에
멈춰있는 일이 있었음). 채점 공식은 DB 한 곳에만 두기로 백엔드와 합의(2026-07-29).

그래서 /predict/risk의 점수 계산 책임은 제거했다. 기존 checklist_scores는 삭제했고,
2026-08 안전진단 흐름 개편에서 재해유형 위험순위 보정용 risk_signals만 다시 추가했다.
최종 위험점수는 계속 DB 함수가 단독으로 계산한다.
"""

from pydantic import BaseModel, Field

# 대표 근로자 프로필 기본값 — 실측 분포 기반이 아닌 임시값.
# (성별/연령/근무기간을 프런트가 아직 수집하지 않음 → 기획 확인 필요)
DEFAULT_GENDER = "남"
DEFAULT_AGE_GROUP = "40세~44세"
DEFAULT_WORK_PERIOD = "10년 이상"


class RiskPredictRequest(BaseModel):
    industry: str = Field(..., description="code_industry.industry (예: 제조업)")
    sub_industry: str = Field(..., description="KOSHA 종업종 원본 문자열 또는 정규화된 44개 카테고리 중 하나")
    size_class: str = Field(..., description="code_size_class.size_class (예: 10~19인)")
    region: str = Field(..., description="code_region.region (예: 부산)")
    construction_amount: str | None = Field(
        None, description="건설업일 때만 사용. 예: '20억~50억원 미만'. 미지정 시 '해당없음' 처리"
    )

    # 대표 근로자 프로필 — 프런트가 아직 안 보내면 기본값 사용 (위 DEFAULT_* 참고)
    gender: str | None = Field(None, description="남 / 여. 미지정 시 기본값 사용")
    age_group: str | None = Field(None, description="예: '40세~44세'. 미지정 시 기본값 사용")
    work_period: str | None = Field(None, description="예: '1~2년 미만'. 미지정 시 기본값 사용")

    year: int = Field(2024, description="예측 기준 연도")
    top_k: int = Field(3, ge=1, le=10, description="발생형태/재해정도 후보 개수")

    machine_type: str | None = Field(None, description="주요 기계·설비 종류")
    machine_count: int | None = Field(None, ge=0, description="주요 기계·설비 총수")
    safety_device_status: str | None = Field(None, description="INSTALLED/PARTIAL/NONE/UNKNOWN")
    storage_location: str | None = Field(None, description="자재·물건 적재 위치")
    storage_method: str | None = Field(None, description="적재 방식 또는 높이")
    risk_signals: list["ChecklistRiskSignal"] = Field(
        default_factory=list,
        description="체크리스트 NO 응답을 재해유형별로 집계한 위험 신호",
    )


class ChecklistRiskSignal(BaseModel):
    category: str
    weight: float = Field(ge=0)
    deficient_count: int = Field(ge=0)


class TopRisk(BaseModel):
    type: str = Field(..., description="발생형태 라벨 (예: 끼임)")
    probability: float
    shap_value: float | None = Field(None, description="해당 클래스에 대한 SHAP 기여도 (모델 확신도 설명용)")
    basis: str | None = Field(None, description="위험순위 산출 근거")


class SeverityPrediction(BaseModel):
    label: str
    probability: float


class RiskPredictResponse(BaseModel):
    top_risks: list[TopRisk] = Field(default_factory=list, description="LightGBM 발생형태 예측 top-k")
    severity_prediction: list[SeverityPrediction] = Field(
        default_factory=list, description="LightGBM 재해정도(발생형태기반) 예측 top-k"
    )

    model_version: str = "lightgbm-checklist-hybrid-2026.08"
