"""
chi_square.py — 피처 × 타겟 카이제곱 검정 + Cramér's V 분석

출력:
  cramers_v_결과.csv   — 전체 수치 결과
  cramers_v_히트맵.png  — 시각화
"""

import os, sys, io
import pandas as pd
import numpy as np
from scipy.stats import chi2_contingency
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import seaborn as sns

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
plt.rcParams['font.family']        = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH  = os.path.join(os.path.dirname(SCRIPT_DIR), 'KOSHA_업종별_CSV', 'KOSHA_전체.csv')

# ── 로드 ──────────────────────────────────────────────────────
print('데이터 로드...')
df = pd.read_csv(DATA_PATH, encoding='utf-8-sig', dtype=str)
print(f'  원본: {len(df):,}행')

INDUSTRY_NORM = {
    '광  업':                  '광업',
    '농  업':                  '농업',
    '어  업':                  '어업',
    '임  업':                  '임업',
    '운수·창고 및 통신업':      '운수·창고·통신업',
    '전기·가스·증기및수도사업': '전기·가스·증기·수도사업',
}
df['대업종'] = df['대업종'].replace(INDUSTRY_NORM)
df['년도']   = df['통계기준년월'].str[:4]   # YYYYMM → YYYY

# ── 서브셋 정의 ────────────────────────────────────────────────
# 발생형태 분석: 분류불능 제외
df_acc = df[~df['발생형태'].isin(['분류불능', ''])].copy()
# 질병종류 분석: 업무상질병 행만
df_dis = df[df['발생형태'] == '업무상질병'].copy()
# 건설업 한정 (건설공사금액 피처용)
df_con     = df_acc[df_acc['대업종'] == '건설업'].copy()
df_con_dis = df_dis[df_dis['대업종'] == '건설업'].copy()

print(f'  발생형태 분석용: {len(df_acc):,}행')
print(f'  질병종류 분석용: {len(df_dis):,}행')
print(f'  건설업(발생형태): {len(df_con):,}행')
print(f'  건설업(질병종류): {len(df_con_dis):,}행')

# ── Cramér's V ────────────────────────────────────────────────
EXCLUDE = {'분류불능', '해당없음', ''}

def cramers_v(x: pd.Series, y: pd.Series):
    """(V, p_value) 반환. 유효 데이터 부족 시 (nan, nan)."""
    mask = x.notna() & y.notna() & ~x.isin(EXCLUDE) & ~y.isin(EXCLUDE)
    xa, ya = x[mask], y[mask]
    if len(xa) < 30 or xa.nunique() < 2 or ya.nunique() < 2:
        return np.nan, np.nan
    ct = pd.crosstab(xa, ya)
    chi2, p, _, _ = chi2_contingency(ct)
    n = ct.values.sum()
    v = np.sqrt(chi2 / (n * min(ct.shape[0] - 1, ct.shape[1] - 1)))
    return round(float(v), 4), float(p)

# ── 분석 실행 ─────────────────────────────────────────────────
FEATURES_COMMON = ['대업종', '년도', '규모', '성별', '연령', '근무기간', '지역']
TARGETS_ACC     = ['발생형태', '재해정도']
TARGETS_DIS     = ['질병종류', '세부질병종류']

print('\n── 카이제곱 + Cramér\'s V 계산 ──')
records = []

def run(data, feat, target, note=''):
    v, p = cramers_v(data[feat], data[target])
    sig = ('***' if p is not None and p < 0.001 else
           '**'  if p is not None and p < 0.01  else
           '*'   if p is not None and p < 0.05  else
           'ns'  if p is not None else '—')
    v_str = f'{v:.4f}' if not np.isnan(v) else '—'
    p_str = f'{p:.2e}' if p is not None and not np.isnan(p) else '—'
    print(f'  {feat:<16} × {target:<12}  V={v_str}  p={p_str}  {sig}{note}')
    records.append({'피처': feat, '타겟': target, 'CramersV': v,
                    'p_value': p, '유의성': sig, '데이터': note.strip() or '전체'})

# 전체 데이터
for feat in FEATURES_COMMON:
    for target in TARGETS_ACC:
        run(df_acc, feat, target)

# 업무상질병
for feat in FEATURES_COMMON:
    for target in TARGETS_DIS:
        run(df_dis, feat, target, ' (업무상질병)')

# 건설공사금액 (건설업만)
for target in TARGETS_ACC:
    run(df_con, '건설공사금액', target, ' (건설업)')
for target in TARGETS_DIS:
    run(df_con_dis, '건설공사금액', target, ' (건설업+질병)')

# ── CSV 저장 ──────────────────────────────────────────────────
result_df = pd.DataFrame(records)
csv_path  = os.path.join(SCRIPT_DIR, 'cramers_v_결과.csv')
result_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
print(f'\nCSV 저장: {csv_path}')

# ── 히트맵 ────────────────────────────────────────────────────
feat_order   = FEATURES_COMMON + ['건설공사금액']
target_order = TARGETS_ACC + TARGETS_DIS

pivot   = result_df.pivot(index='피처', columns='타겟', values='CramersV')
p_pivot = result_df.pivot(index='피처', columns='타겟', values='p_value')
pivot   = pivot.reindex(index=feat_order, columns=target_order)
p_pivot = p_pivot.reindex(index=feat_order, columns=target_order)

# 셀 텍스트: V값 + 유의성 별표
annot = np.full(pivot.shape, '', dtype=object)
for i, feat in enumerate(feat_order):
    for j, target in enumerate(target_order):
        v = pivot.loc[feat, target]
        p = p_pivot.loc[feat, target]
        if pd.isna(v):
            annot[i, j] = '—'
        else:
            stars = ('***' if p < 0.001 else '**' if p < 0.01 else '*' if p < 0.05 else '')
            annot[i, j] = f'{v:.3f}\n{stars}' if stars else f'{v:.3f}'

fig, ax = plt.subplots(figsize=(9, 6.5))

sns.heatmap(
    pivot.astype(float),
    annot=annot,
    fmt='',
    cmap='YlOrRd',
    vmin=0.0, vmax=0.5,
    linewidths=0.6,
    linecolor='#e0e0e0',
    ax=ax,
    cbar_kws={'label': "Cramér's V", 'shrink': 0.8},
    annot_kws={'size': 9.5},
)

ax.set_title(
    "피처 × 타겟  Cramér's V 히트맵\n"
    "★ *** p<0.001  ** p<0.01  * p<0.05  |  0.1 미만=거의 무관  0.3 이상=중간 연관",
    fontsize=12, fontweight='bold', pad=14,
)
ax.set_xlabel('타겟 변수', fontsize=11, labelpad=8)
ax.set_ylabel('피처 변수', fontsize=11, labelpad=8)
ax.set_xticklabels(ax.get_xticklabels(), rotation=25, ha='right', fontsize=10)
ax.set_yticklabels(ax.get_yticklabels(), rotation=0, fontsize=10)

# 구분선: 건설공사금액 행 위 / 질병 타겟 열 앞
ax.axhline(len(FEATURES_COMMON), color='#444444', linewidth=2.0, linestyle='--')
ax.axvline(len(TARGETS_ACC),     color='#444444', linewidth=2.0, linestyle='--')

# 구분선 라벨
ax.text(len(TARGETS_ACC) / 2, len(feat_order) + 0.25, '← 전체 데이터',
        ha='center', va='bottom', fontsize=8, color='#555555')
ax.text(len(TARGETS_ACC) + len(TARGETS_DIS) / 2, len(feat_order) + 0.25, '업무상질병만 →',
        ha='center', va='bottom', fontsize=8, color='#555555')
ax.text(-0.55, len(FEATURES_COMMON) / 2, '공통\n피처',
        ha='center', va='center', fontsize=7.5, color='#555555', rotation=90)
ax.text(-0.55, len(FEATURES_COMMON) + 0.5, '건설업\n전용',
        ha='center', va='center', fontsize=7.5, color='#555555', rotation=90)

fig.tight_layout()
png_path = os.path.join(SCRIPT_DIR, 'cramers_v_히트맵.png')
fig.savefig(png_path, dpi=150, bbox_inches='tight')
plt.close(fig)
print(f'히트맵 저장: {png_path}')
print('\n완료')
