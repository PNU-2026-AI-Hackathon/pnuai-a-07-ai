"""
POST /predict/risk 요청/응답 스키마.

주의: 이 계약은 아직 백엔드(구현서)와 최종 확정되지 않았다 — 원래 제안은
industry/sub_industry/size_class/region/checklist_scores만 있었는데, 실제
LightGBM 모델(predict.py)은 성별·연령·근무기간도 필요로 한다(개인 재해자
기준으로 학습됐기 때문). 사업장 단위 진단에 "대표 근로자" 프로필을 어떻게
반영할지는 기획 확인이 필요한 열린 문제라, 일단 옵션 필드로 두고 기본값을
채워서 오늘 당장 동작은 하게 만들어뒀다 — 실제 대표값 수집 방식이 정해지면
DEFAULT_* 값과 필드 필수 여부를 다시 조정해야 한다.
"""

from typing import Literal

from pydantic import BaseModel, Field

# 대표 근로자 프로필 기본값 — 실측 분포 기반이 아닌 임시값.
# (성별/연령/근무기간을 프런트가 아직 수집하지 않음 → 기획 확인 필요)
DEFAULT_GENDER = "남"
DEFAULT_AGE_GROUP = "40세~44세"
DEFAULT_WORK_PERIOD = "10년 이상"

# DB answer_t enum과 동일 (2026-07-28 DB 변경공지: 체크리스트 835문항 교체 + NA 지원)
ChecklistAnswer = Literal["YES", "NO", "NA"]


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

    checklist_scores: dict[str, ChecklistAnswer] = Field(
        default_factory=dict,
        description="item_code → 답변. YES(안전조치 완료) / NO(미비, 감점 대상) / NA(해당 설비·작업 없음, 채점 제외). "
        "835개 item_code는 GET /predict/checklist-items 참고 (work_type으로 필터된 하위 목록만 보내면 됨)",
    )

    year: int = Field(2024, description="예측 기준 연도")
    top_k: int = Field(3, ge=1, le=10, description="발생형태/재해정도 후보 개수")


class TopRisk(BaseModel):
    type: str = Field(..., description="발생형태 라벨 (예: 끼임)")
    probability: float
    shap_value: float | None = Field(None, description="해당 클래스에 대한 SHAP 기여도 (모델 확신도 설명용)")


class SeverityPrediction(BaseModel):
    label: str
    probability: float


class RiskPredictResponse(BaseModel):
    risk_score: float | None = Field(
        None, description="0~100. coldstart_baseline에 매칭되는 참조 데이터가 전혀 없으면 None"
    )
    risk_grade: str | None = Field(None, description="LOW / MEDIUM / HIGH / CRITICAL")
    base_component: float | None = Field(None, description="베이스라인 백분위 점수 (0~60)")
    checklist_component: float = Field(
        0.0, description="체크리스트 가감점 (0~40). (미비 항목 가중치 합 / 응답 항목 가중치 합) × 40 — 문항 수와 무관한 비율 기반"
    )
    match_level: str = Field(
        "NONE", description="베이스라인 매칭 단계: EXACT / INDUSTRY_SIZE / INDUSTRY / NONE"
    )

    top_risks: list[TopRisk] = Field(default_factory=list, description="LightGBM 발생형태 예측 top-k")
    severity_prediction: list[SeverityPrediction] = Field(
        default_factory=list, description="LightGBM 재해정도(발생형태기반) 예측 top-k"
    )

    model_version: str = "lightgbm-2026.07"
