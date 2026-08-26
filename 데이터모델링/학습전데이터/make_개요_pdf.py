"""
make_개요_pdf.py — 개요.pdf 생성
학습전데이터/ 폴더 안에서 실행: python make_개요_pdf.py
"""

import os, sys, io
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import FancyBboxPatch

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
plt.rcParams['font.family']        = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH   = os.path.join(SCRIPT_DIR, '개요.pdf')

C_HEAD  = '#1e3a5f'
C_EVEN  = '#f0f4fa'
C_ODD   = '#ffffff'
C_TEXT  = '#1a1a2e'

BANNER_Y = 0.940
BANNER_H = 0.046
CONTENT_START = 0.878   # 배너 아래 첫 섹션 시작 y
GAP_SECTION   = 0.016   # 섹션 간 여백
GAP_LABEL     = 0.024   # 섹션 제목 높이
GAP_TEXT      = 0.019   # 텍스트 한 줄 높이
ROW_H_FIG     = 0.022   # 테이블 행 1개의 figure coord 높이


def add_page_number(fig, n):
    fig.text(0.97, 0.015, str(n), ha='right', va='bottom', fontsize=9, color='#aaaaaa')


def draw_banner(fig, title, subtitle=None):
    fig.add_artist(FancyBboxPatch(
        (0.05, BANNER_Y), 0.90, BANNER_H,
        boxstyle='round,pad=0.005', facecolor=C_HEAD, edgecolor='none',
        transform=fig.transFigure, clip_on=False, zorder=5,
    ))
    fig.text(0.50, BANNER_Y + BANNER_H / 2, title,
             ha='center', va='center', fontsize=14, fontweight='bold',
             color='white', zorder=6)
    if subtitle:
        fig.text(0.50, BANNER_Y - 0.018, subtitle,
                 ha='center', va='top', fontsize=9.5, color='#556688')


def sec(fig, y, text):
    """섹션 제목 출력. y = 제목 하단 기준."""
    fig.text(0.05, y, text, ha='left', va='bottom',
             fontsize=10, fontweight='bold', color=C_HEAD)


def txt(fig, y, text, color=C_TEXT, size=8.5):
    """텍스트 한 줄 출력. y = 텍스트 상단 기준."""
    fig.text(0.06, y, text, ha='left', va='top', fontsize=size, color=color)


def place_table(fig, y_top, headers, rows, col_widths,
                font_size=8.5, row_h_fig=ROW_H_FIG, x=0.05, w=0.90):
    """
    y_top: 테이블 상단 figure y
    반환: 테이블 하단 figure y
    """
    n = len(rows) + 1          # header + data rows
    row_h_ax = 1.0 / n         # axes 좌표계에서 행 높이 (전체를 n등분)
    axes_h   = n * row_h_fig   # figure 좌표에서 테이블 전체 높이

    ax = fig.add_axes([x, y_top - axes_h, w, axes_h])
    ax.axis('off')

    x_starts = [sum(col_widths[:i]) for i in range(len(col_widths))]

    # 헤더
    for j, (h, cw, cx) in enumerate(zip(headers, col_widths, x_starts)):
        ax.add_patch(plt.Rectangle(
            (cx, 1.0 - row_h_ax), cw, row_h_ax,
            facecolor=C_HEAD, transform=ax.transAxes, clip_on=False, zorder=3,
        ))
        ax.text(cx + cw / 2, 1.0 - row_h_ax / 2, h,
                ha='center', va='center', fontsize=font_size,
                color='white', fontweight='bold',
                transform=ax.transAxes, zorder=4)

    # 데이터 행
    for i, row in enumerate(rows):
        bg = C_EVEN if i % 2 == 0 else C_ODD
        row_y = 1.0 - (i + 2) * row_h_ax
        for j, (val, cw, cx) in enumerate(zip(row, col_widths, x_starts)):
            ax.add_patch(plt.Rectangle(
                (cx, row_y), cw, row_h_ax,
                facecolor=bg, edgecolor='#cccccc', linewidth=0.4,
                transform=ax.transAxes, clip_on=False, zorder=3,
            ))
            ax.text(cx + cw / 2, row_y + row_h_ax / 2, str(val),
                    ha='center', va='center', fontsize=font_size - 0.5,
                    color=C_TEXT, transform=ax.transAxes, zorder=4)

    return y_top - axes_h


def draw_code_block(fig, x0, y0, w, lines, font_size=8.2, line_gap=0.020):
    total_h = len(lines) * line_gap + 0.010
    fig.add_artist(FancyBboxPatch(
        (x0, y0 - total_h), w, total_h,
        boxstyle='round,pad=0.006', facecolor='#f4f4f8', edgecolor='#cccccc', linewidth=0.6,
        transform=fig.transFigure, clip_on=False, zorder=3,
    ))
    for i, line in enumerate(lines):
        fig.text(x0 + 0.015, y0 - 0.005 - i * line_gap, line,
                 ha='left', va='top', fontsize=font_size,
                 fontfamily='Malgun Gothic', color='#1e1e2e',
                 transform=fig.transFigure, zorder=4)
    return y0 - total_h


# ─────────────────────────────────────────────────────────────
# Page 1: 모델 구조 + 피처 + 원핫 + Train/Test
# ─────────────────────────────────────────────────────────────
def page1(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '학습 전 데이터 전처리 개요', '피처 구성 및 인코딩 방식')

    y = CONTENT_START

    # ── 모델 구조 ──
    sec(fig, y, '모델 구조')
    y -= GAP_LABEL
    txt(fig, y, '업종 규모에 따라 대규모 5개는 개별 모델, 소규모 5개는 통합 1개 모델 (총 6개 모델)')
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['폴더', '대상 행', '타겟 컬럼'],
        [['target-발생형태/', '전체 (분류불능 제외)', '발생형태_enc, 재해정도_enc'],
         ['target-질병종류/', '발생형태 == 업무상질병 행만', '질병종류_enc, 세부질병종류_enc, 재해정도_enc']],
        [0.22, 0.28, 0.50], font_size=8.5)
    y -= GAP_SECTION

    # ── 피처 (공통) ──
    sec(fig, y, '피처 (공통)')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['컬럼', '타입', '인코딩', '비고'],
        [['통계기준년월',           '수치형', 'int 그대로',              ''],
         ['규모_enc',              '순서형', 'SIZE_ORDER (0~8)',        ''],
         ['연령_enc',              '순서형', 'AGE_ORDER (0~9)',         ''],
         ['근무기간_enc',           '순서형', 'WORK_PERIOD_ORDER (0~7)', ''],
         ['종업종_enc',            '명목형', 'SUBJOB_MAP (0~43)',       'SUBJOB_NORM 82→44 통합, categorical_feature'],
         ['건설공사금액_enc',       '순서형', 'CONST_AMT_ORDER (0~7)',   '건설업.csv 전용'],
         ['성별_남',               '원핫',   '0/1',                     '성별_여 드롭 (reference)'],
         ['지역_강원 ~ 지역_충남', '원핫',   '0/1 × 15개',             '지역_충북 드롭 (reference)']],
        [0.22, 0.09, 0.26, 0.43], font_size=8)
    y -= GAP_SECTION

    # ── 추가 피처 (소규모) ──
    sec(fig, y, '추가 피처 (소규모통합.csv 전용)')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['컬럼', '비고'],
        [['산업_농업, 산업_어업, 산업_임업, 산업_전기가스 (0/1 × 4개)',
          '산업_금융 드롭 (reference, 0000 = 금융)']],
        [0.52, 0.48], font_size=8.5)
    y -= GAP_SECTION

    # ── 피처 컬럼 수 요약 ──
    sec(fig, y, '피처 컬럼 수 요약')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['파일', '피처 수', '총 컬럼 수 (발생형태)', '총 컬럼 수 (질병종류)'],
        [['건설업.csv',              '22', '24', '25'],
         ['광업·기타·운수·제조업.csv', '21', '23', '24'],
         ['소규모통합.csv',           '25', '27', '28']],
        [0.35, 0.17, 0.24, 0.24], font_size=8.5)
    y -= GAP_SECTION

    # ── 원핫 reference ──
    sec(fig, y, '원핫 인코딩 Reference Category')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['그룹', '드롭 (reference)', '유지'],
        [['성별', '성별_여',   '성별_남 (1개)'],
         ['지역', '지역_충북', '지역_강원 ~ 지역_충남 (15개)'],
         ['산업', '산업_금융', '산업_농업·어업·임업·전기가스 (4개)']],
        [0.13, 0.28, 0.59], font_size=8.5)
    y -= 0.004
    txt(fig, y, '* 트리 기반 모델(LightGBM)에서 다중공선성은 성능에 영향 없으나, 피처 해석 명확성을 위해 reference category 드롭.',
        color='#555577', size=7.8)
    y -= GAP_TEXT + GAP_SECTION

    # ── Train / Test Split ──
    sec(fig, y, 'Train / Test Split 기준')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['구분', '연도', '행수 (발생형태 기준)'],
        [['Train', '2017~2023', '약 796,524행 (73%)'],
         ['Test',  '2024~2025', '약 288,962행 (27%)']],
        [0.15, 0.20, 0.65], font_size=8.5)
    y -= 0.004
    txt(fig, y, '* 시계열 split — 데이터 누수 없음. 통계기준년월 <= 2023 / >= 2024 로 분리.',
        color='#555577', size=7.8)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 2: 종업종 정규화 + 타겟 + Drop 통계
# ─────────────────────────────────────────────────────────────
def page2(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '종업종 정규화 · 타겟 인코딩 · Drop 통계')

    y = CONTENT_START

    # ── 종업종 정규화 ──
    sec(fig, y, '종업종 정규화 (SUBJOB_NORM)')
    y -= GAP_LABEL
    txt(fig, y, 'KOSHA는 연도별로 종업종 분류 기준을 여러 차례 변경 → 동일 업종이 최대 5가지 다른 이름으로 기록됨.')
    y -= GAP_TEXT
    txt(fig, y, 'kosha_encodings.py의 SUBJOB_NORM이 82개 원본 → 44개 통합 명칭으로 정규화, SUBJOB_MAP이 정수(0~43)로 인코딩.')
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['단계', '처리', '예시'],
        [['1단계 (SUBJOB_NORM)', '82개 원본 표기 → 44개 통합',
          '기계기구·비금속·금속제조업 등 5종 → 기계기구·금속·비금속광물제품제조업'],
         ['2단계 (SUBJOB_MAP)', '44개 통합명 → 정수',
          '기계기구·금속·비금속광물제품제조업 → 7']],
        [0.22, 0.24, 0.54], font_size=8)
    y -= 0.004
    txt(fig, y, "* LightGBM 학습 시 categorical_feature=['종업종_enc'], max_cat_threshold=64 설정으로 명목형 분기 처리.",
        color='#555577', size=7.8)
    y -= GAP_TEXT + GAP_SECTION

    # ── 타겟 변수 인코딩 ──
    sec(fig, y, '타겟 변수 인코딩')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['컬럼', '타입', '인코딩', '클래스 수'],
        [['발생형태_enc',    '명목형', 'ACCIDENT_TYPE_MAP (0~22)', '23개'],
         ['재해정도_enc',    '순서형', 'INJURY_ORDER (0~7)',       '8개'],
         ['질병종류_enc',    '명목형', 'DISEASE_MAP (0~8)',        '9개'],
         ['세부질병종류_enc', '명목형', 'DISEASE_DETAIL_MAP (0~31)', '32개']],
        [0.24, 0.10, 0.40, 0.26], font_size=8.5)
    y -= GAP_SECTION

    # ── Drop 통계 — 대규모 ──
    sec(fig, y, '분류불능 Drop 통계 — 대규모 업종')
    y -= GAP_LABEL
    txt(fig, y, '공통 drop 조건: 연령_enc == -1  OR  근무기간_enc == -1  OR  종업종_enc is NaN',
        color=C_HEAD, size=8.2)
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['업종', '원본', '공통 drop', '발생형태 drop', '건설공사금액 drop', '발생형태 최종', '질병종류 최종'],
        [['건설업',          '271,290', '25',  '139', '3,343', '267,808', '41,688'],
         ['광업',            '24,856',  '112', '32',  '0',     '24,824',  '23,545'],
         ['기타의사업',       '404,384', '102', '58',  '0',     '404,326', '43,488'],
         ['운수·창고·통신업', '95,108',  '10',  '38',  '0',     '95,070',  '7,194'],
         ['제조업',          '272,567', '75',  '74',  '0',     '272,493', '60,941']],
        [0.19, 0.11, 0.11, 0.14, 0.16, 0.165, 0.125],
        font_size=7.8)
    y -= GAP_SECTION

    # ── Drop 통계 — 소규모 ──
    sec(fig, y, '분류불능 Drop 통계 — 소규모 업종 (통합)')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['업종', '원본', 'drop', '발생형태 최종', '질병종류 최종'],
        [['농업',                   '6,038',  '1', '6,037',  '366'],
         ['어업',                   '505',    '1', '504',    '39'],
         ['임업',                   '9,055',  '0', '9,055',  '482'],
         ['전기·가스·증기·수도사업', '1,033',  '4', '1,029',  '197'],
         ['금융및보험업',            '4,340',  '0', '4,340',  '796'],
         ['소규모 합계',             '20,971', '6', '20,965', '1,880']],
        [0.30, 0.14, 0.10, 0.23, 0.23], font_size=8.5)
    y -= GAP_SECTION

    # ── 전체 합계 ──
    sec(fig, y, '전체 합계')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['원본', '총 drop', '발생형태 최종', '질병종류 최종'],
        [['1,089,532', '4,046', '1,085,486', '178,736']],
        [0.25, 0.25, 0.25, 0.25], font_size=9)
    y -= 0.004
    txt(fig, y, '* 전체 drop: 4,046행 (원본의 0.37%), 보존율 99.63%',
        color='#555577', size=7.8)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 3: 파일 구조
# ─────────────────────────────────────────────────────────────
def page3(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '파일 구조')

    y = CONTENT_START
    sec(fig, y, '학습전데이터/ 디렉토리 구조')
    y -= GAP_LABEL
    draw_code_block(fig, 0.06, y, 0.88, [
        '학습전데이터/',
        '├── kosha_encodings.py       전역 인코딩 dict (순서형·명목형 피처, 역매핑 *_INV 포함)',
        '├── preprocess.py            전처리 통합 스크립트 (1·2단계 포함)',
        '├── 개요.md                  이 파일',
        '├── target-발생형태/',
        '│   ├── 건설업.csv           267,808행  피처 24개',
        '│   ├── 광업.csv             24,824행   피처 23개',
        '│   ├── 기타의사업.csv        404,326행  피처 23개',
        '│   ├── 운수_창고_통신업.csv  95,070행   피처 23개',
        '│   ├── 제조업.csv           272,493행  피처 23개',
        '│   └── 소규모통합.csv       20,965행   피처 27개 (산업 원핫 포함)',
        '└── target-질병종류/',
        '    ├── 건설업.csv           41,688행   피처 25개',
        '    ├── 광업.csv             23,545행   피처 24개',
        '    ├── 기타의사업.csv        43,488행   피처 24개',
        '    ├── 운수_창고_통신업.csv  7,194행    피처 24개',
        '    ├── 제조업.csv           60,941행   피처 24개',
        '    └── 소규모통합.csv       1,880행    피처 28개 (산업 원핫 포함)',
    ], font_size=8.5, line_gap=0.026)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
print('PDF 생성 중...')
with PdfPages(OUT_PATH) as pdf:
    meta = pdf.infodict()
    meta['Title']   = '학습 전 데이터 전처리 개요'
    meta['Author']  = 'KOSHA 산재예방 AI'
    meta['Subject'] = '피처 구성, 인코딩, Drop 통계'

    page1(pdf, 1)
    print('  [1/3] 모델 구조 · 피처 · 원핫 · Split')
    page2(pdf, 2)
    print('  [2/3] 종업종 정규화 · 타겟 인코딩 · Drop 통계')
    page3(pdf, 3)
    print('  [3/3] 파일 구조')

print(f'완료: {OUT_PATH}')
