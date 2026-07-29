"""
DB개선 데이터 설계 문서 PDF 생성
출력: DB개선/DB개선_설명서.pdf
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
OUT_PATH   = os.path.join(SCRIPT_DIR, 'DB개선_설명서.pdf')

C_HEAD = '#1e3a5f'
C_EVEN = '#f0f4fa'
C_ODD  = '#ffffff'
C_TEXT = '#1a1a2e'
C_WARN = '#7a2020'

BANNER_Y      = 0.940
BANNER_H      = 0.046
CONTENT_START = 0.878
GAP_SECTION   = 0.018
GAP_LABEL     = 0.026
GAP_TEXT      = 0.020
ROW_H_FIG     = 0.022


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
    fig.text(0.05, y, text, ha='left', va='bottom',
             fontsize=10, fontweight='bold', color=C_HEAD)


def txt(fig, y, text, color=C_TEXT, size=8.5):
    fig.text(0.06, y, text, ha='left', va='top', fontsize=size, color=color)


def place_table(fig, y_top, headers, rows, col_widths,
                font_size=8.5, row_h_fig=ROW_H_FIG, x=0.05, w=0.90):
    n = len(rows) + 1
    row_h_ax = 1.0 / n
    axes_h   = n * row_h_fig
    ax = fig.add_axes([x, y_top - axes_h, w, axes_h])
    ax.axis('off')
    x_starts = [sum(col_widths[:i]) for i in range(len(col_widths))]
    for j, (h, cw, cx) in enumerate(zip(headers, col_widths, x_starts)):
        ax.add_patch(plt.Rectangle((cx, 1.0 - row_h_ax), cw, row_h_ax,
            facecolor=C_HEAD, transform=ax.transAxes, clip_on=False, zorder=3))
        ax.text(cx + cw/2, 1.0 - row_h_ax/2, h, ha='center', va='center',
                fontsize=font_size, color='white', fontweight='bold',
                transform=ax.transAxes, zorder=4)
    for i, row in enumerate(rows):
        bg = C_EVEN if i % 2 == 0 else C_ODD
        row_y = 1.0 - (i+2) * row_h_ax
        for j, (val, cw, cx) in enumerate(zip(row, col_widths, x_starts)):
            ax.add_patch(plt.Rectangle((cx, row_y), cw, row_h_ax,
                facecolor=bg, edgecolor='#cccccc', linewidth=0.4,
                transform=ax.transAxes, clip_on=False, zorder=3))
            ax.text(cx + cw/2, row_y + row_h_ax/2, str(val),
                    ha='center', va='center', fontsize=font_size-0.5,
                    color=C_TEXT, transform=ax.transAxes, zorder=4)
    return y_top - axes_h


def draw_code_block(fig, x0, y0, w, lines, font_size=8.2, line_gap=0.021):
    total_h = len(lines) * line_gap + 0.012
    fig.add_artist(FancyBboxPatch(
        (x0, y0 - total_h), w, total_h,
        boxstyle='round,pad=0.006', facecolor='#f4f4f8', edgecolor='#cccccc', linewidth=0.6,
        transform=fig.transFigure, clip_on=False, zorder=3))
    for i, line in enumerate(lines):
        fig.text(x0+0.015, y0-0.006-i*line_gap, line,
                 ha='left', va='top', fontsize=font_size,
                 fontfamily='Malgun Gothic', color='#1e1e2e',
                 transform=fig.transFigure, zorder=4)
    return y0 - total_h


def draw_warn_box(fig, x0, y0, w, lines, font_size=8.2, line_gap=0.021):
    total_h = len(lines) * line_gap + 0.012
    fig.add_artist(FancyBboxPatch(
        (x0, y0 - total_h), w, total_h,
        boxstyle='round,pad=0.006', facecolor='#fff3f3', edgecolor='#cc4444', linewidth=0.8,
        transform=fig.transFigure, clip_on=False, zorder=3))
    for i, line in enumerate(lines):
        fig.text(x0+0.015, y0-0.006-i*line_gap, line,
                 ha='left', va='top', fontsize=font_size,
                 fontfamily='Malgun Gothic', color=C_WARN,
                 transform=fig.transFigure, zorder=4)
    return y0 - total_h


# ─────────────────────────────────────────────────────────────
# Page 1: 개요 + checklist_item_insert.sql
# ─────────────────────────────────────────────────────────────
def page1(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, 'DB개선 — SafeWork AI 데이터 설계 문서',
                'ML팀(신찬우) -> DB팀(강주호) 전달 / output/ 폴더 내 SQL 파일 3개')
    y = CONTENT_START

    sec(fig, y, '전달 파일 목록')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['파일명', '내용', '크기'],
        [['checklist_item_insert.sql', '법령 2547개 + 체크리스트 질문 835개', '~1.9 MB'],
         ['ml_encoding_map_create_insert.sql', 'ML 인코딩 기준표 (신규 테이블)', '소용량'],
         ['ml_training_data_create_insert.sql', 'ML 학습 데이터 126만 행 (신규 테이블)', '~627 MB']],
        [0.40, 0.40, 0.20], font_size=8.5)
    y -= GAP_SECTION

    sec(fig, y, 'checklist_item_insert.sql — 법령 + 체크리스트')
    y -= GAP_LABEL
    txt(fig, y, '법령 데이터가 먼저, 체크리스트가 법령을 참조하는 구조라 순서 고정.')
    y -= GAP_TEXT + 0.004

    txt(fig, y, '[1] law_article 테이블 — 법령 조항 2547개', color=C_HEAD, size=9)
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['법령명', '조항 수'],
        [['산업안전보건법', ''],
         ['산업안전보건법 시행령', ''],
         ['산업안전보건법 시행규칙', ''],
         ['산업안전보건기준에 관한 규칙', '합계 2547개'],
         ['중대재해 처벌 등에 관한 법률', '']],
        [0.70, 0.30], font_size=8.5, row_h_fig=0.018)
    y -= GAP_SECTION

    txt(fig, y, '[2] checklist_item 테이블 — 체크리스트 질문 835개', color=C_HEAD, size=9)
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['컬럼', '내용', '예시'],
        [['item_code',       '항목 식별 코드',           'SIF-CON-0001 (건설업), SIF-MFG-0001 (제조업)'],
         ['category',        '작업 종류',                '굴착 작업, 가공 설비를 이용한 작업'],
         ['question',        '체크리스트 질문',           '굴착면의 기울기가 안전 기준을 준수하고 있는가?'],
         ['description',     '관련 실제 산재 사고 요약',   '[무너짐] 2018년 OO공사 굴착사면 붕괴...'],
         ['target_industry', '업종',                     '건설업 / 제조업'],
         ['risk_weight',     '가중치 (지지 사고 건수)',    '7 (실제 7건 산재 기반)'],
         ['law_ref',         '관련 법령 article_id 목록', '"1489,1486"']],
        [0.18, 0.28, 0.54], font_size=8)
    y -= GAP_SECTION

    sec(fig, y, 'law_ref 연결 구조')
    y -= GAP_LABEL
    txt(fig, y, '실제 산재 사고 기반 체크리스트 질문마다 AI(임베딩 유사도 + LLM)가 관련 법령 매핑.')
    y -= GAP_TEXT
    y = draw_code_block(fig, 0.06, y, 0.88, [
        'law_ref = "1489,1486"',
        '→ law_article 테이블에서 article_id 1489, 1486 조회하면 해당 법령 조항 전문 확인 가능',
    ])

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 2: ML 인코딩 + 학습 데이터 + 흐름 요약
# ─────────────────────────────────────────────────────────────
def page2(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, 'ML 인코딩 + 학습 데이터 설계')
    y = CONTENT_START

    sec(fig, y, 'ml_encoding_map_create_insert.sql — 인코딩 기준표 (신규 테이블)')
    y -= GAP_LABEL
    txt(fig, y, 'ML 모델이 한글 값을 정수로 변환하는 기준. 모델 재사용 시 동일 기준 적용 필수.')
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['컬럼', '내용', '예시'],
        [['feature_name', '피처 이름',          '연령, 규모, 발생형태'],
         ['original_val', '원본 한글 값',        '40세~44세, 5~9인, 떨어짐'],
         ['encoded_val',  '정수 인코딩 값',      '5, 1, 6'],
         ['enc_type',     '인코딩 방식',         'ordinal(순서형) / label(명목형)']],
        [0.20, 0.30, 0.50], font_size=8.5)
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['피처', '방식', '클래스 수'],
        [['규모', 'ordinal', '9개'],
         ['연령', 'ordinal', '10개'],
         ['근무기간', 'ordinal', '8개'],
         ['건설공사금액', 'ordinal', '8개 (건설업 전용)'],
         ['발생형태', 'label', '23개'],
         ['질병종류', 'label', '9개'],
         ['세부질병종류', 'label', '32개'],
         ['종업종', 'label', '44개 (82개 원본명 정규화)']],
        [0.30, 0.20, 0.50], font_size=8.5)
    y -= 0.004
    txt(fig, y, '* 성별·지역·산업은 원핫 인코딩 (컬럼명 규칙: 성별_남, 지역_경기 등) — 별도 테이블 없음',
        color='#555577', size=7.8)
    y -= GAP_TEXT + GAP_SECTION

    sec(fig, y, 'ml_training_data_create_insert.sql — 학습 데이터 (신규 테이블)')
    y -= GAP_LABEL
    txt(fig, y, '인코딩까지 완료된 ML 최종 학습 데이터. 기존 ai_safework_full.sql에 없는 신규 테이블.')
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['컬럼', '내용', '예시'],
        [['dataset_type', '데이터 종류',         'accident(발생형태 예측) / disease(질병종류 예측)'],
         ['industry',     '업종',               '건설업, 제조업, 광업 등'],
         ['year',         '연도',               '2017 ~ 2025'],
         ['features',     '인코딩된 피처 (JSON)', '{"규모_enc": 1, "연령_enc": 5, ...}'],
         ['target_enc',   '예측 대상값 (정수)',   '6  (발생형태 떨어짐에 해당)'],
         ['target_col',   '예측 대상 컬럼명',    'accident_type / disease_type']],
        [0.18, 0.22, 0.60], font_size=8)
    y -= GAP_TEXT
    y = place_table(fig, y,
        ['업종', '발생형태 예측', '질병종류 예측'],
        [['건설업',          '267,808행', '41,688행'],
         ['제조업',          '272,493행', '60,941행'],
         ['광업',            '24,824행',  '23,545행'],
         ['기타의사업',       '404,326행', '43,488행'],
         ['운수·창고·통신업', '95,070행',  '7,194행'],
         ['소규모 통합',      '20,965행',  '1,880행'],
         ['합계',            '1,085,486행', '178,736행']],
        [0.35, 0.33, 0.32], font_size=8.5)
    y -= 0.004
    draw_warn_box(fig, 0.06, y, 0.88, [
        '[주의] 파일 크기 약 627MB — DB 서버 여유 공간 확인 후 적재 권장',
    ], line_gap=0.022)

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
# Page 3: 데이터 흐름 + 참고사항
# ─────────────────────────────────────────────────────────────
def page3(pdf, pnum):
    fig = plt.figure(figsize=(8.27, 11.69))
    fig.patch.set_facecolor('#f8f9fc')
    draw_banner(fig, '데이터 흐름 및 참고사항')
    y = CONTENT_START

    sec(fig, y, '전체 데이터 흐름')
    y -= GAP_LABEL
    y = draw_code_block(fig, 0.06, y, 0.88, [
        '실제 산재 사고 데이터 (SIF 건설업 2,574건 + 제조업 1,858건)',
        '    ↓  AI 분류 + LLM 체크리스트 생성',
        '체크리스트 질문 (공종/작업 x 발생형태 조합)',
        '    ↓  임베딩 유사도(sentence-transformers) + LLM 법령 매핑',
        '',
        'checklist_item_insert.sql',
        '  ├── law_article  : 2547개 법령 조항',
        '  └── checklist_item : 835개 질문 + law_ref (법령 article_id 참조)',
        '',
        'KOSHA 마이크로데이터 (2017~2025, 약 109만 행)',
        '    ↓  전처리 + 인코딩 (kosha_encodings.py 기준)',
        '',
        'ml_training_data_create_insert.sql : 126만 행 (발생형태 + 질병종류)',
        'ml_encoding_map_create_insert.sql  : 인코딩 기준표 151개',
    ], font_size=8.5, line_gap=0.024)
    y -= GAP_SECTION

    sec(fig, y, 'DB팀 참고사항')
    y -= GAP_LABEL
    y = place_table(fig, y,
        ['항목', '내용'],
        [['체크리스트 코드 충돌',
          'SIF- prefix 사용. 기존 항목(SIF- 아닌 것) 건드리지 않음'],
         ['법령 article_id',
          '기존 ai_safework_full.sql과 동일한 ID. 변경 시 law_ref 참조 전체 깨짐'],
         ['ml_training_data 재실행',
          'DELETE 후 전체 INSERT 구조 — 재실행 시 덮어쓰기'],
         ['신규 테이블',
          'ml_encoding_map, ml_training_data 2개가 ai_safework_full.sql에 없는 신규']],
        [0.28, 0.72], font_size=8.5)
    y -= GAP_SECTION

    sec(fig, y, 'FastAPI 연동 참고 (law_ref 활용)')
    y -= GAP_LABEL
    y = draw_code_block(fig, 0.06, y, 0.88, [
        '# checklist_item.law_ref = "1489,1486"',
        'law_ids = [int(x) for x in item.law_ref.split(",") if x]',
        '',
        '-- law_article 조회 예시',
        'SELECT * FROM law_article WHERE article_id = ANY(ARRAY[1489, 1486]);',
    ])

    add_page_number(fig, pnum)
    pdf.savefig(fig, bbox_inches='tight')
    plt.close(fig)


# ─────────────────────────────────────────────────────────────
print('PDF 생성 중...')
with PdfPages(OUT_PATH) as pdf:
    meta = pdf.infodict()
    meta['Title']   = 'DB개선 SafeWork AI 데이터 설계 문서'
    meta['Author']  = '산재지킴이 AI ML팀 신찬우'
    meta['Subject'] = '법령 + 체크리스트 + ML 데이터 설계'

    page1(pdf, 1)
    print('  [1/3] 개요 + checklist_item_insert.sql')
    page2(pdf, 2)
    print('  [2/3] ML 인코딩 + 학습 데이터')
    page3(pdf, 3)
    print('  [3/3] 데이터 흐름 + 참고사항')

print(f'완료: {OUT_PATH}')
