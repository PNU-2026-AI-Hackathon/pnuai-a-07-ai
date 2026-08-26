"""
SIF EDA 결과 시각화
EDA/ 폴더의 CSV → EDA/그래프/ 폴더에 PNG 저장
"""

import csv, os, sys
sys.stdout.reconfigure(encoding='utf-8')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

plt.rcParams['font.family']        = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False
plt.rcParams['axes.spines.top']    = False
plt.rcParams['axes.spines.right']  = False
plt.rcParams['axes.grid']          = True
plt.rcParams['grid.alpha']         = 0.25
plt.rcParams['grid.linewidth']     = 0.6

BASE    = os.path.dirname(os.path.abspath(__file__))
EDA_DIR = os.path.join(BASE, "EDA")
OUT_DIR = os.path.join(EDA_DIR, "그래프")
os.makedirs(OUT_DIR, exist_ok=True)

# 색상 (단일 시퀀셜 블루 계열)
CLR_BAR   = "#3b82f6"
CLR_MUTED = "#93c5fd"
CLR_HEAD  = "#1e3a5f"


# ── 유틸 ──────────────────────────────────────────────────────

def load_csv(filename):
    path = os.path.join(EDA_DIR, filename)
    if not os.path.exists(path):
        print(f"  [없음] {filename}")
        return None, None
    with open(path, encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        headers = next(reader)
        rows = [row for row in reader]
    return headers, rows


def save(fig, filename):
    path = os.path.join(OUT_DIR, filename)
    fig.savefig(path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    print(f"  → 저장: EDA/그래프/{filename}")


# ── 가로 막대 그래프 ──────────────────────────────────────────

def hbar(title, labels, values, filename, color=CLR_BAR, top_n=20):
    labels  = labels[:top_n]
    values  = values[:top_n]
    n       = len(labels)
    fig_h   = max(4, n * 0.42 + 1.2)

    fig, ax = plt.subplots(figsize=(10, fig_h))
    y_pos   = np.arange(n)

    bars = ax.barh(y_pos, values, height=0.65, color=color,
                   edgecolor='white', linewidth=0.8)

    # 데이터 레이블
    max_v = max(values) if values else 1
    for bar, v in zip(bars, values):
        x = bar.get_width()
        ax.text(x + max_v * 0.01, bar.get_y() + bar.get_height() / 2,
                f'{v:,}', va='center', ha='left', fontsize=8.5, color='#374151')

    ax.set_yticks(y_pos)
    ax.set_yticklabels(labels, fontsize=9)
    ax.invert_yaxis()
    ax.set_xlabel('건수', fontsize=9)
    ax.set_title(title, fontsize=13, fontweight='bold', color=CLR_HEAD, pad=12)
    ax.xaxis.set_major_formatter(ticker.FuncFormatter(lambda x, _: f'{int(x):,}'))
    ax.tick_params(axis='x', labelsize=8)
    ax.spines['left'].set_visible(False)
    ax.set_xlim(0, max_v * 1.15)

    fig.tight_layout()
    save(fig, filename)


# ── 히트맵 ────────────────────────────────────────────────────

def heatmap(title, headers, data, filename):
    row_labels = [str(r[0]) for r in data]
    col_labels = headers[1:-1]           # 마지막 '합계' 제외
    matrix     = np.array([[int(r[i]) for i in range(1, len(headers) - 1)]
                            for r in data], dtype=float)

    n_rows, n_cols = matrix.shape
    fig_h = max(5, n_rows * 0.55 + 2)
    fig_w = max(10, n_cols * 1.1 + 3)

    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    im = ax.imshow(matrix, cmap='Blues', aspect='auto')

    # 컬러바
    cbar = fig.colorbar(im, ax=ax, shrink=0.6, pad=0.02)
    cbar.ax.tick_params(labelsize=8)
    cbar.set_label('건수', fontsize=8)

    # 축 레이블
    ax.set_xticks(np.arange(n_cols))
    ax.set_yticks(np.arange(n_rows))
    ax.set_xticklabels(col_labels, fontsize=8.5, rotation=35, ha='right')
    ax.set_yticklabels(row_labels, fontsize=8.5)

    # 셀 값 표기
    thresh = matrix.max() / 2
    for i in range(n_rows):
        for j in range(n_cols):
            v = int(matrix[i, j])
            if v == 0:
                continue
            color = 'white' if matrix[i, j] > thresh else '#1e3a5f'
            ax.text(j, i, str(v), ha='center', va='center',
                    fontsize=7.5, color=color, fontweight='bold')

    ax.set_title(title, fontsize=13, fontweight='bold', color=CLR_HEAD, pad=14)
    ax.tick_params(top=False, bottom=True, labeltop=False, labelbottom=True)

    fig.tight_layout()
    save(fig, filename)


# ── 건설업 ────────────────────────────────────────────────────

def plot_construction():
    print("\n[건설업 그래프]")

    h, rows = load_csv("건설업_발생형태.csv")
    if rows:
        hbar("건설업 — 발생형태 분포",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "건설업_발생형태.png")

    h, rows = load_csv("건설업_공종.csv")
    if rows:
        hbar("건설업 — 공종별 사고 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "건설업_공종.png")

    h, rows = load_csv("건설업_작업명.csv")
    if rows:
        hbar("건설업 — 작업명별 사고 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "건설업_작업명.png")

    h, rows = load_csv("건설업_기인물.csv")
    if rows:
        hbar("건설업 — 기인물 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "건설업_기인물.png")

    h, rows = load_csv("건설업_공종×발생형태.csv")
    if rows:
        heatmap("건설업 — 공종 × 발생형태 (건수)", h, rows,
                "건설업_공종×발생형태.png")


# ── 제조업 ────────────────────────────────────────────────────

def plot_manufacturing():
    print("\n[제조업 그래프]")

    h, rows = load_csv("제조업_발생형태.csv")
    if rows:
        hbar("제조업 — 발생형태 분포",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "제조업_발생형태.png", color="#10b981")

    h, rows = load_csv("제조업_중분류.csv")
    if rows:
        hbar("제조업 — 산재업종 중분류별 사고 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "제조업_중분류.png", color="#10b981")

    h, rows = load_csv("제조업_고위험상황.csv")
    if rows:
        hbar("제조업 — 고위험작업·상황 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "제조업_고위험상황.png", color="#10b981")

    h, rows = load_csv("제조업_기인물.csv")
    if rows:
        hbar("제조업 — 기인물 빈도 (Top 20)",
             [r[0] for r in rows], [int(r[1]) for r in rows],
             "제조업_기인물.png", color="#10b981")

    h, rows = load_csv("제조업_중분류×발생형태.csv")
    if rows:
        heatmap("제조업 — 중분류 × 발생형태 (건수)", h, rows,
                "제조업_중분류×발생형태.png")


# ── 메인 ──────────────────────────────────────────────────────

if __name__ == "__main__":
    plot_construction()
    plot_manufacturing()
    print(f"\n완료. EDA/그래프/ 폴더 확인")
