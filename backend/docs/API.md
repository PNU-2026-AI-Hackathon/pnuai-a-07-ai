# SafeWork AI 백엔드 API 연동 문서

> 프론트엔드 연동용 문서입니다. 아래 예시는 전부 **실제 서버 응답을 그대로 옮긴 것**입니다.
> 마지막 갱신: 2026-08-03
>
> API 를 추가하거나 응답 형태를 바꾸면 이 문서도 같은 PR 에서 함께 갱신해 주세요.

- Base URL (로컬): `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- 인증: `/api/auth/**` 를 제외한 **모든 API 에 JWT 필요**
  ```
  Authorization: Bearer {accessToken}
  ```

---

## 전체 흐름

```
회원가입/로그인 → 사업장 등록 → 점검 문항 조회 → 체크리스트 제출
                                                      ↓ (제출 응답에 위험도 포함)
                                                  위험도 진단
                                                      ↓
                                                  PDF 리포트

예방 가이드는 위 흐름과 독립적으로 언제든 호출 가능
```

---

# 1. 인증

## 1-1. 회원가입

```
POST /api/auth/register
```
> ⚠️ `/signup` 아니고 **`/register`** 입니다.

```json
{ "email": "boss@example.com", "password": "test1234",
  "name": "구현서", "phone": "010-1234-5678" }
```

**201**
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer" }
```

## 1-2. 로그인

```
POST /api/auth/login
```
```json
{ "email": "boss@example.com", "password": "test1234" }
```
응답은 회원가입과 동일. **토큰 유효기간 1시간**입니다.

---

# 2. 사업장

## 2-1. 등록

```
POST /api/workplaces
```

```json
{ "name": "테스트금속", "industry": "제조업", "subIndustry": "금속가공",
  "sizeClass": "5인 미만", "region": "부산",
  "employeeCount": 4, "address": "부산 사상구" }
```

| 필드 | 필수 | 비고 |
|---|:---:|---|
| `name` | ✅ | 사업장명 |
| `industry` | ✅ | 아래 코드값 |
| `sizeClass` | ✅ | 아래 코드값 |
| `region` | ✅ | 아래 코드값 |
| `subIndustry` | ❌ | 자유 입력 |
| `employeeCount` | ❌ | 0 이상 |
| `address` | ❌ | |

### 코드값 (DB 와 정확히 일치해야 함 — 셀렉트박스 권장)

```
industry   : 제조업, 건설업, 운수창고통신업, 전기가스증기수도사업

sizeClass  : 5인 미만, 5~9인, 10~19인, 20~29인, 30~49인,
             50~99인, 100~299인, 300~499인, 500~999인, 1,000인 이상

region     : 서울, 부산, 대구, 인천, 광주, 대전, 울산, 경기, 강원,
             충북, 충남, 전북, 전남, 경북, 경남, 제주
```

**201**
```json
{ "id": 5, "name": "테스트금속", "industry": "제조업", "subIndustry": "금속가공",
  "region": "부산", "employeeCount": 4, "sizeClass": "5인 미만",
  "address": "부산 사상구", "createdAt": "2026-08-03T16:18:55.942818" }
```

## 2-2. 조회 / 수정

```
GET /api/workplaces           내 사업장 목록
GET /api/workplaces/{id}      상세
PUT /api/workplaces/{id}      수정 (본문은 등록과 동일)
```

---

# 3. 체크리스트

## 3-1. 점검 문항 조회

```
GET /api/workplaces/{workplaceId}/checklist-items
```

사업장 **업종에 해당하는** 문항을 반환합니다.

| 쿼리 파라미터 | 기본값 | 설명 |
|---|:---:|---|
| `criticalOnly` | `false` | `true` 면 중대 항목만 |
| `workType` | — | 작업 종류로 필터 |
| `category` | — | 재해유형으로 필터 |

> ⚠️ **문항이 많습니다.** 제조업 385개 / 건설업 450개.
> 사장님께 전부 답하게 하는 건 비현실적이니 `criticalOnly=true`(제조업 98개) 로 시작하거나,
> 예방 가이드가 알려준 상위 재해유형만 `category` 로 걸러 쓰는 걸 권합니다.

**200**
```json
[
  {
    "itemCode": "SIF-MFG-0361",
    "category": "산소결핍",
    "workType": "피트, 맨홀, 오·폐수 처리시설 등 밀폐된 장소에서 작업",
    "question": "밀폐된 장소 작업 담당자가 산소결핍, 유해가스 중독의 증상과 응급 대응 방법에 대해 교육받고 있는가?",
    "description": "[산소결핍] 2016년 6월경 상수도 맨홀 내부에서 재해자 2명이 ... 1명은 사망하고 1명은 부상",
    "riskWeight": 15.0,
    "isCritical": true
  }
]
```

| 필드 | 설명 |
|---|---|
| `itemCode` | 문항 코드 — **제출할 때 이 값을 그대로 보냅니다** |
| `category` | 재해유형 (끼임, 산소결핍 등) |
| `workType` | 작업 종류 |
| `question` | 화면에 노출할 점검 질문 |
| `description` | 실제 재해 사례 요약 — 툴팁/더보기에 쓰면 설득력 있습니다 |
| `riskWeight` | 위험 가중치 (클수록 위험) |
| `isCritical` | 중대 항목 여부 — 강조 표시 권장 |

## 3-2. 체크리스트 제출 ⭐

```
POST /api/workplaces/{workplaceId}/checklist-submissions
```

```json
{
  "responses": [
    { "itemCode": "SIF-MFG-0361", "answer": "YES" },
    { "itemCode": "SIF-MFG-0141", "answer": "NO", "note": "다음 주 조치 예정" },
    { "itemCode": "SIF-MFG-0221", "answer": "NA" }
  ]
}
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `itemCode` | ✅ | 3-1 에서 받은 값 |
| `answer` | ✅ | **`YES` / `NO` / `NA`** 셋 중 하나 (대문자) |
| `note` | ❌ | 메모 |

- `NA`(해당 없음)는 위험도 계산에서 제외됩니다.
- 같은 `itemCode` 를 두 번 보내면 400 입니다.

**201 — 제출과 동시에 위험도 진단 결과까지 돌려줍니다.** (별도 호출 불필요)

```json
{
  "submissionId": 4,
  "totalItems": 10,
  "answeredItems": 9,
  "riskAssessment": {
    "assessmentId": 3,
    "workplaceId": 5,
    "submissionId": 4,
    "method": "COLDSTART",
    "riskScore": 55.03,
    "riskGrade": "HIGH",
    "topAccidentType": "끼임",
    "baseComponent": 32.81,
    "checklistComponent": 22.22,
    "matchLevel": "EXACT",
    "modelVersion": "coldstart-v1",
    "assessedAt": "2026-08-03T08:50:31.367182Z"
  }
}
```

`answeredItems` 는 `NA` 를 뺀 실제 답변 수입니다.

---

# 4. 위험도 진단

## 4-1. 최신 결과 조회

```
GET /api/workplaces/{workplaceId}/risk-assessments/latest
```

응답은 위 `riskAssessment` 와 동일합니다.
제출이 한 번도 없으면 **400** + `"아직 위험도 진단 결과가 없습니다. 체크리스트를 먼저 제출해 주세요."`

| 필드 | 설명 |
|---|---|
| `riskScore` | **0~100 점. 높을수록 위험** |
| `riskGrade` | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `topAccidentType` | 가장 가능성 높은 재해유형 |
| `baseComponent` | 기본 점수(0~60) — 동종·동규모·동지역 재해 통계 |
| `checklistComponent` | 체크리스트 점수(0~40) — 미비 항목 가중치 |
| `matchLevel` | 통계 매칭 정확도 (`EXACT` / `INDUSTRY_SIZE` / `INDUSTRY` / `NONE`) |
| `method` | `COLDSTART`(통계) / `XGBOOST`(ML) / `HYBRID` |
| `submissionId` | 이 진단의 근거가 된 제출 |

### 등급 표시 참고

```ts
const gradeColor = {
  LOW:      { label: '양호',      color: '#15803d' },
  MEDIUM:   { label: '보통',      color: '#ca8a04' },
  HIGH:     { label: '위험',      color: '#ea580c' },
  CRITICAL: { label: '매우 위험', color: '#b91c1c' },
};
```

> `riskScore = baseComponent + checklistComponent` 이므로 스택 바 차트로 "우리 업종 기본 위험 + 우리 사업장 미비" 를 나눠 보여주면 설득력이 좋습니다.
> `matchLevel` 이 `EXACT` 가 아니면 "유사 사업장 데이터가 적어 참고치" 라는 안내를 붙이는 게 정확합니다.

---

# 5. 예방 가이드

```
GET /api/prevention-guide
```

사업장 특성으로 **예상 재해유형 + 예방 체크리스트 + 근거 법령**을 한 번에 받습니다.
(체크리스트 제출 전에도 호출 가능 — 사업장 정보만 있으면 됩니다)

| 쿼리 파라미터 | 필수 | 기본값 |
|---|:---:|:---:|
| `industry` | ✅ | — |
| `sizeClass` | ✅ | — |
| `region` | ✅ | — |
| `expectedAccidentCount` | ❌ | 3 |
| `itemsPerAccident` | ❌ | 3 |

```ts
const qs = new URLSearchParams({
  industry: '제조업', sizeClass: '5인 미만', region: '부산',
  expectedAccidentCount: '3', itemsPerAccident: '3',
});
const res = await fetch(`/api/prevention-guide?${qs}`, {
  headers: { Authorization: `Bearer ${accessToken}` },
});
```

**200**
```json
{
  "predictions": [
    {
      "rank": 1,
      "accidentType": "끼임",
      "ratio": 0.2334,
      "deathRatio": 0.0083,
      "checklist": [
        {
          "itemCode": "SIF-MFG-0271",
          "workType": "자동화 설비 작업",
          "question": "근로자들이 자동화 설비의 끼임 위험성과 안전 작업 절차에 대해 정기적으로 교육받고 있는가?",
          "riskWeight": 13.0,
          "lawBasis": ["산업안전보건법 제29조"],
          "isCritical": true
        }
      ]
    },
    { "rank": 2, "accidentType": "업무상질병",
      "ratio": 0.1718, "deathRatio": 0.0522, "checklist": [] }
  ]
}
```

### 주의할 점 3가지

**① `checklist` 가 빈 배열일 수 있습니다**
재해유형은 예측됐지만 매칭되는 점검 항목이 없는 경우입니다(위 예시의 `업무상질병`).
렌더링 전 `checklist.length` 를 확인하고, 0이면 "등록된 점검 항목이 아직 없습니다" 같은 안내를 넣어주세요.
`rank` 는 1부터 연속으로 옵니다.

**② `ratio` / `deathRatio` 는 0~1 소수**
`0.2334` → 화면에는 `23.3%`. `(ratio * 100).toFixed(1)`

**③ `lawBasis` 는 배열**
문자열 아닙니다. 0개일 수도 있습니다.

---

# 6. PDF 리포트

## 6-1. 생성

```
POST /api/workplaces/{workplaceId}/reports
```

본문 없음. **최신 위험도 진단 결과를 바탕으로 PDF 를 만듭니다.**
(진단이 없으면 400 — 체크리스트를 먼저 제출해야 합니다)

**201**
```json
{ "reportId": 6, "status": "DONE", "fileSize": 87329,
  "generatedAt": "2026-08-03T17:50:31.702+09:00" }
```

## 6-2. 다운로드

```
GET /api/reports/{reportId}/download
```

`application/pdf` 바이너리가 내려옵니다. **`Authorization` 헤더가 필요하므로 `<a href>` 로는 안 되고** fetch 로 받아서 blob 처리해야 합니다.

```ts
const res = await fetch(`/api/reports/${reportId}/download`, {
  headers: { Authorization: `Bearer ${accessToken}` },
});
const blob = await res.blob();
const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = `안전관리_진단_리포트_${reportId}.pdf`;
a.click();
URL.revokeObjectURL(url);
```

리포트 내용: ①사업장 정보 ②위험도 진단 결과 ③조치가 필요한 항목 + 근거 법령 ④예상 재해유형별 예방 조치 ⑤안내

---

# 7. 에러 응답

| 상태 | 상황 | 본문 |
|---|---|---|
| **400** | 필수값 누락 | `{"error":"입력값이 올바르지 않습니다.","fields":{"region":"지역은 필수입니다"}}` |
| **400** | 형식 오류 (`answer` 에 이상한 값 등) | `{"error":"요청 본문을 해석할 수 없습니다. 필드 형식과 허용값을 확인해 주세요."}` |
| **400** | 비즈니스 오류 | `{"error":"존재하지 않는 문항 코드입니다: XXX"}` |
| **403** | 토큰 없음/만료 | (본문 없음) |
| **500** | 서버 오류 | `{"error":"서버 내부 오류가 발생했습니다."}` → 저에게 알려주세요 |

`fields` 가 있으면 **필드명 → 메시지** 맵이라 폼 에러 표시에 바로 쓸 수 있습니다.

남의 사업장에 접근하면 403 이 아니라 **400 `"사업장을 찾을 수 없습니다."`** 가 나갑니다(존재 여부를 노출하지 않기 위함).

---

# 8. TypeScript 타입

```ts
// 인증
export interface TokenResponse { accessToken: string; tokenType: string; }

// 사업장
export interface Workplace {
  id: number; name: string; industry: string; subIndustry: string | null;
  region: string; sizeClass: string; employeeCount: number | null;
  address: string | null; createdAt: string;
}

// 체크리스트
export type Answer = 'YES' | 'NO' | 'NA';

export interface ChecklistItem {
  itemCode: string; category: string; workType: string;
  question: string; description: string | null;
  riskWeight: number; isCritical: boolean;
}

export interface ChecklistSubmitRequest {
  responses: { itemCode: string; answer: Answer; note?: string }[];
}

export interface ChecklistSubmitResponse {
  submissionId: number; totalItems: number; answeredItems: number;
  riskAssessment: RiskAssessment;
}

// 위험도
export type RiskGrade = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface RiskAssessment {
  assessmentId: number; workplaceId: number; submissionId: number | null;
  method: 'COLDSTART' | 'XGBOOST' | 'HYBRID';
  riskScore: number; riskGrade: RiskGrade;
  topAccidentType: string | null;
  baseComponent: number | null;
  checklistComponent: number | null;
  matchLevel: string | null;
  modelVersion: string; assessedAt: string;
}

// 예방 가이드
export interface PreventionGuideResponse { predictions: AccidentPrediction[]; }

export interface AccidentPrediction {
  rank: number; accidentType: string;
  ratio: number;       // 0~1
  deathRatio: number;  // 0~1
  checklist: PreventionChecklistItem[];  // 빈 배열 가능
}

export interface PreventionChecklistItem {
  itemCode: string; workType: string; question: string;
  riskWeight: number; isCritical: boolean; lawBasis: string[];
}

// 리포트
export interface ReportCreateResponse {
  reportId: number; status: 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED';
  fileSize: number | null; generatedAt: string | null;
}
```

---

# 9. 로컬 실행

```bash
docker start safework-postgres
```
그 다음 IntelliJ 에서 `SafeworkBackendApplication` 실행 → `localhost:8080`

DB 스키마가 최신이 아니면 예방 가이드가 빈 배열로 나오거나 위험도 진단이 실패합니다.
그럴 땐 알려주세요 — `database/schema/` 의 스크립트를 순서대로 적재해야 합니다.

---

## 아직 없는 API (예정)

- 유사 재해사례 (ML 서버 연동 대기)
- 법령 RAG 챗봇
- 맞춤 채용 추천
- 사고 대처 가이드

필요한 필드나 형태가 있으면 미리 말씀해주세요. 만들 때 반영하겠습니다.
