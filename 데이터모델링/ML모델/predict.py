"""
predict.py — 학습된 LightGBM 모델로 사고 유형 확률 top-k 예측

사용법:
    from predict import predict

    result = predict(
        대업종   = '제조업',
        종업종   = '기계기구·금속·비금속광물제품제조업',
        성별     = '남',
        연령     = '40세~44세',
        근무기간 = '1~2년 미만',
        규모     = '5~9인',
        지역     = '경기',
        top_k    = 3,
    )
    # result['발생형태']  → [('끼임', 0.281), ('물체에맞음', 0.194), ('떨어짐', 0.172)]
    # result['재해정도']  → [('요양재해자', 0.423), ('15~28일', 0.201), ...]
    # result['질병종류']  → [('신체부담작업', 0.381), ...]   # 모델 없으면 None
    # result['세부질병종류'] → [...]                         # 모델 없으면 None
"""

import os
import sys
import numpy as np
import pandas as pd
import lightgbm as lgb

# kosha_encodings 경로 추가
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '학습전데이터'))
from kosha_encodings import (
    SIZE_ORDER, AGE_ORDER, WORK_PERIOD_ORDER, CONST_AMT_ORDER,
    SUBJOB_NORM, SUBJOB_MAP,
    ACCIDENT_TYPE_INV, INJURY_INV, DISEASE_INV, DISEASE_DETAIL_INV,
)

# ── 경로 ──────────────────────────────────────────────────────
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'models')

# ── 소규모 업종 정보 ──────────────────────────────────────────
_SMALL_SET = {'농업', '어업', '임업', '전기·가스·증기·수도사업', '금융및보험업'}
_SMALL_ONEHOT = {
    '농업':                   '산업_농업',
    '어업':                   '산업_어업',
    '임업':                   '산업_임업',
    '전기·가스·증기·수도사업': '산업_전기가스',
    '금융및보험업':            None,   # reference category → 전부 0
}
_REGIONS = ['강원','경기','경남','경북','광주','대구','대전','부산',
            '서울','울산','인천','전남','전북','제주','충남']


# ── 내부 유틸 ─────────────────────────────────────────────────

def _safe_ind(대업종: str) -> str:
    """대업종 → 모델 파일명 suffix (소규모는 '소규모통합')"""
    if 대업종 in _SMALL_SET:
        return '소규모통합'
    return 대업종.replace('·', '_').replace(' ', '_')


def _load_model(task_name: str, safe: str) -> lgb.Booster | None:
    path = os.path.join(MODEL_DIR, f'{task_name}_{safe}.txt')
    if not os.path.exists(path):
        return None
    # lgb.Booster(model_file=) 는 C++ fopen() 사용 → 한글 경로 불가
    # Python open()으로 읽어서 model_str= 로 전달
    with open(path, 'r', encoding='utf-8') as f:
        return lgb.Booster(model_str=f.read())


def _build_row(대업종: str, 종업종: str, 성별: str, 연령: str,
               근무기간: str, 규모: str, 지역: str,
               건설공사금액: str | None, 년도: int) -> pd.DataFrame:
    """입력값을 모델 피처 DataFrame 한 행으로 변환"""
    row: dict = {}

    # 순서형 피처
    row['통계기준년월'] = int(f'{년도}01')
    row['규모_enc']     = SIZE_ORDER.get(규모, np.nan)
    row['연령_enc']     = AGE_ORDER.get(연령, np.nan)
    row['근무기간_enc'] = WORK_PERIOD_ORDER.get(근무기간, np.nan)
    row['종업종_enc']   = SUBJOB_MAP.get(SUBJOB_NORM.get(종업종, ''), np.nan)

    # 성별 원핫
    row['성별_남'] = 1 if 성별 == '남' else 0

    # 지역 원핫 (충북 드롭, reference)
    for r in _REGIONS:
        row[f'지역_{r}'] = 1 if 지역 == r else 0

    # 건설공사금액 (건설업 전용)
    if 대업종 == '건설업':
        row['건설공사금액_enc'] = CONST_AMT_ORDER.get(건설공사금액 or '해당없음', -1)

    # 소규모 업종 산업 원핫
    if 대업종 in _SMALL_SET:
        for col in ['산업_농업', '산업_어업', '산업_임업', '산업_전기가스']:
            row[col] = 0
        onehot = _SMALL_ONEHOT.get(대업종)
        if onehot:
            row[onehot] = 1

    return pd.DataFrame([row])


def _top_k(probs: np.ndarray, inv_map: dict, k: int) -> list[tuple[str, float]]:
    """확률 배열 → 상위 k개 (한글 라벨, 확률) 리스트"""
    idx = np.argsort(probs)[::-1][:k]
    return [(inv_map.get(int(i), f'class_{i}'), round(float(probs[i]), 4)) for i in idx]


# ── 메인 함수 ─────────────────────────────────────────────────

def predict(
    대업종:       str,
    종업종:       str,
    성별:         str,
    연령:         str,
    근무기간:     str,
    규모:         str,
    지역:         str,
    건설공사금액: str | None = None,
    년도:         int = 2024,
    top_k:        int = 3,
) -> dict:
    """
    Parameters
    ----------
    대업종       : 건설업 / 광업 / 기타의사업 / 운수·창고·통신업 / 제조업
                  / 농업 / 어업 / 임업 / 전기·가스·증기·수도사업 / 금융및보험업
    종업종       : KOSHA 원본 종업종 문자열 (SUBJOB_NORM으로 자동 정규화)
    성별         : '남' / '여'
    연령         : '40세~44세' 등 AGE_ORDER 키
    근무기간     : '1~2년 미만' 등 WORK_PERIOD_ORDER 키
    규모         : '5~9인' 등 SIZE_ORDER 키
    지역         : '경기' 등 시도명 (충북 → 충북 입력 가능, 지역_충북 없으니 전부 0 처리)
    건설공사금액 : 건설업일 때만 유효, None이면 '해당없음' 처리
    년도         : 예측 기준 연도 (기본 2024)
    top_k        : 반환할 후보 수 (기본 3)

    Returns
    -------
    {
        '발생형태':    [('끼임', 0.281), ('물체에맞음', 0.194), ...],
        '재해정도':    [('요양재해자', 0.423), ...],
        '질병종류':    [('신체부담작업', 0.381), ...] or None,
        '세부질병종류': [('신체부담작업', 0.302), ...] or None,
    }
    """
    safe = _safe_ind(대업종)
    row  = _build_row(대업종, 종업종, 성별, 연령, 근무기간, 규모, 지역,
                      건설공사금액, 년도)

    result: dict = {}

    # 발생형태 / 재해정도(발생형태기반)
    for task_name, out_key, inv_map in [
        ('발생형태',             '발생형태',  ACCIDENT_TYPE_INV),
        ('재해정도_발생형태기반', '재해정도',  INJURY_INV),
    ]:
        m = _load_model(task_name, safe)
        if m is None:
            result[out_key] = None
            continue
        X = row.reindex(columns=m.feature_name(), fill_value=0)
        probs = m.predict(X)[0]
        result[out_key] = _top_k(probs, inv_map, top_k)

    # 질병종류 / 세부질병종류
    for task_name, out_key, inv_map in [
        ('질병종류',    '질병종류',    DISEASE_INV),
        ('세부질병종류', '세부질병종류', DISEASE_DETAIL_INV),
    ]:
        m = _load_model(task_name, safe)
        if m is None:
            result[out_key] = None
            continue
        X = row.reindex(columns=m.feature_name(), fill_value=0)
        probs = m.predict(X)[0]
        result[out_key] = _top_k(probs, inv_map, top_k)

    return result


# ── 간단 테스트 ───────────────────────────────────────────────
if __name__ == '__main__':
    import sys, io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

    result = predict(
        대업종   = '제조업',
        종업종   = '기계기구·금속·비금속광물제품제조업',
        성별     = '남',
        연령     = '40세~44세',
        근무기간 = '1~2년 미만',
        규모     = '5~9인',
        지역     = '경기',
        top_k    = 3,
    )

    for key, val in result.items():
        if val is None:
            print(f'{key}: 모델 없음')
        else:
            print(f'{key}:')
            for label, prob in val:
                print(f'  {label:<20} {prob:.4f}')
