"""
make_guide_pdf.py — predict_guide.pdf 생성
ML모델/ 폴더 안에서 실행: python make_guide_pdf.py
"""

import os, sys, io
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import FancyBboxPatch
import numpy as np

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

plt.rcParams['font.family']        = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH   = os.path.join(SCRIPT_DIR, 'predict_guide.pdf')

C_HEAD  = '#1e3a5f'
C_EVEN  = '#f0f4fa'
C_ODD   = '#ffffff'
C_TEXT  = '#1a1a2e'
C_CODE  = '#1e1e2e'
C_CODEBG = '#f4f4f8'
C_GREEN = '#065f46'
C_GREENBG = '#ecfdf5'

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
                 ha='center', va='top', fontsize=10, color='#556688')


def section_label(fig, y, text):
    fig.text(0.05, y, text, ha='left', va='bottom',
             fontsize=10.5, fontweight='bold', color=C_HEAD)


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


def draw_code_block(fig, x0, y0, w, lines, font_size=7.8, line_gap=0.022):
    total_h = len(lines) * line_gap + 0.012
    fig.add_artist(FancyBboxPatch(
        (x0, y0 - total_h + 0.006), w, total_h,
        boxstyle='round,pad=0.006',
        facecolor=C_CODEBG, edgecolor='#cccccc', linewidth=0.6,
        transform=fig.transFigure, clip_on=False, zorder=3,
    ))
    for i, line in enumerate(lines):
        fig.text(x0 + 0.015, y0 - i * line_gap, line,
                 ha='left', va='top', fontsize=font_size,
                 fontfamily='Malgun Gothic', color='#1e1e2e',
                 transform=fig.transFigure, zorder=4)
    return y0 - total_h


def draw_tag_row(fig, x0, y, tags, font_size=8.0):
    """태그 목록을 여러 줄로 자동 줄바꿈하며 그림. 최종 y 반환."""
    TAG_H   = 0.022   # 태그 높이
    GAP_X   = 0.008   # 태그 사이 가로 여백
    LINE_Y  = 0.030   # 줄 간격
    MAX_X   = 0.94    # 오른쪽 끝 한계

    cx, cy = x0, y
    for tag in tags:
        # 한글은 영문보다 약 1.8배 넓음
        n_kor = sum(1 for c in tag if ord(c) > 0x7F)
        n_asc = len(tag) - n_kor
        w_est = n_kor * 0.012 + n_asc * 0.007 + 0.018

        if cx + w_est > MAX_X:   # 줄바꿈
            cx  = x0
            cy -= LINE_Y

        fig.add_artist(FancyBboxPatch(
            (cx, cy - TAG_H), w_est, TAG_H,
            boxstyle='round,pad=0.003',
            facecolor=C_GREENBG, edgecolor='#6ee7b7', linewidth=0.8,
            transform=fig.transFigure, clip_on=False, zorder=3,
        ))
        fig.text(cx + w_est / 2, cy - TAG_H / 2, tag,
                 ha='center', va='center', fontsize=font_size,
                 color=C_GREEN, fontweight='bold',
                 transform=fig.transFigure, zorder=4)
        cx += w_est + GAP_X

    return cy - TAG_H  # 마지막 태그의 하단 y


# ─────────────────────────────────────────────────────────────
# Page 1: 개요 + 입력 파라미터
# ─────────────────────────────────────────────────────────────
def page_overview(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, 'predict.py 사용 가이드',
                'LightGBM 기반 산재 사고 유형 확률 Top-K 예측 모듈')

    # 모듈 설명
    fig.text(0.06, 0.870,
             '학습된 LightGBM 모델로 산재 사고의 발생형태 · 재해정도 · 질병종류 · 세부질병종류 확률을 Top-K로 반환하는 예측 모듈.',
             ha='left', va='top', fontsize=9, color=C_TEXT)

    # 입력 파라미터 표
    section_label(fig, 0.836, '입력 파라미터')
    ax1 = fig.add_axes([0.05, 0.61, 0.90, 0.215])
    param_rows = [
        ['대업종',       'str',      '필수', '아래 허용값 참고'],
        ['종업종',       'str',      '필수', 'KOSHA 원본 문자열 (내부 자동 정규화)'],
        ['성별',         'str',      '필수', '남 / 여'],
        ['연령',         'str',      '필수', '예: 40세~44세'],
        ['근무기간',     'str',      '필수', '예: 1~2년 미만'],
        ['규모',         'str',      '필수', '예: 5~9인'],
        ['지역',         'str',      '필수', '예: 경기'],
        ['건설공사금액', 'str|None', '선택', '건설업 전용. 생략 시 해당없음 처리'],
        ['년도',         'int',      '선택', '예측 기준 연도 (기본값: 2024)'],
        ['top_k',        'int',      '선택', '반환할 후보 수 (기본값: 3)'],
    ]
    draw_table(ax1, ['파라미터', '타입', '필수', '설명'],
               param_rows, col_widths=[0.18, 0.12, 0.07, 0.63],
               row_height=0.092, font_size=8)

    # 반환값
    section_label(fig, 0.594, '반환값 (dict)')
    ax2 = fig.add_axes([0.05, 0.48, 0.90, 0.105])
    ret_rows = [
        ['발생형태',    'list[tuple[str, float]] | None', '사고 유형 Top-K  (23개 클래스)'],
        ['재해정도',    'list[tuple[str, float]] | None', '재해 심각도 Top-K (8개 클래스)'],
        ['질병종류',    'list[tuple[str, float]] | None', '업무상질병 유형 Top-K (7~9개 클래스)'],
        ['세부질병종류', 'list[tuple[str, float]] | None', '세부 질병 분류 Top-K (21~32개 클래스)'],
    ]
    draw_table(ax2, ['키', '타입', '설명'],
               ret_rows, col_widths=[0.15, 0.37, 0.48],
               row_height=0.115, font_size=8)

    fig.text(0.06, 0.468,
             '※ 각 항목은 (라벨, 확률) 튜플 리스트로 확률 내림차순 정렬. 해당 모델 파일 없으면 None 반환.',
             ha='left', va='top', fontsize=8, color='#666688')

    GAP = 0.012   # 섹션 제목과 태그 사이, 태그 끝과 다음 제목 사이 여백

    # 허용값 — 대업종
    y = 0.440
    section_label(fig, y, '허용값 — 대업종')
    y -= 0.030
    y = draw_tag_row(fig, 0.06, y,
                     ['건설업', '광업', '기타의사업', '운수·창고·통신업', '제조업',
                      '농업', '어업', '임업', '전기·가스·증기·수도사업', '금융및보험업'])
    y -= GAP
    fig.text(0.06, y, '* 농업·어업·임업·전기·가스·증기·수도사업·금융및보험업 → 소규모 통합 모델 적용',
             ha='left', va='top', fontsize=7.8, color='#888899')
    y -= 0.022

    # 허용값 — 연령
    y -= GAP
    section_label(fig, y, '허용값 — 연령')
    y -= 0.030
    y = draw_tag_row(fig, 0.06, y,
                     ['~19세', '20세~24세', '25세~29세', '30세~34세', '35세~39세',
                      '40세~44세', '45세~49세', '50세~54세', '55세~59세', '60세~64세', '65세~'])
    y -= GAP

    # 허용값 — 규모
    y -= GAP
    section_label(fig, y, '허용값 — 규모')
    y -= 0.030
    y = draw_tag_row(fig, 0.06, y,
                     ['1인', '2~4인', '5~9인', '10~29인', '30~49인', '50~99인', '100~299인', '300인 이상'])
    y -= GAP

    # 허용값 — 근무기간
    y -= GAP
    section_label(fig, y, '허용값 — 근무기간')
    y -= 0.030
    y = draw_tag_row(fig, 0.06, y,
                     ['1개월 미만', '1~3개월 미만', '3~6개월 미만', '6개월~1년 미만',
                      '1~2년 미만', '2~3년 미만', '3~4년 미만', '4~5년 미만', '5년 이상'])
    y -= GAP

    # 허용값 — 지역
    y -= GAP
    section_label(fig, y, '허용값 — 지역')
    y -= 0.030
    y = draw_tag_row(fig, 0.06, y,
                     ['서울', '부산', '대구', '인천', '광주', '대전', '울산', '경기',
                      '강원', '충남', '충북', '전남', '전북', '경남', '경북', '제주'])
    y -= GAP

    # 허용값 — 건설공사금액
    y -= GAP
    section_label(fig, y, '허용값 — 건설공사금액 (건설업 전용)')
    y -= 0.030
    draw_tag_row(fig, 0.06, y,
                 ['해당없음', '3억 미만', '3억~5억 미만', '5억~10억 미만', '10억~30억 미만',
                  '30억~50억 미만', '50억~100억 미만', '100억~300억 미만', '300억 이상'])

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 2: 사용 예시
# ─────────────────────────────────────────────────────────────
def page_usage(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, '사용 예시')

    # 1. Python import
    section_label(fig, 0.870, '1.  Python import 방식')
    y = draw_code_block(fig, 0.06, 0.848, 0.88, [
        'from predict import predict',
        '',
        'result = predict(',
        '    대업종   = \'제조업\',',
        '    종업종   = \'기계기구·금속·비금속광물제품제조업\',',
        '    성별     = \'남\',',
        '    연령     = \'40세~44세\',',
        '    근무기간 = \'1~2년 미만\',',
        '    규모     = \'5~9인\',',
        '    지역     = \'경기\',',
        '    top_k    = 3,',
        ')',
        '',
        'print(result[\'발생형태\'])',
        '# [(\'끼임\', 0.2878), (\'물체에맞음\', 0.1225), (\'절단베임찔림\', 0.1152)]',
    ], line_gap=0.021)

    # 2. CLI
    section_label(fig, y - 0.015, '2.  터미널 CLI 방식')
    y = draw_code_block(fig, 0.06, y - 0.038, 0.88, [
        'python predict.py \\',
        '  --대업종 제조업 \\',
        '  --종업종 "기계기구·금속·비금속광물제품제조업" \\',
        '  --성별 남 \\',
        '  --연령 "40세~44세" \\',
        '  --근무기간 "1~2년 미만" \\',
        '  --규모 "5~9인" \\',
        '  --지역 경기 \\',
        '  --top_k 3',
    ], line_gap=0.021)

    fig.text(0.06, y - 0.005,
             '건설업 추가 예시 (건설공사금액 포함):',
             ha='left', va='top', fontsize=8.5, color=C_HEAD, fontweight='bold')
    y = draw_code_block(fig, 0.06, y - 0.028, 0.88, [
        'python predict.py \\',
        '  --대업종 건설업 --종업종 건축공사업 --성별 남 \\',
        '  --연령 "50세~54세" --근무기간 "1개월 미만" \\',
        '  --규모 "5~9인" --지역 서울 \\',
        '  --건설공사금액 "5억~10억 미만"',
    ], line_gap=0.021)

    # 3. 출력 예시
    section_label(fig, y - 0.015, '3.  출력 예시')
    y = draw_code_block(fig, 0.06, y - 0.038, 0.88, [
        '발생형태:',
        '  끼임                     0.2878',
        '  물체에맞음                0.1225',
        '  절단베임찔림               0.1152',
        '재해정도:',
        '  91~180일                 0.4044',
        '  29~90일                  0.3414',
        '  6개월 이상                0.1755',
        '질병종류:',
        '  요통                     0.4936',
        '  신체부담작업               0.3628',
        '  뇌.심혈관질환              0.0878',
        '세부질병종류:',
        '  신체부담작업               0.3568',
        '  사고성 요통                0.3044',
        '  비사고성 요통              0.1887',
    ], line_gap=0.021)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 3: 내부 동작 + 모델 정보 + 주의사항
# ─────────────────────────────────────────────────────────────
def page_internals(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')

    draw_banner(fig, '내부 동작 및 주의사항')

    GAP = 0.018  # 섹션 사이 여백

    # 내부 동작 흐름
    y = 0.870
    section_label(fig, y, '내부 동작 흐름')
    y -= 0.030
    y = draw_code_block(fig, 0.06, y, 0.88, [
        '입력값',
        '  └─ 대업종 판별 → 모델 파일 suffix 결정',
        '       ├─ 소규모 (농업/어업/임업/전기가스/금융) → 소규모통합 모델',
        '       └─ 대규모 (건설/광업/기타/운수/제조)     → 업종별 개별 모델',
        '',
        '  └─ 종업종 정규화 (SUBJOB_NORM: 82개 원본 → 44개 통합)',
        '  └─ 전체 피처 인코딩 (_build_row)',
        '       ├─ 규모 / 연령 / 근무기간 / 종업종 → 순서형 정수 인코딩',
        '       ├─ 성별 / 지역                    → 원핫 인코딩',
        '       └─ 건설공사금액                   → 건설업 전용 순서형 인코딩',
        '',
        '  └─ 모델별 predict → softmax 확률 배열',
        '  └─ Top-K 추출 → 역매핑 (enc → 한글 라벨)',
        '  └─ dict 반환',
    ], line_gap=0.022)
    y -= GAP

    # 모델 파일 정보
    section_label(fig, y, '모델 파일 정보')
    y -= 0.025
    fig.text(0.06, y,
             '경로: ML모델/models/{태스크명}_{업종명}.txt  |  총 30개 (5 태스크 × 6 업종)',
             ha='left', va='top', fontsize=8.5, color=C_TEXT)
    y -= 0.018
    tbl_h = 0.135
    ax1 = fig.add_axes([0.05, y - tbl_h, 0.90, tbl_h])
    model_rows = [
        ['발생형태',             '사고 유형 분류',      '23',    '6'],
        ['재해정도_발생형태기반', '재해 심각도 (사고)',  '8',     '6'],
        ['질병종류',             '업무상질병 유형',     '7~9',   '6'],
        ['세부질병종류',         '세부 질병 분류',      '21~32', '6'],
        ['재해정도_질병기반',    '재해 심각도 (질병)',  '8',     '6'],
    ]
    draw_table(ax1, ['태스크명', '예측 대상', '클래스 수', '모델 수'],
               model_rows, col_widths=[0.30, 0.38, 0.16, 0.16],
               row_height=0.115, font_size=8.5)
    y -= tbl_h + GAP

    # 피처 구성
    section_label(fig, y, '업종별 피처 구성')
    y -= 0.018
    tbl_h2 = 0.110
    ax2 = fig.add_axes([0.05, y - tbl_h2, 0.90, tbl_h2])
    feat_rows = [
        ['건설업',      '22', '통계기준년월, 규모/연령/근무기간/종업종_enc, 건설공사금액_enc, 성별_남, 지역×15'],
        ['대규모 5개',  '21', '통계기준년월, 규모/연령/근무기간/종업종_enc, 성별_남, 지역×15'],
        ['소규모 통합', '25', '통계기준년월, 산업원핫×4, 규모/연령/근무기간/종업종_enc, 성별_남, 지역×15'],
    ]
    draw_table(ax2, ['업종', '피처 수', '피처 목록'],
               feat_rows, col_widths=[0.14, 0.10, 0.76],
               row_height=0.120, font_size=7.8)
    y -= tbl_h2 + GAP

    # 주의사항
    section_label(fig, y, '주의사항')
    y -= 0.025
    notes = [
        '① predict.py는 ML모델/ 폴더 기준으로 실행하거나 sys.path에 해당 경로가 포함되어 있어야 함.',
        '② 종업종은 KOSHA 원본 문자열을 그대로 넣으면 내부에서 자동 정규화 처리됨.',
        '③ 질병종류 / 세부질병종류는 업무상질병 데이터로만 학습된 모델 —',
        '    사고성 재해에 적용 시 참고용으로만 활용할 것.',
        '④ 확률값은 모델 예측 확률이므로 전체 합이 1.0이며,',
        '    Top-K는 그 중 상위 K개만 추출한 것 (나머지 확률 합 = 1 - 표시된 합).',
    ]
    y0 = y
    for note in notes:
        fig.text(0.06, y0, note, ha='left', va='top', fontsize=8.5, color=C_TEXT)
        y0 -= 0.026

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
print('PDF 생성 중...')
with PdfPages(OUT_PATH) as pdf:
    meta = pdf.infodict()
    meta['Title']   = 'predict.py 사용 가이드'
    meta['Author']  = 'KOSHA 산재예방 AI'
    meta['Subject'] = '산재 사고 유형 Top-K 확률 예측 모듈 가이드'

    page_overview(pdf, 1)
    print('  [1/3] 개요 · 입력 파라미터')
    page_usage(pdf, 2)
    print('  [2/3] 사용 예시')
    page_internals(pdf, 3)
    print('  [3/3] 내부 동작 · 주의사항')

print(f'완료: {OUT_PATH}')
