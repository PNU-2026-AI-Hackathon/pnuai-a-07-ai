"""
train.py — LightGBM × Optuna 학습 통합 스크립트

6개 업종 모델 × 5개 타겟 = 30개 모델
  target-발생형태 : 발생형태_enc, 재해정도_enc
  target-질병종류 : 질병종류_enc, 세부질병종류_enc, 재해정도_enc

Optuna 서치 범위 (전 모델 동일):
  num_leaves        20 ~ 200
  max_depth          3 ~ 10
  learning_rate   0.01 ~ 0.1  (log scale)
  min_child_samples 10 ~ 100
  subsample        0.6 ~ 1.0
  colsample_bytree 0.6 ~ 1.0
  reg_alpha        0.0 ~ 1.0
  reg_lambda       0.0 ~ 1.0

Train : 통계기준년월 <= 2023
Test  : 통계기준년월 >= 2024
"""

import os, sys, io, warnings
import pandas as pd
import numpy as np
import lightgbm as lgb
import optuna
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

warnings.filterwarnings('ignore')
optuna.logging.set_verbosity(optuna.logging.WARNING)
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# ── 경로 ─────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_ROOT  = os.path.join(os.path.dirname(SCRIPT_DIR), '학습전데이터')
MODEL_DIR  = os.path.join(SCRIPT_DIR, 'models')
RESULT_DIR = os.path.join(SCRIPT_DIR, 'results')

# ── 설정 ─────────────────────────────────────────────────
TRAIN_YEAR = 2023   # 이하 train, 초과 test
N_TRIALS   = 50     # Optuna trial 수 (줄이면 빨라짐)

# ── 업종 파일 매핑 ────────────────────────────────────────
INDUSTRIES = {
    '건설업':           '건설업.csv',
    '광업':             '광업.csv',
    '기타의사업':       '기타의사업.csv',
    '운수·창고·통신업': '운수_창고_통신업.csv',
    '제조업':           '제조업.csv',
    '소규모통합':       '소규모통합.csv',
}

# ── 태스크 정의: (데이터폴더, 타겟컬럼, 그 폴더의 모든 타겟컬럼, 태스크명) ──
TASKS = [
    ('target-발생형태', '발생형태_enc',
     ['발생형태_enc', '재해정도_enc'], '발생형태'),

    ('target-발생형태', '재해정도_enc',
     ['발생형태_enc', '재해정도_enc'], '재해정도_발생형태기반'),

    ('target-질병종류', '질병종류_enc',
     ['질병종류_enc', '세부질병종류_enc', '재해정도_enc'], '질병종류'),

    ('target-질병종류', '세부질병종류_enc',
     ['질병종류_enc', '세부질병종류_enc', '재해정도_enc'], '세부질병종류'),

    ('target-질병종류', '재해정도_enc',
     ['질병종류_enc', '세부질병종류_enc', '재해정도_enc'], '재해정도_질병기반'),
]

# ── 한글 폰트 ─────────────────────────────────────────────
plt.rcParams['font.family']      = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False


# ──────────────────────────────────────────────────────────
def build_params(trial, n_classes: int) -> dict:
    return {
        'objective':         'multiclass',
        'num_class':         n_classes,
        'metric':            'multi_logloss',
        'verbosity':         -1,
        'boosting_type':     'gbdt',
        'is_unbalance':      True,
        'num_leaves':        trial.suggest_int('num_leaves', 20, 200),
        'max_depth':         trial.suggest_int('max_depth', 3, 10),
        'learning_rate':     trial.suggest_float('learning_rate', 0.01, 0.1, log=True),
        'min_child_samples': trial.suggest_int('min_child_samples', 10, 100),
        'subsample':         trial.suggest_float('subsample', 0.6, 1.0),
        'colsample_bytree':  trial.suggest_float('colsample_bytree', 0.6, 1.0),
        'reg_alpha':         trial.suggest_float('reg_alpha', 0.0, 1.0),
        'reg_lambda':        trial.suggest_float('reg_lambda', 0.0, 1.0),
    }


def train_model(df, target_col, all_target_cols, industry, task_name):
    feat_cols = [c for c in df.columns if c not in all_target_cols]

    tr = df[df['통계기준년월'] <= TRAIN_YEAR]
    te = df[df['통계기준년월'] >  TRAIN_YEAR]

    if len(tr) == 0 or len(te) == 0:
        print(f'  [건너뜀] {task_name} / {industry}: train={len(tr)} test={len(te)}')
        return None

    X_tr, y_tr = tr[feat_cols].values, tr[target_col].values.astype(int)
    X_te, y_te = te[feat_cols].values, te[target_col].values.astype(int)

    n_classes = int(df[target_col].max()) + 1

    cb = [lgb.early_stopping(50, verbose=False), lgb.log_evaluation(-1)]

    # ── Optuna ─────────────────────────────────────────────
    # Dataset을 trial마다 새로 생성 — 재사용 시 min_child_samples 변경으로 캐시 충돌 발생
    def objective(trial):
        params = build_params(trial, n_classes)
        dtrain_t = lgb.Dataset(X_tr, label=y_tr)
        dvalid_t = lgb.Dataset(X_te, label=y_te, reference=dtrain_t)
        m = lgb.train(params, dtrain_t, num_boost_round=1000,
                      valid_sets=[dvalid_t], callbacks=cb)
        pred = m.predict(X_te).argmax(axis=1)
        return f1_score(y_te, pred, average='macro', zero_division=0)

    total_models = len(TASKS) * len(INDUSTRIES)
    model_idx    = getattr(train_model, '_call_count', 0) + 1
    train_model._call_count = model_idx
    print(f'  [{model_idx}/{total_models}] {task_name} / {industry} — Optuna 탐색 중...')

    study = optuna.create_study(direction='maximize')
    study.optimize(objective, n_trials=N_TRIALS, show_progress_bar=True,
                   catch=(Exception,))

    # ── 최종 학습 ──────────────────────────────────────────
    best = {
        'objective': 'multiclass', 'num_class': n_classes,
        'metric': 'multi_logloss', 'verbosity': -1,
        'boosting_type': 'gbdt', 'is_unbalance': True,
        **study.best_params,
    }
    dtrain_f = lgb.Dataset(X_tr, label=y_tr)
    dvalid_f = lgb.Dataset(X_te, label=y_te, reference=dtrain_f)
    final = lgb.train(best, dtrain_f, num_boost_round=1000,
                      valid_sets=[dvalid_f], callbacks=cb)

    # ── 저장 ───────────────────────────────────────────────
    # LightGBM C++ save_model()은 Windows에서 한글 경로를 처리하지 못함.
    # model_to_string()으로 모델 텍스트를 뽑아 Python open()으로 직접 저장.
    safe_ind  = industry.replace('·', '_').replace(' ', '_')
    save_path = os.path.join(MODEL_DIR, f'{task_name}_{safe_ind}.txt')
    with open(save_path, 'w', encoding='utf-8') as _f:
        _f.write(final.model_to_string())

    # ── 평가 ───────────────────────────────────────────────
    pred    = final.predict(X_te).argmax(axis=1)
    acc     = accuracy_score(y_te, pred)
    prec    = precision_score(y_te, pred, average='macro',    zero_division=0)
    rec     = recall_score(y_te, pred,    average='macro',    zero_division=0)
    f1      = f1_score(y_te, pred,        average='macro',    zero_division=0)
    f1_w    = f1_score(y_te, pred,        average='weighted', zero_division=0)

    print(f'  {industry:<14}  acc={acc:.4f}  f1_macro={f1:.4f}  '
          f'f1_weighted={f1_w:.4f}  (trial {study.best_trial.number})')
    return {
        'task':        task_name,
        'industry':    industry,
        'train_rows':  len(tr),
        'test_rows':   len(te),
        'n_classes':   n_classes,
        'accuracy':    round(acc,  4),
        'precision':   round(prec, 4),
        'recall':      round(rec,  4),
        'f1_macro':    round(f1,   4),
        'f1_weighted': round(f1_w, 4),
        'best_num_leaves':        study.best_params.get('num_leaves'),
        'best_max_depth':         study.best_params.get('max_depth'),
        'best_learning_rate':     round(study.best_params.get('learning_rate', 0), 5),
        'best_min_child_samples': study.best_params.get('min_child_samples'),
    }


def plot_bar(task_name, rows):
    industries = [r['industry'] for r in rows]
    metrics    = ['accuracy', 'precision', 'recall', 'f1_macro', 'f1_weighted']
    labels     = ['Accuracy', 'Precision', 'Recall', 'F1-macro', 'F1-weighted']
    colors     = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6']

    x     = np.arange(len(industries))
    width = 0.18
    fig, ax = plt.subplots(figsize=(max(10, len(industries) * 2), 6))

    for i, (metric, label, color) in enumerate(zip(metrics, labels, colors)):
        vals = [r[metric] for r in rows]
        bars = ax.bar(x + (i - 1.5) * width, vals, width, label=label, color=color, alpha=0.85)
        for bar, v in zip(bars, vals):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.005,
                    f'{v:.3f}', ha='center', va='bottom', fontsize=7.5)

    ax.set_title(f'{task_name} — 업종별 모델 성능 비교', fontsize=14, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels(industries, fontsize=10)
    ax.set_ylim(0, 1.12)
    ax.set_ylabel('Score')
    ax.legend(loc='upper right')
    ax.grid(axis='y', alpha=0.3)

    path = os.path.join(RESULT_DIR, f'{task_name}_비교.png')
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)
    print(f'  그래프 저장: {path}')


# ──────────────────────────────────────────────────────────
all_metrics = []

for data_dir, target_col, all_target_cols, task_name in TASKS:
    print(f'\n{"="*60}')
    print(f'  태스크: {task_name}  (target: {target_col})')
    print(f'{"="*60}')

    task_rows = []
    for industry, fname in INDUSTRIES.items():
        csv_path = os.path.join(DATA_ROOT, data_dir, fname)
        if not os.path.exists(csv_path):
            print(f'  [없음] {csv_path}')
            continue

        df = pd.read_csv(csv_path, encoding='utf-8-sig')
        if target_col not in df.columns:
            continue

        row = train_model(df, target_col, all_target_cols, industry, task_name)
        if row:
            task_rows.append(row)
            all_metrics.append(row)

    if task_rows:
        # CSV 저장
        csv_out = os.path.join(RESULT_DIR, f'metrics_{task_name}.csv')
        pd.DataFrame(task_rows).to_csv(csv_out, index=False, encoding='utf-8-sig')
        print(f'  지표 저장: {csv_out}')

        # bar 그래프
        plot_bar(task_name, task_rows)

# ── 전체 요약 CSV ──────────────────────────────────────────
if all_metrics:
    summary_path = os.path.join(RESULT_DIR, 'metrics_전체.csv')
    pd.DataFrame(all_metrics).to_csv(summary_path, index=False, encoding='utf-8-sig')
    print(f'\n전체 지표 저장: {summary_path}')

print('\n학습 완료')
