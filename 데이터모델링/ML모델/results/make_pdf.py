"""
make_pdf.py — 모델결과보고서.pdf 생성
results/ 폴더 안에서 실행: python make_pdf.py
"""

import os, sys, io
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import FancyBboxPatch

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

plt.rcParams['font.family']        = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH   = os.path.join(SCRIPT_DIR, 'metrics_전체.csv')
OUT_PATH   = os.path.join(SCRIPT_DIR, '모델결과보고서.pdf')

df = pd.read_csv(CSV_PATH, encoding='utf-8-sig')

TASK_ORDER = ['발생형태', '재해정도_발생형태기반', '질병종류', '세부질병종류', '재해정도_질병기반']
TASK_LABEL = {
    '발생형태':             '태스크 1 — 발생형태  (23개 클래스)',
    '재해정도_발생형태기반': '태스크 2 — 재해정도_발생형태기반  (8개 클래스)',
    '질병종류':             '태스크 3 — 질병종류  (7~9개 클래스)',
    '세부질병종류':         '태스크 4 — 세부질병종류  (21~32개 클래스)',
    '재해정도_질병기반':    '태스크 5 — 재해정도_질병기반  (8개 클래스)',
}

C_HEAD = '#1e3a5f'
C_EVEN = '#f0f4fa'
C_ODD  = '#ffffff'
C_TEXT = '#1a1a2e'
C_FMAC = '#dc2626'
C_FW   = '#7c3aed'

BANNER_Y    = 0.940   # 배너 하단 y (상단 = BANNER_Y + BANNER_H)
BANNER_H    = 0.046
CONTENT_TOP = BANNER_Y - 0.055   # 콘텐츠 시작 y (배너 아래, 여백 충분히)


def add_page_number(fig, n):
    fig.text(0.97, 0.015, str(n), ha='right', va='bottom', fontsize=9, color='#aaaaaa')


def draw_banner(fig, title):
    """배너 + 제목 텍스트. fig.transFigure 좌표."""
    banner = FancyBboxPatch(
        (0.05, BANNER_Y), 0.90, BANNER_H,
        boxstyle='round,pad=0.005',
        facecolor=C_HEAD, edgecolor='none',
        transform=fig.transFigure, clip_on=False, zorder=5,
    )
    fig.add_artist(banner)
    fig.text(0.50, BANNER_Y + BANNER_H / 2, title,
             ha='center', va='center', fontsize=14, fontweight='bold',
             color='white', zorder=6)


def section_label(fig, y, text):
    """섹션 소제목 (fig 좌표). ax.set_title() 대신 사용."""
    fig.text(0.05, y, text, ha='left', va='bottom',
             fontsize=10.5, fontweight='bold', color=C_HEAD, zorder=4)


def draw_table(ax, headers, rows, col_widths=None, row_height=0.11, font_size=8.5):
    ax.axis('off')
    n_cols = len(headers)
    if col_widths is None:
        col_widths = [1.0 / n_cols] * n_cols
    x_starts = [sum(col_widths[:i]) for i in range(n_cols)]
    y_top = 1.0

    for j, (h, w, x) in enumerate(zip(headers, col_widths, x_starts)):
        ax.add_patch(plt.Rectangle(
            (x, y_top - row_height), w, row_height,
            facecolor=C_HEAD, transform=ax.transAxes, clip_on=False, zorder=3,
        ))
        ax.text(x + w / 2, y_top - row_height / 2, h,
                ha='center', va='center', fontsize=font_size,
                color='white', fontweight='bold', transform=ax.transAxes, zorder=4)

    for i, row in enumerate(rows):
        bg = C_EVEN if i % 2 == 0 else C_ODD
        y  = y_top - (i + 2) * row_height
        for j, (val, w, x) in enumerate(zip(row, col_widths, x_starts)):
            ax.add_patch(plt.Rectangle(
                (x, y), w, row_height,
                facecolor=bg, edgecolor='#cccccc', linewidth=0.4,
                transform=ax.transAxes, clip_on=False, zorder=3,
            ))
            ax.text(x + w / 2, y + row_height / 2, str(val),
                    ha='center', va='center', fontsize=font_size - 0.5,
                    color=C_TEXT, transform=ax.transAxes, zorder=4)


# ─────────────────────────────────────────────────────────────
# Page 1: 표지
# ─────────────────────────────────────────────────────────────
def page_cover(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, 'ML 모델 학습 결과 보고서')
    fig.text(0.50, BANNER_Y - 0.018,
             'LightGBM × Optuna — 산재 분류 모델 (30개)',
             ha='center', va='top', fontsize=11, color='#555577')

    # 실험 개요
    section_label(fig, CONTENT_TOP, '실험 개요')
    ax1 = fig.add_axes([0.08, 0.57, 0.84, 0.28])
    rows = [
        ['알고리즘',         'LightGBM (GBDT)'],
        ['하이퍼파라미터 튜닝', 'Optuna TPE, N_TRIALS = 50'],
        ['모델 총 수',       '5 태스크 × 6 업종 = 30개'],
        ['Train 기준',      '통계기준년월 ≤ 2023'],
        ['Test 기준',       '통계기준년월 ≥ 2024'],
        ['클래스 불균형 처리', 'is_unbalance = True'],
        ['Early stopping',  'patience 50 rounds'],
        ['Optuna 최적화 목표', 'Macro F1 (maximize)'],
    ]
    draw_table(ax1, ['항목', '값'], rows, col_widths=[0.38, 0.62], row_height=0.095)

    # 태스크 구성
    section_label(fig, 0.52, '태스크 구성')
    ax2 = fig.add_axes([0.08, 0.10, 0.84, 0.40])
    rows2 = [
        ['1', '발생형태',             'target-발생형태', '23',    '사고 유형 분류'],
        ['2', '재해정도_발생형태기반', 'target-발생형태', '8',     '재해 심각도 (사고 데이터)'],
        ['3', '질병종류',             'target-질병종류',  '7~9',   '업무상질병 유형 분류'],
        ['4', '세부질병종류',         'target-질병종류',  '21~32', '세부 질병 분류'],
        ['5', '재해정도_질병기반',    'target-질병종류',  '8',     '재해 심각도 (질병 데이터)'],
    ]
    draw_table(ax2, ['#', '태스크명', '데이터 폴더', '클래스 수', '설명'],
               rows2, col_widths=[0.05, 0.22, 0.22, 0.11, 0.40], row_height=0.115)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 2~6: 태스크별 상세
# ─────────────────────────────────────────────────────────────
def page_task(pdf, task_name, pnum):
    tdf = df[df['task'] == task_name].copy()
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, TASK_LABEL[task_name])

    # ── 성능 지표 테이블 ──
    section_label(fig, CONTENT_TOP, '성능 지표 (Test set)')
    ax1 = fig.add_axes([0.04, 0.64, 0.92, 0.21])
    r_metric = []
    for _, row in tdf.iterrows():
        r_metric.append([
            row['industry'],
            f"{int(row['train_rows']):,}",
            f"{int(row['test_rows']):,}",
            f"{row['accuracy']:.4f}",
            f"{row['precision']:.4f}",
            f"{row['recall']:.4f}",
            f"{row['f1_macro']:.4f}",
            f"{row['f1_weighted']:.4f}",
        ])
    draw_table(ax1,
               ['업종', 'Train', 'Test', 'Accuracy', 'Precision', 'Recall', 'F1-macro', 'F1-weighted'],
               r_metric,
               col_widths=[0.175, 0.095, 0.085, 0.095, 0.095, 0.085, 0.095, 0.115],
               row_height=0.11, font_size=8)

    # ── 하이퍼파라미터 테이블 ──
    section_label(fig, 0.605, '최적 하이퍼파라미터 (Optuna Best Trial)')
    ax2 = fig.add_axes([0.04, 0.43, 0.92, 0.16])
    r_hp = []
    for _, row in tdf.iterrows():
        r_hp.append([
            row['industry'],
            str(int(row['best_num_leaves'])),
            str(int(row['best_max_depth'])),
            f"{row['best_learning_rate']:.5f}",
            str(int(row['best_min_child_samples'])),
        ])
    draw_table(ax2,
               ['업종', 'num_leaves', 'max_depth', 'learning_rate', 'min_child_samples'],
               r_hp,
               col_widths=[0.27, 0.18, 0.15, 0.20, 0.20],
               row_height=0.115, font_size=8.5)

    # ── 바차트 ──
    ax3 = fig.add_axes([0.08, 0.07, 0.87, 0.36])
    industries = tdf['industry'].tolist()
    x     = np.arange(len(industries))
    width = 0.35

    b1 = ax3.bar(x - width / 2, tdf['f1_macro'].values,    width, label='F1-macro',    color=C_FMAC, alpha=0.85)
    b2 = ax3.bar(x + width / 2, tdf['f1_weighted'].values,  width, label='F1-weighted', color=C_FW,   alpha=0.85)

    ymax = max(tdf['f1_weighted'].max(), tdf['f1_macro'].max())
    for bar in list(b1) + list(b2):
        h = bar.get_height()
        ax3.text(bar.get_x() + bar.get_width() / 2, h + 0.005,
                 f'{h:.3f}', ha='center', va='bottom', fontsize=7)

    ax3.set_xticks(x)
    ax3.set_xticklabels(industries, fontsize=9)
    ax3.set_ylim(0, min(1.05, ymax * 1.30 + 0.05))
    ax3.set_ylabel('Score', fontsize=9)
    ax3.set_title('F1-macro vs F1-weighted (업종별 비교)', fontsize=10,
                  fontweight='bold', color=C_HEAD, pad=6)
    ax3.legend(fontsize=9, loc='upper right')
    ax3.grid(axis='y', alpha=0.3, linestyle='--')
    ax3.spines['top'].set_visible(False)
    ax3.spines['right'].set_visible(False)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 7: 전체 요약 + 결과 해석
# ─────────────────────────────────────────────────────────────
def page_summary(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, '전체 성능 요약 및 결과 해석')

    # 요약 테이블
    section_label(fig, CONTENT_TOP, '태스크별 성능 요약 (F1-macro 기준)')
    task_meta = {
        '발생형태':             ('23',    '4.3%'),
        '재해정도_발생형태기반': ('8',     '12.5%'),
        '질병종류':             ('7~9',   '11~14%'),
        '세부질병종류':         ('21~32', '3~5%'),
        '재해정도_질병기반':    ('8',     '12.5%'),
    }
    summary_rows = []
    for task in TASK_ORDER:
        tdf = df[df['task'] == task]
        avg  = tdf['f1_macro'].mean()
        best = tdf.loc[tdf['f1_macro'].idxmax()]
        worst= tdf.loc[tdf['f1_macro'].idxmin()]
        n_cls, rand = task_meta[task]
        summary_rows.append([
            task, n_cls, rand, f'{avg:.3f}',
            f"{best['industry']} ({best['f1_macro']:.3f})",
            f"{worst['industry']} ({worst['f1_macro']:.3f})",
        ])
    ax1 = fig.add_axes([0.03, 0.66, 0.94, 0.19])
    draw_table(ax1,
               ['태스크', '클래스', '랜덤기준', 'avg F1-macro', '최고 업종', '최저 업종'],
               summary_rows,
               col_widths=[0.22, 0.07, 0.09, 0.10, 0.255, 0.265],
               row_height=0.115, font_size=8)

    # 전체 비교 바차트
    ax2 = fig.add_axes([0.07, 0.39, 0.88, 0.27])
    task_short  = ['발생형태', '재해정도\n발생형태기반', '질병종류', '세부질병종류', '재해정도\n질병기반']
    industries  = df[df['task'] == '발생형태']['industry'].tolist()
    colors_ind  = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']
    x     = np.arange(len(TASK_ORDER))
    width = 0.13
    for k, (ind, col) in enumerate(zip(industries, colors_ind)):
        vals = []
        for t in TASK_ORDER:
            sub = df[(df['task'] == t) & (df['industry'] == ind)]
            vals.append(sub['f1_macro'].values[0] if len(sub) > 0 else 0)
        ax2.bar(x + (k - 2.5) * width, vals, width, label=ind, color=col, alpha=0.82)
    ax2.set_xticks(x)
    ax2.set_xticklabels(task_short, fontsize=8.5)
    ax2.set_ylabel('F1-macro', fontsize=9)
    ax2.set_title('태스크 × 업종 F1-macro 전체 비교', fontsize=10,
                  fontweight='bold', color=C_HEAD, pad=6)
    ax2.legend(fontsize=7.5, loc='upper right', ncol=2)
    ax2.grid(axis='y', alpha=0.3, linestyle='--')
    ax2.spines['top'].set_visible(False)
    ax2.spines['right'].set_visible(False)

    # 해석 텍스트
    section_label(fig, 0.365, '결과 해석')
    notes = [
        ('■ 성능이 낮은 이유', True),
        ('  1) 피처–타겟 약한 상관: 성별·연령·지역·규모만으로 사고 유형 예측은 본질적으로 어려운 문제.', False),
        ('     사고 유형은 "어떤 작업을 하느냐·어떤 장비를 쓰느냐"에 의존 — 해당 피처 없음.', False),
        ('  2) 강한 클래스 불균형: 발생형태 23개 중 상위 4개가 70%+ 차지.', False),
        ('  3) 세부질병종류 클래스 최대 32개: 소규모 업종은 행수 대비 클래스 수 과다.', False),
        ('  4) 광업 Acc 95.9% 이상현상: 테스트셋 1개 클래스 쏠림 — F1-macro 7.5%가 실제 성능.', False),
        ('', False),
        ('■ 개선 방향', True),
        ('  - 피처 추가: 작업 공정, 장비, 세분류 업종 등 직접적 위험 요인 변수 발굴', False),
        ('  - Top-K 예측: 정확한 1개 대신 상위 3~5개 후보 제시 방식으로 실용성 확보', False),
        ('  - 베이지안 추정기와 병용: ML은 업무상질병 세분류에 집중, 나머지는 베이즈 보조', False),
        ('  - Feature importance 분석: 업종별 기여 피처 확인 후 불필요 피처 정리', False),
    ]
    y0 = 0.345
    for text, bold in notes:
        fig.text(0.06, y0, text, ha='left', va='top',
                 fontsize=8.8 if bold else 8.3,
                 fontweight='bold' if bold else 'normal',
                 color=C_HEAD if bold else C_TEXT)
        y0 -= 0.024

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
print('PDF 생성 중...')
with PdfPages(OUT_PATH) as pdf:
    meta = pdf.infodict()
    meta['Title']   = 'ML 모델 학습 결과 보고서'
    meta['Author']  = 'KOSHA 산재예방 AI'
    meta['Subject'] = 'LightGBM × Optuna 30개 모델 학습 결과'

    page_cover(pdf, 1)
    print('  [1/7] 표지·개요')
    for i, task in enumerate(TASK_ORDER, start=2):
        page_task(pdf, task, i)
        print(f'  [{i}/7] {task}')
    page_summary(pdf, 7)
    print('  [7/7] 전체 요약·해석')

print(f'완료: {OUT_PATH}')
