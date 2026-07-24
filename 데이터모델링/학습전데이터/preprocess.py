"""
preprocess.py — KOSHA 마이크로데이터 전처리 통합 스크립트

1단계: 대규모 업종 5개 → 개별 CSV
2단계: 소규모 업종 5개 → 소규모통합.csv

원핫 인코딩 reference category (드롭):
  성별  : 성별_여   드롭 → 성별_남 1개
  지역  : 지역_충북 드롭 → 지역_강원~지역_충남 15개
  산업  : 산업_금융 드롭 → 산업_농업/어업/임업/전기가스 4개 (소규모통합 전용)

건설공사금액_enc:
  건설업.csv에만 포함. 나머지 업종 파일에는 컬럼 자체 제외.

소규모 (1만행 이하, 통합): 농업, 어업, 임업, 전기·가스·증기·수도사업, 금융및보험업
대규모 (개별 저장)       : 건설업, 광업, 기타의사업, 운수·창고·통신업, 제조업
"""

import os, sys, io
import pandas as pd
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from kosha_encodings import (
    SIZE_ORDER, AGE_ORDER, WORK_PERIOD_ORDER, CONST_AMT_ORDER,
    ACCIDENT_TYPE_MAP, DISEASE_MAP, DISEASE_DETAIL_MAP, INJURY_ORDER,
    SUBJOB_NORM, SUBJOB_MAP,
)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
KOSHA_CSV  = os.path.join(os.path.dirname(SCRIPT_DIR), 'KOSHA_업종별_CSV', 'KOSHA_전체.csv')
DIR_ACC    = os.path.join(SCRIPT_DIR, 'target-발생형태')
DIR_DIS    = os.path.join(SCRIPT_DIR, 'target-질병종류')
os.makedirs(DIR_ACC, exist_ok=True)
os.makedirs(DIR_DIS, exist_ok=True)

INDUSTRY_NORM = {
    '광  업':                  '광업',
    '농  업':                  '농업',
    '어  업':                  '어업',
    '임  업':                  '임업',
    '운수·창고 및 통신업':      '운수·창고·통신업',
    '전기·가스·증기및수도사업': '전기·가스·증기·수도사업',
}

LARGE_INDUSTRIES = {'건설업', '광업', '기타의사업', '운수·창고·통신업', '제조업'}

# 소규모 업종 → 산업 원핫 컬럼명 (None = 금융, reference category → 0000)
SMALL_INDUSTRIES = {
    '농업':                   '산업_농업',
    '어업':                   '산업_어업',
    '임업':                   '산업_임업',
    '전기·가스·증기·수도사업': '산업_전기가스',
    '금융및보험업':            None,
}
INDUSTRY_ONEHOT_COLS = ['산업_농업', '산업_어업', '산업_임업', '산업_전기가스']

REGIONS      = ['강원','경기','경남','경북','광주','대구','대전','부산',
                '서울','울산','인천','전남','전북','제주','충남']  # 충북 드롭
REGION_FEATS = [f'지역_{r}' for r in REGIONS]

# 피처 컬럼 세트
BASE      = ['통계기준년월', '규모_enc', '연령_enc', '근무기간_enc', '종업종_enc']
GENDER    = ['성별_남']
FEAT_건설  = BASE + ['건설공사금액_enc'] + GENDER + REGION_FEATS   # 21개
FEAT_대규모 = BASE                       + GENDER + REGION_FEATS   # 20개
FEAT_소규모 = (['통계기준년월'] + INDUSTRY_ONEHOT_COLS             # 산업 원핫 4개
              + ['규모_enc', '연령_enc', '근무기간_enc', '종업종_enc']
              + GENDER + REGION_FEATS)                              # 25개


# ── 로드 ─────────────────────────────────────────────────
print('데이터 로드...')
df = pd.read_csv(KOSHA_CSV, encoding='utf-8-sig', dtype=str)
raw_total = len(df)
print(f'  원본: {raw_total:,}행')

df['대업종'] = df['대업종'].replace(INDUSTRY_NORM)
df['통계기준년월'] = df['통계기준년월'].astype(int)


# ── 인코딩 ───────────────────────────────────────────────
df['규모_enc']         = df['규모'].map(SIZE_ORDER)
df['연령_enc']         = df['연령'].map(AGE_ORDER)
df['근무기간_enc']     = df['근무기간'].map(WORK_PERIOD_ORDER)
df['건설공사금액_enc'] = df['건설공사금액'].map(CONST_AMT_ORDER)
df['종업종_enc']      = df['종업종'].map(SUBJOB_NORM).map(SUBJOB_MAP)
df['발생형태_enc']     = df['발생형태'].map(ACCIDENT_TYPE_MAP)
df['재해정도_enc']     = df['재해정도'].map(INJURY_ORDER)
df['질병종류_enc']     = df['질병종류'].map(DISEASE_MAP)
df['세부질병종류_enc'] = df['세부질병종류'].map(DISEASE_DETAIL_MAP)

# 원핫: 성별_남 (성별_여 드롭), 지역 15개 (충북 드롭)
df['성별_남'] = (df['성별'] == '남').astype(int)
for r in REGIONS:
    df[f'지역_{r}'] = (df['지역'] == r).astype(int)

# 소규모 산업 원핫 (금융 = reference → 0,0,0,0)
for col in INDUSTRY_ONEHOT_COLS:
    df[col] = 0
for 업종, onehot_col in SMALL_INDUSTRIES.items():
    if onehot_col is not None:
        df.loc[df['대업종'] == 업종, onehot_col] = 1


# ── 공통 drop ────────────────────────────────────────────
drop_mask = (df['연령_enc'] == -1) | (df['근무기간_enc'] == -1) | df['종업종_enc'].isna()
n_drop_common = drop_mask.sum()
df = df[~drop_mask].reset_index(drop=True)
print(f'  연령·근무기간 분류불능 drop: {n_drop_common:,}행 → 잔여 {len(df):,}행')


# ── 1단계: 대규모 업종 개별 저장 ─────────────────────────
print(f'\n[1단계] 대규모 업종')
print(f'{"업종":<22} {"원본":>8} {"acc drop":>10} {"acc 최종":>10} {"dis 최종":>10}')
print('-' * 65)

stats_large = []
for 업종 in sorted(LARGE_INDUSTRIES):
    g    = df[df['대업종'] == 업종].copy()
    feat = FEAT_건설 if 업종 == '건설업' else FEAT_대규모
    fname = 업종.replace('·','_').replace(' ','_').replace('/','_').replace('.','_')

    g1 = g[g['발생형태_enc'] != -1].copy()
    if 업종 == '건설업':
        g1 = g1[g1['건설공사금액_enc'] != -2].copy()
    n_drop = len(g) - len(g1)
    g1[feat + ['발생형태_enc', '재해정도_enc']].to_csv(
        os.path.join(DIR_ACC, f'{fname}.csv'), index=False, encoding='utf-8-sig')

    g2 = g[g['발생형태'] == '업무상질병'].copy()
    if 업종 == '건설업':
        g2 = g2[g2['건설공사금액_enc'] != -2].copy()
    g2[feat + ['질병종류_enc', '세부질병종류_enc', '재해정도_enc']].to_csv(
        os.path.join(DIR_DIS, f'{fname}.csv'), index=False, encoding='utf-8-sig')

    stats_large.append({'업종': 업종, '원본': len(g), 'drop': n_drop,
                        'acc': len(g1), 'dis': len(g2)})
    print(f'  {업종:<20} {len(g):>8,} {n_drop:>10,} {len(g1):>10,} {len(g2):>10,}')


# ── 2단계: 소규모 업종 통합 저장 ─────────────────────────
print(f'\n[2단계] 소규모 업종 통합')
small_acc, small_dis = [], []
stats_small = []

for 업종 in SMALL_INDUSTRIES:
    g  = df[df['대업종'] == 업종].copy()
    g1 = g[g['발생형태_enc'] != -1].copy()
    g2 = g[g['발생형태'] == '업무상질병'].copy()

    small_acc.append(g1[FEAT_소규모 + ['발생형태_enc', '재해정도_enc']])
    small_dis.append(g2[FEAT_소규모 + ['질병종류_enc', '세부질병종류_enc', '재해정도_enc']])
    stats_small.append({'업종': 업종, '원본': len(g), 'drop': len(g) - len(g1),
                        'acc': len(g1), 'dis': len(g2)})
    print(f'  {업종:<30} acc {len(g1):>6,}행  dis {len(g2):>5,}행')

comb_acc = pd.concat(small_acc, ignore_index=True)
comb_dis = pd.concat(small_dis, ignore_index=True)
comb_acc.to_csv(os.path.join(DIR_ACC, '소규모통합.csv'), index=False, encoding='utf-8-sig')
comb_dis.to_csv(os.path.join(DIR_DIS, '소규모통합.csv'), index=False, encoding='utf-8-sig')
print(f'  → 소규모통합 발생형태: {len(comb_acc):,}행  컬럼수: {len(comb_acc.columns)}')
print(f'  → 소규모통합 질병종류: {len(comb_dis):,}행  컬럼수: {len(comb_dis.columns)}')


# ── 요약 ─────────────────────────────────────────────────
all_stats = stats_large + stats_small
tot_orig = sum(s['원본'] for s in all_stats)
tot_drop = sum(s['drop'] for s in all_stats)
tot_acc  = sum(s['acc'] for s in all_stats)
tot_dis  = sum(s['dis'] for s in all_stats)

print(f'\n{"─"*65}')
print(f'  합계  원본 {tot_orig:,}  drop {tot_drop + n_drop_common:,}  '
      f'발생형태 {tot_acc:,}  질병종류 {tot_dis:,}')
print(f'  전체 보존율: {tot_acc / raw_total * 100:.2f}%')
print(f'\n완료  target-발생형태/ {len(os.listdir(DIR_ACC))}개  '
      f'target-질병종류/ {len(os.listdir(DIR_DIS))}개')
