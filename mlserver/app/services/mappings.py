"""
DB(ai_safework.code_industry / code_size_class) 값 ↔ predict.py 입력값 변환.

배경: DB의 code_industry는 실제 적재된 마이크로데이터가 4업종(건설업/운수창고통신업/
전기가스증기수도사업/제조업)뿐이라 4개만 있고, 모델은 6개 업종(+소규모통합 5개 원업종)으로
학습됨 (강주호 확인, 2026-07-23). 업종을 4개로 한정할지 6개로 확장할지는 기획 합의 대상이라
아직 미정 — 그 결정과 무관하게 동작하도록 매핑 테이블은 모델이 아는 값 전부를 담아둔다.
DB가 4개만 보내는 동안은 자연히 4개 항목만 실제로 쓰인다.

규모(size_class)는 DB가 10구간(20~29인/30~49인 분리)으로 원본 그래뉼래러티를 보존하고,
모델은 9구간(20~49인 병합)으로 학습됐다 — DB가 틀린 게 아니라 병합 방향이 다른 것뿐이므로
여기서 병합해서 넘긴다.
"""

# DB code_industry.industry → predict.py 대업종 파라미터
# 왼쪽 4개(건설업/운수창고통신업/전기가스증기수도사업/제조업)는 2026-07-23 기준 DB에 실재.
# 나머지는 업종 스코프가 6개로 확장될 경우를 대비한 선반영 — 아직 DB에는 없다.
INDUSTRY_DB_TO_MODEL: dict[str, str] = {
    "건설업": "건설업",
    "제조업": "제조업",
    "운수창고통신업": "운수·창고·통신업",
    "전기가스증기수도사업": "전기·가스·증기·수도사업",
    # 스코프 확장 대비 (현재 DB code_industry에는 없음)
    "광업": "광업",
    "기타의사업": "기타의사업",
    "농업": "농업",
    "어업": "어업",
    "임업": "임업",
    "금융및보험업": "금융및보험업",
}

# DB code_size_class.size_class (10구간) → predict.py 규모 파라미터 (9구간, SIZE_ORDER 키)
SIZE_CLASS_DB_TO_MODEL: dict[str, str] = {
    "5인 미만": "5인 미만",
    "5~9인": "5~9인",
    "10~19인": "10~19인",
    "20~29인": "20~49인",
    "30~49인": "20~49인",
    "50~99인": "50~99인",
    "100~299인": "100~299인",
    "300~499인": "300~499인",
    "500~999인": "500~999인",
    "1,000인 이상": "1,000인 이상",
}

# code_region은 모델 입력과 표기가 100% 일치 (16개, 충북은 모델에서 원핫 기준값으로 드롭됨).
# 매핑 불필요 — 그대로 predict.py에 넘기면 된다.


class UnmappedValueError(ValueError):
    """DB 값이 매핑 테이블에 없을 때 — 프런트/백엔드가 코드마스터에 없는 값을 보낸 경우."""


def map_industry(db_industry: str) -> str:
    try:
        return INDUSTRY_DB_TO_MODEL[db_industry]
    except KeyError:
        raise UnmappedValueError(
            f"알 수 없는 industry 값: {db_industry!r} "
            f"(code_industry 마스터에 없거나 매핑 테이블 갱신 필요)"
        ) from None


def map_size_class(db_size_class: str) -> str:
    try:
        return SIZE_CLASS_DB_TO_MODEL[db_size_class]
    except KeyError:
        raise UnmappedValueError(
            f"알 수 없는 size_class 값: {db_size_class!r} "
            f"(code_size_class 마스터에 없거나 매핑 테이블 갱신 필요)"
        ) from None
