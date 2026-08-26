"""
make_pdf.py — 분석개요.pdf 생성
통계검정/ 폴더 안에서 실행: python make_pdf.py
"""

import os, sys, io
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
OUT_PATH   = os.path.join(SCRIPT_DIR, '분석개요.pdf')
IMG_PATH   = os.path.join(SCRIPT_DIR, 'cramers_v_히트맵.png')

C_HEAD = '#1e3a5f'
C_EVEN = '#f0f4fa'
C_ODD  = '#ffffff'
C_TEXT = '#1a1a2e'
BANNER_Y = 0.940
BANNER_H = 0.046


def add_page_number(fig, n):
    fig.text(0.97, 0.015, str(n), ha='right', va='bottom', fontsize=9, color='#aaaaaa')


def draw_banner(fig, title, subtitle=None):
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
    if subtitle:
        fig.text(0.50, BANNER_Y - 0.018, subtitle,
                 ha='center', va='top', fontsize=10, color='#555577')


def section_label(fig, y, text):
    fig.text(0.05, y, text, ha='left', va='bottom',
             fontsize=11, fontweight='bold', color=C_HEAD)


def draw_table(ax, headers, rows, col_widths=None, row_height=0.11, font_size=8.5):
    ax.axis('off')
    n_cols = len(headers)
    if col_widths is None:
        col_widths = [1.0 / n_cols] * n_cols
    x_starts = [sum(col_widths[:i]) for i in range(n_cols)]

    for j, (h, w, x) in enumerate(zip(headers, col_widths, x_starts)):
        ax.add_patch(plt.Rectangle((x, 1.0 - row_height), w, row_height,
                                   facecolor=C_HEAD, transform=ax.transAxes,
                                   clip_on=False, zorder=3))
        ax.text(x + w / 2, 1.0 - row_height / 2, h,
                ha='center', va='center', fontsize=font_size,
                color='white', fontweight='bold', transform=ax.transAxes, zorder=4)

    for i, row in enumerate(rows):
        bg = C_EVEN if i % 2 == 0 else C_ODD
        y  = 1.0 - (i + 2) * row_height
        for j, (val, w, x) in enumerate(zip(row, col_widths, x_starts)):
            ax.add_patch(plt.Rectangle((x, y), w, row_height,
                                       facecolor=bg, edgecolor='#cccccc', linewidth=0.4,
                                       transform=ax.transAxes, clip_on=False, zorder=3))
            ax.text(x + w / 2, y + row_height / 2, str(val),
                    ha='center', va='center', fontsize=font_size - 0.5,
                    color=C_TEXT, transform=ax.transAxes, zorder=4)


# ─────────────────────────────────────────────────────────────
# Page 1: 분석 목적 + 검정 방법
# ─────────────────────────────────────────────────────────────
def page_intro(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '피처-타겟 연관성 검정', '카이제곱 + Cramér\'s V 분석 보고서')

    # 분석 목적
    section_label(fig, 0.875, '분석 목적')
    purpose = [
        'ML 모델 성능이 전반적으로 낮게 나온 원인을 규명하고,',
        '각 피처가 타겟 변수와 실제로 얼마나 연관되어 있는지 통계적으로 검증.',
        '',
        '단순 p-value는 샘플 수(약 108만 행)가 크면 전부 유의하게 나오므로,',
        'Cramér\'s V (효과 크기)를 주요 지표로 사용.',
    ]
    y0 = 0.840
    for line in purpose:
        fig.text(0.07, y0, line, ha='left', va='top', fontsize=9.5, color=C_TEXT)
        y0 -= 0.026

    # 검정 방법
    section_label(fig, 0.685, '검정 방법')

    # 카이제곱 설명
    fig.text(0.07, 0.655, '① 카이제곱 독립성 검정', ha='left', va='top',
             fontsize=10, fontweight='bold', color='#2c5f8a')
    fig.text(0.07, 0.625, '두 범주형 변수가 서로 독립인지 검정 (관측 빈도 vs 기대 빈도 비교)',
             ha='left', va='top', fontsize=9, color=C_TEXT)

    # Cramér's V 설명
    fig.text(0.07, 0.588, "② Cramér's V (효과 크기)", ha='left', va='top',
             fontsize=10, fontweight='bold', color='#2c5f8a')
    fig.text(0.07, 0.558,
             "카이제곱은 n에 비례해 커지므로, n과 카테고리 수로 정규화 → 0~1 스케일로 비교 가능.",
             ha='left', va='top', fontsize=9, color=C_TEXT)
    fig.text(0.07, 0.530,
             "V = sqrt( χ² / (n × min(r-1, c-1)) )    r: 피처 카테고리 수  |  c: 타겟 카테고리 수",
             ha='left', va='top', fontsize=9, color=C_TEXT)

    # V 기준 테이블
    ax_v = fig.add_axes([0.07, 0.395, 0.86, 0.115])
    draw_table(ax_v,
               ['V 범위', '연관 강도'],
               [['0.00 ~ 0.10', '거의 무관계'],
                ['0.10 ~ 0.20', '약한 연관'],
                ['0.20 ~ 0.30', '중간 연관'],
                ['0.30 이상',   '강한 연관']],
               col_widths=[0.4, 0.6], row_height=0.185, font_size=9)

    # 데이터 설계
    section_label(fig, 0.365, '데이터 설계')
    ax_d = fig.add_axes([0.07, 0.205, 0.86, 0.145])
    draw_table(ax_d,
               ['구분', '조건', '행수'],
               [['전체',        '분류불능 제외',              '1,089,169'],
                ['업무상질병',   '발생형태 == 업무상질병',      '179,570'],
                ['건설업',      '대업종 == 건설업',            '271,176'],
                ['건설업+질병', '건설업 중 업무상질병',        '42,371']],
               col_widths=[0.22, 0.50, 0.28], row_height=0.17, font_size=9)

    # 피처/타겟
    section_label(fig, 0.178, '피처 및 타겟')
    fig.text(0.07, 0.148,
             '피처: 대업종 · 년도 · 규모 · 성별 · 연령 · 근무기간 · 지역 · 건설공사금액(건설업 한정)',
             ha='left', va='top', fontsize=9, color=C_TEXT)
    fig.text(0.07, 0.122,
             '타겟: 발생형태(전체) · 재해정도(전체) · 질병종류(업무상질병) · 세부질병종류(업무상질병)',
             ha='left', va='top', fontsize=9, color=C_TEXT)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 2: 결과 테이블 + 피처별 해석
# ─────────────────────────────────────────────────────────────
def page_results(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '분석 결과')

    # 결과 테이블
    section_label(fig, 0.875, "Cramér's V 전체 결과  (전 조합 p < 0.001 ***)")
    ax_r = fig.add_axes([0.04, 0.67, 0.92, 0.195])
    draw_table(ax_r,
               ['피처', '발생형태', '재해정도', '질병종류', '세부질병종류', '비고'],
               [['성별',       '0.346 ★', '0.139', '0.271', '0.330 ★', '전체'],
                ['대업종',     '0.251',   '0.170', '0.242', '0.250',   '전체'],
                ['근무기간',   '0.150',   '0.109', '0.162', '0.172',   '전체'],
                ['연령',       '0.122',   '0.115', '0.187', '0.198',   '전체'],
                ['규모',       '0.117',   '0.059', '0.094', '0.103',   '전체'],
                ['건설공사금액','0.107',   '0.036', '0.038', '0.058',   '건설업만'],
                ['지역',       '0.071',   '0.097', '0.172', '0.135',   '전체'],
                ['년도',       '0.052',   '0.032', '0.079', '0.102',   '전체']],
               col_widths=[0.18, 0.14, 0.13, 0.13, 0.155, 0.165],
               row_height=0.095, font_size=8.5)

    # 피처별 해석
    section_label(fig, 0.635, '피처별 해석')

    items = [
        ('성별  V = 0.346  —  가장 강함',
         ['성별 자체보다 직종 분리(occupational segregation)를 반영.',
          '남성: 건설·제조·중장비 → 떨어짐·끼임·물체에 맞음 집중',
          '여성: 서비스·의료·사무  → 넘어짐·업무상질병·불균형동작 집중',
          '데이터에 실제 직종 피처가 없어 성별이 그 역할을 흡수.'], '#c0392b'),

        ('대업종  V = 0.251',
         ['업종마다 작업환경이 달라 사고 패턴 차이 존재.',
          '성별보다 낮은 이유: 업종 내부에서도 다양한 사고 유형이 혼재.'], '#1a5276'),

        ('근무기간  V = 0.150  /  연령  V = 0.122',
         ['신입 vs 베테랑, 연령대별 사고 패턴 차이 존재하나 설명력은 제한적.'], '#145a32'),

        ('규모  V = 0.117  /  건설공사금액  V = 0.107',
         ['소규모 사업장 안전 관리 부족, 공사 규모별 작업 유형 차이 반영.'], '#6c3483'),

        ('지역  V = 0.071  /  년도  V = 0.052  —  가장 약함',
         ['지역은 지역별 주요 업종 분포를 간접 반영하는 수준.',
          '년도: 시간이 지나도 사고 패턴이 거의 변하지 않음 → 정상(stationary).'], '#784212'),
    ]

    y0 = 0.605
    for title, lines, color in items:
        fig.text(0.06, y0, f'▶  {title}', ha='left', va='top',
                 fontsize=9.5, fontweight='bold', color=color)
        y0 -= 0.028
        for line in lines:
            fig.text(0.09, y0, f'• {line}', ha='left', va='top',
                     fontsize=8.8, color=C_TEXT)
            y0 -= 0.024
        y0 -= 0.010

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 3: 히트맵 이미지 + 결론
# ─────────────────────────────────────────────────────────────
def page_heatmap_conclusion(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, "Cramér's V 히트맵 및 결론")

    # 히트맵 이미지
    section_label(fig, 0.875, "Cramér's V 히트맵")
    if os.path.exists(IMG_PATH):
        img = plt.imread(IMG_PATH)
        ax_img = fig.add_axes([0.05, 0.53, 0.90, 0.335])
        ax_img.imshow(img)
        ax_img.axis('off')
    else:
        fig.text(0.50, 0.68, '[히트맵 이미지 없음 — chi_square.py 먼저 실행]',
                 ha='center', va='center', fontsize=10, color='#888888')

    # 결론
    section_label(fig, 0.495, '결론')

    conclusions = [
        ('① ML 성능 저하의 통계적 근거',
         '모든 피처의 V ≤ 0.35 — 어떤 피처도 타겟을 강하게 결정하지 못함.\n'
         'ML 모델 F1-macro 5~22%는 모델 문제가 아니라 피처셋 자체의 정보 부족.'),
        ('② 피처 중요도 순위',
         '성별 > 대업종 > 근무기간 > 연령 > 규모 > 건설공사금액 > 지역 > 년도'),
    ]

    y0 = 0.465
    for title, body in conclusions:
        fig.text(0.06, y0, title, ha='left', va='top',
                 fontsize=10, fontweight='bold', color=C_HEAD)
        y0 -= 0.030
        for line in body.split('\n'):
            fig.text(0.08, y0, line, ha='left', va='top', fontsize=9, color=C_TEXT)
            y0 -= 0.025
        y0 -= 0.015

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
print('PDF 생성 중...')
with PdfPages(OUT_PATH) as pdf:
    meta = pdf.infodict()
    meta['Title']   = '피처-타겟 연관성 검정 보고서'
    meta['Subject'] = '카이제곱 + Cramér\'s V 분석'

    page_intro(pdf, 1)
    print('  [1/3] 분석 목적·방법·설계')
    page_results(pdf, 2)
    print('  [2/3] 결과 테이블·해석')
    page_heatmap_conclusion(pdf, 3)
    print('  [3/3] 히트맵·결론')

print(f'완료: {OUT_PATH}')
