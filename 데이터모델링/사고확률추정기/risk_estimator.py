"""
risk_estimator.py — 베이즈 정리 기반 산재 위험도 추정 모듈

사용법:
    from risk_estimator import RiskEstimator

    est = RiskEstimator()
    result = est.predict(
        산업='건설업',
        연령='40~49세',
        성별='남',
        시도='경기',
        규모='50~99인',
    )
    # result: DataFrame [발생형태, P_risk, 순위]

연령 입력값 (IPF 표준 5개 그룹):
    '20~29세', '30~39세', '40~49세', '50~59세', '60세이상'
"""

import os
import pandas as pd
import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CHAIN_DIR  = os.path.join(os.path.dirname(SCRIPT_DIR), '체인룰 데이터')


class RiskEstimator:

    def __init__(self):
        # P(F | 사고) — (산업, 연령, 성별, 시도, 규모, 발생형태) → 비율
        self.pf_acc = pd.read_csv(
            os.path.join(CHAIN_DIR, 'P_FL_given_accident.csv'), encoding='utf-8-sig'
        )

        # P(F) — (산업, 연령, 성별, 시도, 규모) → 비율
        self.pf = pd.read_csv(
            os.path.join(CHAIN_DIR, 'P_F_joint.csv'), encoding='utf-8-sig'
        )

        # P(사고) — 전체 근로자 대비 전체 재해자 비율 (상수)
        acc_rate = pd.read_csv(
            os.path.join(CHAIN_DIR, 'P_업종사고_재해율.csv'), encoding='utf-8-sig'
        )
        self.p_accident = acc_rate['재해자수'].sum() / acc_rate['근로자수'].sum()

    def predict(self, 산업: str, 연령: str, 성별: str, 시도: str, 규모: str) -> pd.DataFrame:
        """
        Returns
        -------
        DataFrame with columns: [발생형태, P_risk, 순위]
        P_risk = P(발생형태, 사고 | F)  (정규화 전 점수, 상대적 위험도)
        """
        # ── P(사고) — 전체 재해율 (상수, 업종 무관) ────────

        # ── P(F) ────────────────────────────────────────
        성별_pf = '남자' if 성별 == '남' else '여자'  # P_F_joint 성별 표기 변환
        pf_row = self.pf[
            (self.pf['산업'] == 산업) &
            (self.pf['연령'] == 연령) &
            (self.pf['성별'] == 성별_pf) &
            (self.pf['시도'] == 시도) &
            (self.pf['규모'] == 규모)
        ]
        if pf_row.empty:
            raise ValueError(f"P_F_joint에 해당 조합 없음: {산업}, {연령}, {성별}, {시도}, {규모}")
        p_f = pf_row['P_F'].values[0]

        if p_f <= 0:
            raise ValueError(f"P(F) = 0: 해당 조합은 모집단에서 관측되지 않음")

        # ── P(F, 발생형태 | 사고) ──────────────────────
        pf_acc_rows = self.pf_acc[
            (self.pf_acc['산업'] == 산업) &
            (self.pf_acc['연령'] == 연령) &
            (self.pf_acc['성별'] == 성별) &
            (self.pf_acc['시도'] == 시도) &
            (self.pf_acc['규모'] == 규모)
        ].copy()

        if pf_acc_rows.empty:
            raise ValueError(f"P_FL_given_accident에 해당 조합 없음: KOSHA 데이터에 케이스 없을 수 있음")

        # ── 베이즈 정리 ─────────────────────────────────
        # P(발생형태=k, 사고 | F) ∝ P(F, k | 사고) × P(사고|산업) / P(F)
        pf_acc_rows['P_risk'] = (
            pf_acc_rows['P_FL_given_accident'] * self.p_accident / p_f
        )

        result = (
            pf_acc_rows[['발생형태', 'P_risk']]
            .sort_values('P_risk', ascending=False)
            .reset_index(drop=True)
        )
        result['순위'] = result.index + 1

        return result


# ── CLI 테스트 ────────────────────────────────────────
if __name__ == '__main__':
    import sys, io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

    est = RiskEstimator()

    examples = [
        dict(산업='건설업',  연령='40~49세', 성별='남', 시도='경기', 규모='50~99인'),
        dict(산업='제조업',  연령='30~39세', 성별='남', 시도='서울', 규모='100~299인'),
        dict(산업='기타의사업', 연령='50~59세', 성별='여', 시도='부산', 규모='5인 미만'),
    ]

    for ex in examples:
        print(f"\n{'='*55}")
        print(f"  산업={ex['산업']} / 연령={ex['연령']} / 성별={ex['성별']}")
        print(f"  시도={ex['시도']} / 규모={ex['규모']}")
        print(f"{'='*55}")
        try:
            df = est.predict(**ex)
            print(df.to_string(index=False))
        except ValueError as e:
            print(f"  [오류] {e}")
