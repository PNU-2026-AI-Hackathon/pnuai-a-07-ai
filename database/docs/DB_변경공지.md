# 📣 DB 변경공지 — 사후 대응 + 사전 예측 추가

> 대상: ML · 백엔드 · 프론트 파트
> 담당: 강주호 (DB)

중간발표 지적사항(사후 행정·법률 처리 지원 / 포괄적 조언 → 구체화)을 반영해,
**사후 대응 3계층 + 사전 예측**을 DB 함수로 추가했습니다. 모두 실데이터로 동작합니다.

---

## 1. 새 테이블

| 테이블 | 내용 | 적재 |
|--------|------|------|
| `admin_procedure` | 사고 후 행정절차 7종 (작업중지·중대재해보고·현장보존·산재조사표·요양급여신청·재발방지·수사대응). 기한·기관·처벌·법적근거 포함 | `SCHEMA_10_admin.sql` 시드 |
| `policy_service` | 사업주 대상 정책·지원사업 | `fetch_policy.py` (gov24) |
| `law_precedent` | 산재·중대재해 판례 | `fetch_precedents.py` (국가법령정보) |
| `law_admin_rule` | 고시·훈령·예규 (행정규칙) | `fetch_admrules.py` (국가법령정보) |
| `accident_type_dist` | 업종×규모×지역별 재해유형 분포 (accident_case 64만 집계) | `SCHEMA_15_predict.sql` |

---

## 2. 새 함수 (백엔드/ML 호출 지점)

### ① 사전 예측 — `fn_predict_accidents(업종, 규모, 지역, top_k)`
사업장 특성을 넣으면 **예상 재해유형 top-K** 를 통계 베이스라인으로 반환.
3단계 폴백(정확 → 업종+규모 → 업종)으로 항상 결과가 나옵니다.

```sql
SELECT * FROM fn_predict_accidents('제조업', '5인 미만', '부산', 5);
-- rank | accident_type | cases | ratio  | death_ratio | match_level
--   1  | 끼임          |  963  | 0.2334 |   0.0083    | EXACT
```
반환: `rank, accident_type, cases, ratio, death_ratio, match_level`
> `ratio` = 발생 빈도, `death_ratio` = 그 유형의 사망 비율. "자주 vs 치명적" 구분 가능.

### ② 사후 조언 — `fn_accident_advice(업종, 재해유형, 중대여부)`
사고 발생 시 **행정·법률·정책 3계층 조언**을 우선순위순으로 반환.

```sql
SELECT layer, title, reason, detail, agency
FROM fn_accident_advice('제조업', '끼임', TRUE);
```
반환: `layer(행정/법률/정책), priority, title, reason, detail, agency, reference, url`

---

## 3. 파트별 참고

- **백엔드**: 위 두 함수를 그대로 호출해 결과를 API로 노출하면 됩니다. 별도 조인 불필요.
- **ML**: `accident_type_dist` 는 예측 모델의 통계 베이스라인/폴백으로 사용 가능.
- **프론트**: 예측 결과의 `death_ratio` 로 "가장 흔한 사고" vs "가장 치명적 사고" 를 구분 표기 권장.

---

## 4. 실행 순서 (기존 DB에 추가 적용 시)

`SCHEMA_10` → `11` → `12` → `13` → `14` → `15` 순서로 DBeaver(Alt+X).
수집 스크립트(`fetch_policy/precedents/admrules.py`)는 API 키를 실행 시 인자로 전달하세요
(코드에 키 없음). 자세한 건 `README.md` 참고.
