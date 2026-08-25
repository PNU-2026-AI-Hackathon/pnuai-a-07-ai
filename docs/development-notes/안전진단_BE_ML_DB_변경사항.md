# 안전진단 BE·ML·DB 변경사항

- 작성일: 2026년 8월 17일
- 대상 흐름: `사업장정보 및 점검 범위 선별 → 중대 SIF 체크리스트·최종 진단 → 유사 재해사례 → 예방가이드`

## 1. Backend 변경사항

### 1.1 SIF 체크리스트 선별 API

체크리스트 조회 API에 STEP 1에서 선택한 작업·위험 범주를 전달할 수 있는 `scope` 파라미터를 추가했다.

```http
GET /api/workplaces/{workplaceId}/checklist-items
    ?criticalOnly=true
    &scope=MACHINE_EQUIPMENT
    &scope=VEHICLE_HANDLING
    &limit=35
```

지원하는 범주는 다음과 같다.

| 범주 코드 | 화면 표시명 |
|---|---|
| `MACHINE_EQUIPMENT` | 기계·설비 작업 |
| `VEHICLE_HANDLING` | 차량·운반 작업 |
| `WORK_AT_HEIGHT` | 고소 작업 |
| `ELECTRICAL` | 전기 작업 |
| `HOT_WORK` | 화기 작업 |
| `CHEMICAL` | 화학물질 취급 |
| `CONFINED_SPACE` | 밀폐공간 작업 |
| `CONSTRUCTION` | 건설·해체 작업 |
| `STORAGE_LOGISTICS` | 적재·보관 작업 |
| `GENERAL` | 일반 작업 중심 |

### 1.2 범주와 SIF 연결 방식

작업·위험 범주와 기존 SIF 문항은 다음 값을 이용해 연결한다.

- `checklist_item.work_type`
- `checklist_item.category`
- `checklist_item.question`

예를 들어 `MACHINE_EQUIPMENT`는 기계, 설비, 자동화, 정비, 보수, 점검, 청소 등의 키워드를 포함하는 SIF와 연결된다.

잘못된 범주 코드가 전달되면 요청을 그대로 처리하지 않고 오류를 반환한다.

### 1.3 SIF 25~35개 선별 규칙

다음 우선순위로 체크리스트를 구성한다.

1. 모든 사업장에 적용할 수 있는 공통 중대문항을 포함한다.
2. 같은 업종에서 선택 범주와 일치하는 중대 SIF를 포함한다.
3. 같은 업종의 문항이 부족하면 다른 업종의 동일 작업 범주 문항을 활용한다.
4. 결과가 25개보다 적으면 해당 업종의 고위험 문항을 위험가중치 순으로 보완한다.
5. 후보가 많으면 최대 35개까지만 반환한다.

공통 중대문항은 통행, 작업환경, 정리정돈, 보호구처럼 여러 업종에서 공통으로 확인해야 하는 문항을 기준으로 한다.

실제 데이터가 25개보다 적은 테스트·초기 환경에서는 보유한 문항까지만 반환할 수 있다.

### 1.4 체크리스트 제출 및 최종 진단

체크리스트를 제출하면 다음 작업을 한 번에 처리한다.

1. 문항 응답 저장
2. `아니오` 응답 및 위험가중치 집계
3. DB 기반 콜드스타트 위험도 계산
4. ML 서버에 사업장 정보와 체크리스트 위험 신호 전달
5. 최종 위험도 진단 결과 반환

`해당 없음` 응답은 응답 기록에는 포함되지만 미비 위험 계산에서는 제외한다.

체크리스트 제출 전에는 최신 위험도 결과를 조회할 수 없도록 제한한다.

### 1.5 유사 재해사례

유사사례 검색 문맥에 다음 값을 포함하도록 변경했다.

- 사업장 업종 및 세부 업종
- 최종 진단의 최우선 재해유형
- 체크리스트에서 확인된 미비 항목
- 사업장 관련 상세 문맥

Backend는 이 문맥을 ML 서버의 `query_context`로 전달한다.

### 1.6 맞춤 예방가이드

최신 체크리스트 미비 항목을 기준으로 예방가이드를 조회하는 API를 추가했다.

```http
GET /api/workplaces/{workplaceId}/prevention-guide
```

예방가이드는 다음 정보를 제공한다.

- 재해유형별 개선 우선순위
- 미비 체크리스트 문항
- 작업유형
- 위험가중치
- 중대 항목 여부
- 관련 법령 근거

PDF 리포트에도 사업장 정보, 최종 위험도, 미비 항목 기반 예방가이드를 포함하도록 연결했다.

### 1.7 Backend 주요 파일

- `backend/src/main/java/com/safework/checklist/controller/ChecklistController.java`
- `backend/src/main/java/com/safework/checklist/repository/ChecklistItemRepository.java`
- `backend/src/main/java/com/safework/checklist/service/ChecklistScope.java`
- `backend/src/main/java/com/safework/checklist/service/ChecklistService.java`
- `backend/src/main/java/com/safework/checklist/repository/ChecklistResponseRepository.java`
- `backend/src/main/java/com/safework/ml/client/MlServerClient.java`
- `backend/src/main/java/com/safework/cases/service/SimilarCaseService.java`
- `backend/src/main/java/com/safework/prevention/controller/DiagnosisPreventionGuideController.java`
- `backend/src/main/java/com/safework/prevention/service/PreventionGuideService.java`
- `backend/src/main/java/com/safework/report/service/ReportService.java`

## 2. ML 변경사항

### 2.1 STEP 1 범주의 역할

STEP 1의 작업·위험 범주는 사고확률을 직접 계산하기 위한 값이 아니다.

이 범주는 98개 중대 SIF 중 사업장과 관련성이 높은 25~35개를 선별하는 조건으로 사용한다. 최종 ML 위험유형 비중은 체크리스트 제출 이후에만 계산한다.

### 2.2 위험 예측 요청 확장

ML 위험 예측 요청이 다음 값을 받을 수 있도록 스키마를 확장했다.

- 업종
- 세부 업종
- 사업장 규모
- 지역
- 근로자 수
- 선택적으로 전달되는 사업장 상세정보
- 체크리스트 위험 신호 `risk_signals`

`risk_signals`에는 재해유형, 미비 문항 수, 위험가중치가 포함된다.

### 2.3 위험유형 비중 계산

체크리스트가 제출된 경우 다음 정보를 조합한다.

| 구성 요소 | 반영 비중 |
|---|---:|
| 기존 ML 모델 결과 | 55% |
| 체크리스트 미비 위험 신호 | 35% |
| 사업장 프로필 휴리스틱 | 10% |

체크리스트가 없는 요청에서는 ML 모델과 사업장 프로필만 사용한다. 다만 현재 안전진단 사용자 흐름에서는 체크리스트 제출 이후에만 최종 결과를 표시한다.

산출값은 보정된 실제 사고 발생확률이 아니라 점검과 개선 우선순위를 위한 `종합 위험유형 비중`이다.

### 2.4 유사사례 검색

유사사례 검색 API에 `query_context`를 추가했다.

`query_context`가 전달되면 단순히 세부 업종만 검색하지 않고 다음 문맥을 함께 이용한다.

- 진단된 주요 재해유형
- 체크리스트 미비 항목
- 작업·설비 관련 문구

이를 통해 STEP 3의 유사사례가 실제 진단 결과와 연결되도록 했다.

### 2.5 ML 주요 파일

- `mlserver/app/models/risk_schema.py`
- `mlserver/app/services/risk_service.py`
- `mlserver/app/api/cases.py`
- `mlserver/app/services/case_service.py`

## 3. Database 변경사항

### 3.1 사업장 상세정보 컬럼

기존 상세정보 설계를 지원하기 위해 `workplace` 테이블에 다음 nullable 컬럼을 추가하는 마이그레이션을 작성했다.

| 컬럼 | 용도 |
|---|---|
| `machine_type` | 주요 기계·설비 종류 |
| `machine_count` | 기계·설비 수량 |
| `safety_device_status` | 안전장치 설치 상태 |
| `storage_location` | 주요 적재 위치 |
| `storage_method` | 적재 방식 또는 높이 |

현재 STEP 1에서는 특정 업종 편향을 줄이기 위해 이 값을 필수로 입력하지 않는다. 컬럼은 nullable로 유지하며 향후 특정 작업 범주를 선택했을 때 표시할 조건부 추가질문에 사용할 수 있다.

### 3.2 SIF 선별에 사용하는 기존 데이터

별도의 SIF 복제 테이블을 만들지 않고 기존 `checklist_item` 데이터를 사용한다.

주요 필드는 다음과 같다.

- `item_code`: SIF 문항 코드
- `target_industry`: 대상 업종
- `work_type`: 세부 작업유형
- `category`: 재해유형
- `question`: 점검 문항
- `risk_weight`: 위험가중치
- `is_critical`: 중대 문항 여부
- `is_active`: 사용 여부

현재 데이터 내 제조업 중대 SIF는 98개이며, Backend가 이 데이터의 작업유형·재해유형·질문 문구를 이용해 25~35개로 선별한다.

### 3.3 체크리스트 응답 및 위험도 연계

다음 기존 테이블과 DB 함수를 계속 사용한다.

- `checklist_submission`: 체크리스트 제출 단위
- `checklist_response`: 문항별 응답
- `risk_assessment`: 최종 위험도 결과
- 콜드스타트 진단 함수: 제출된 응답을 바탕으로 기본 위험과 체크리스트 미비 위험 계산

Backend는 체크리스트 응답 저장 후 DB 진단 함수를 호출한다. 따라서 응답 저장과 최종 위험도 계산 순서가 유지되어야 한다.

### 3.4 작업·위험 범주 저장 여부

현재 STEP 1에서 선택한 작업·위험 범주는 별도 DB 컬럼에 저장하지 않는다.

선택값은 Frontend 진단 세션에 유지되며 체크리스트 조회 시 반복 가능한 `scope` 쿼리 파라미터로 Backend에 전달된다.

향후 다음 요구가 생기면 별도 저장 구조를 검토할 수 있다.

- 진단 재접속 및 다른 기기에서 이어하기
- 과거 진단의 범주 선택 이력 조회
- 범주별 통계 분석
- 사업장별 기본 작업범주 저장

### 3.5 Database 주요 파일

- `database/schema/SCHEMA_26_diagnosis_flow.sql`
- `database/docker/init/00-load.sh`
- `database/exports/checklist_export.csv`

## 4. 검증 상태

완료된 검증:

- Backend JDK 17 기준 `compileJava`, `compileTestJava` 성공
- SIF 선별 단위 테스트 3건 성공
- 관련 후보가 많을 때 최대 35개 제한 확인
- 관련 후보가 적을 때 25개까지 보완 확인
- 해당 업종 데이터가 없을 때 타 업종 동일 범주 활용 확인
- Frontend에서 범주 3개 선택 후 예시 SIF 30개 표시 확인

추가 검증 필요:

- Docker 및 PostgreSQL을 실행한 상태에서 실제 98개 SIF 통합 조회
- 작업·위험 범주 조합별 선별 문항의 현장 적합성 검토
- 실제 체크리스트 제출부터 위험도·유사사례·예방가이드까지 전체 API 통합 테스트
- 실제 PDF 리포트 출력 확인

## 5. 현재 로컬 화면 관련 주의사항

현재 인앱 브라우저의 화면은 개발용 예시 데이터로 동작한다.

- 화면에 표시되는 30개 SIF는 미리보기용 문항이다.
- 위험도, 유사사례, 예방가이드도 개발용 예시 결과다.
- 실제 BE와 PostgreSQL을 연결하면 DB의 SIF와 진단 결과를 사용한다.
