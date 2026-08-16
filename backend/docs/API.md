# SafeWork AI 백엔드 API 연동 문서

> 프론트엔드 연동용 문서입니다. 아래 예시는 전부 **실제 서버 응답을 그대로 옮긴 것**입니다.
> 마지막 갱신: 2026-08-04
>
> API 를 추가하거나 응답 형태를 바꾸면 이 문서도 같은 PR 에서 함께 갱신해 주세요.

- Base URL (로컬): `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- 인증: `/api/auth/register`, `/api/auth/login` 을 제외한 **모든 API 에 JWT 필요**
  ```
  Authorization: Bearer {accessToken}
  ```

### 배포했을 때 — API 주소와 CORS

개발 중에는 Vite 가 `/api` 를 백엔드로 프록시해 줘서 **브라우저 입장에서는 같은 주소**입니다.
그런데 프론트를 GitHub Pages 등에 올리면 프록시가 없어서 `/api/...` 가 **프론트 주소로**
날아가 404 가 납니다.

**프론트** — API 주소를 환경변수로 받아 붙여 주세요.

```ts
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';   // 개발: '' (프록시), 배포: 'https://api.example.com'
fetch(`${API_BASE}${path}`, ...)
```

**백엔드** — 아래 고정 주소로 열어 두었습니다.

```
VITE_API_BASE_URL=https://haste-denture-tree.ngrok-free.dev
```

> ⚠️ **요청에 헤더를 하나 더 넣어 주세요.**
> ```ts
> headers.set("ngrok-skip-browser-warning", "true");
> ```
> 없으면 ngrok 무료 플랜이 **경고 HTML 페이지**를 대신 돌려줍니다(JSON 파싱 실패).
> 브라우저에서 오는 요청에만 뜨는 거라 curl 로 테스트하면 안 보입니다.

CORS 기본 허용 출처입니다.

```
http://localhost:5173  ·  http://127.0.0.1:5173
https://pnu-2026-ai-hackathon.github.io
https://*.trycloudflare.com  ·  https://*.ngrok-free.dev  ·  https://*.ngrok-free.app
```

다른 주소가 필요하면 **코드 수정 없이** 환경변수로 넣으면 됩니다.

```bash
APP_CORS_ALLOWED_ORIGINS=https://주소1,https://주소2
```

> ⚠️ **HTTPS 페이지에서 HTTP 백엔드는 브라우저가 막습니다**(mixed content).
> GitHub Pages 는 HTTPS 라서 백엔드도 **HTTPS 로 배포**해야 합니다.
> 인증서 없이 빨리 확인하려면 Cloudflare 터널이 HTTPS 주소를 바로 줍니다.

> `Content-Disposition` 을 노출하도록 해 두었습니다. PDF 다운로드에서 파일 이름을
> 읽으려면 필요합니다.

---

## 전체 흐름

```
회원가입/로그인 → 사업장 등록 → 점검 문항 조회 → 체크리스트 제출
                                                      ↓ (제출 응답에 위험도 포함)
                                                  위험도 진단
                                                      ↓
                                                  PDF 리포트

예방 가이드 · 법령 상담은 위 흐름과 독립적으로 언제든 호출 가능

사고가 실제로 났을 때 (별도 흐름)
  사고 내용을 글로 입력 → 즉시 조치 · 법적 의무 · 행정 처리 · 처벌 안내  (8-2)
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

## 1-3. 내 정보 조회

```
GET /api/auth/me
```
**200**
```json
{ "userId": 7, "email": "boss@example.com", "name": "구현서",
  "phone": null, "role": "OWNER", "createdAt": "2026-08-04T04:23:48.746521" }
```

토큰만으로는 **화면에 이름을 띄울 수 없어서** 만들었습니다. "구현서 사장님 안녕하세요" 같은
헤더를 그릴 때 쓰시면 됩니다.

> JWT 를 프론트에서 직접 디코딩해 쓰지 마세요. 토큰 형식이 바뀌면 화면이 같이 깨집니다.

**저장해 둔 토큰이 아직 살아 있는지 확인**하는 용도로도 좋습니다. 새로고침했을 때 이걸 먼저
불러 보고 실패하면 로그인 화면으로 보내시면 됩니다.

| 상황 | 응답 |
|---|---|
| 정상 | `200` |
| 토큰 없음 · 만료 · 위조 | `403` |
| 토큰은 유효한데 사용자가 없음 (DB 초기화 등) | `400` + `"사용자를 찾을 수 없습니다"` |

`phone` 은 회원가입 때 안 넣으면 `null` 입니다. `role` 은 지금 전부 `OWNER` 입니다.
비밀번호는 해시조차 내려가지 않습니다.

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

### 코드값은 하드코딩하지 마시고 `GET /api/references` 를 쓰세요 ⭐

```
GET /api/references
```

**200**
```json
{
  "industries":  [{ "code": "제조업", "displayName": "제조업", "highRisk": true }],
  "sizeClasses": [{ "code": "5인 미만", "displayName": "5인 미만",
                    "modelSizeClass": "5인 미만", "sortOrder": 1 }],
  "regions":     [{ "code": "부산", "displayName": "부산", "target": true }],
  "accidentTypes": [{ "code": "끼임", "displayName": "끼임" }],
  "workTypes":   [{ "industry": "제조업", "workType": "프레스 작업", "itemCount": 12 }]
}
```

DB 의 코드 테이블을 그대로 내보냅니다. **전부 합쳐 120여 건이라 앱을 켤 때 한 번 받아 두고
계속 쓰시면 됩니다.**

| 필드 | 쓰임 |
|---|---|
| `industries` · `regions` | 사업장 등록 폼 셀렉트박스 |
| `sizeClasses[].sortOrder` | **이 순서대로 내려갑니다.** 프론트에서 다시 정렬하지 마세요 |
| `sizeClasses[].modelSizeClass` | ML 모델은 9종을 쓰는데 DB 는 10종입니다(20~29인·30~49인 → 20~49인). 참고용 |
| `accidentTypes` | 사고 대처 가이드(8-1), 사고 유형 재선택(8-2) |
| `workTypes[].itemCount` | 점검 문항이 업종당 수백 개라, 작업 종류로 먼저 좁힐 때 "12문항" 처럼 표시 |

참고용 현재 값입니다. **화면에는 API 응답을 쓰세요** — 값이 바뀌면 이 목록이 먼저 낡습니다.

```
industry   : 제조업, 건설업, 운수창고통신업, 전기가스증기수도사업   (4)

sizeClass  : 5인 미만, 5~9인, 10~19인, 20~29인, 30~49인,
             50~99인, 100~299인, 300~499인, 500~999인, 1,000인 이상   (10)

region     : 서울, 부산, 대구, 인천, 광주, 대전, 울산, 경기, 강원,
             충북, 충남, 전북, 전남, 경북, 경남, 제주                 (16)
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
| `workType` | — | 작업 종류로 필터. 여러 작업은 같은 파라미터를 반복 (`workType=A&workType=B`) |
| `category` | — | 재해유형으로 필터 |
| `limit` | `30` | 반환 문항 수. 최소 1개, 최대 50개 |

> ⚠️ **문항이 많습니다.** 제조업 385개 / 건설업 450개.
> 사장님께 전부 답하게 하는 건 비현실적이니 `GET /api/references`의 `workTypes`에서
> 현장 작업을 먼저 선택하고, `criticalOnly=true`와 선택한 `workType`을 함께 보내세요.
> 서버는 위험도 순으로 최대 30개만 반환합니다.

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
    "assessedAt": "2026-08-03T08:50:31.367182Z",
    "topRisks": [
      { "type": "업무상질병", "probability": 0.2117, "shap_value": 0.067 },
      { "type": "끼임", "probability": 0.1892, "shap_value": 0.041 }
    ],
    "severityPrediction": [
      { "label": "6개월 이상", "probability": 0.3438 }
    ]
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
| `method` | `COLDSTART`(통계만) / `HYBRID`(통계 + ML 예측) |
| `submissionId` | 이 진단의 근거가 된 제출 |
| `topRisks` | **ML 예측** — 어떤 재해가 날 가능성이 높은지 (`probability` 0~1) |
| `severityPrediction` | **ML 예측** — 사고가 나면 얼마나 심각할지 (요양 기간) |

> **점수와 예측은 출처가 다릅니다.**
> `riskScore`·`riskGrade`·`baseComponent`·`checklistComponent` 는 **DB 통계 함수**가,
> `topRisks`·`severityPrediction` 은 **ML 서버(LightGBM)** 가 만듭니다.
>
> ML 서버를 못 쓰면 `topRisks`/`severityPrediction` 이 **빈 배열**로 오고 `method` 는
> `COLDSTART` 로 남습니다. 점수는 그대로 나오므로 화면이 비지는 않습니다.

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

# 6. 검색 (법령 · 유사 사례)

## 6-1. 법령 검색

```
GET /api/laws/search?q={질문}&size=5
```

산업안전보건 법령에서 관련 조문을 찾습니다. **일상어로 물어봐도 됩니다** —
"떨어질 것 같아요" 같은 표현을 법률용어("추락")로 확장해서 검색합니다.

| 쿼리 파라미터 | 필수 | 기본값 | 범위 |
|---|:---:|:---:|---|
| `q` | ✅ | — | 질문 또는 키워드 |
| `size` | ❌ | 5 | 1~20 |

**200**
```json
{
  "query": "사다리에서 떨어질 것 같아요",
  "mode": "HYBRID",
  "searchTerms": ["사다리", "떨어질", "추락", "떨어짐"],
  "totalCount": 5,
  "results": [
    { "articleId": 2053, "lawName": "산업안전보건기준에 관한 규칙",
      "articleNo": "제42조", "clauseNo": "제4항", "title": "추락의 방지",
      "content": "...", "source": "KEYWORD", "score": null, "matchedTerms": 2 },
    { "articleId": 1573, "lawName": "산업안전보건기준에 관한 규칙",
      "articleNo": "제24조", "clauseNo": null, "title": "사다리식 통로 등의 구조",
      "content": "...", "source": "SEMANTIC", "score": 0.417, "matchedTerms": null }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `mode` | `HYBRID`(키워드+의미 검색) / `KEYWORD`(ML 서버를 못 쓸 때) |
| `source` | 이 결과를 찾아낸 방식 — `KEYWORD` 또는 `SEMANTIC` |
| `score` | 의미 검색의 유사도(0~1). 키워드 결과면 `null` |
| `matchedTerms` | 키워드 검색에서 맞은 검색어 수. 의미 검색 결과면 `null` |
| `searchTerms` | 키워드 검색에 쓴 단어들 — **"왜 이 조문이 나왔는지" 보여주면 신뢰도가 올라갑니다** |
| `lawName` + `articleNo` | **함께 표시해야 합니다.** 같은 `제42조`라도 법령이 다르면 내용이 완전히 다릅니다 (산안법=유해위험방지계획서, 규칙=추락의 방지) |
| `clauseNo` | 항 번호. **의미 검색 결과는 항을 주지 않아 `null`** 입니다 |

### 검색 방식에 대해

키워드 검색과 의미 검색(ML 임베딩) **결과를 섞어서** 줍니다. 실제로 비교해 보니 서로 잘하는
질문이 달랐습니다.

- `"사다리에서 떨어질 것 같아요"` → 키워드가 제42조(추락의 방지)를 정확히 찾음
- `"안전관리자 꼭 둬야 하나요"` → 의미 검색만 제17조(안전관리자)를 찾아냄

한쪽만 쓰면 다른 쪽이 잘 찾던 질문이 나빠져서 둘을 함께 씁니다.

### 알아두실 점

- 결과가 0건일 수 있습니다 (`totalCount: 0`). "다른 표현으로 검색해보세요" 안내를 권합니다.
- **아직 LLM 답변 생성은 없습니다.** 관련 조문을 찾아주는 단계까지라,
  채팅 UI 보다는 **"관련 법령 찾기"** 형태가 현재 동작과 맞습니다.
- ML 서버가 꺼져 있으면 `mode: "KEYWORD"` 로 내려옵니다 — 서비스는 계속 동작합니다.

---

## 6-2. 유사 재해사례

```
GET /api/workplaces/{workplaceId}/similar-cases?size=5
```

사업장의 업종·세부업종과 비슷한 **중대재해(SIF) 사례와 재발방지 대책**을 반환합니다.
ML 서버의 임베딩 검색을 사용합니다.

**200**
```json
{
  "industry": "제조업",
  "subIndustry": "금속가공",
  "topKeywords": ["중량물(금형)", "사출성형기"],
  "totalCount": 3,
  "cases": [
    { "sifId": 308, "summary": "2019년 4월 금형공장에서 ...",
      "countermeasures": ["크레인 후크 해지장치 설치", "중량물 취급 작업계획서 작성"],
      "score": 0.727 }
  ],
  "note": null
}
```

| 필드 | 설명 |
|---|---|
| `topKeywords` | 사례에서 자주 나온 위험 키워드 |
| `countermeasures` | **배열입니다.** 원본이 한 덩어리 텍스트라 백엔드가 끊어서 전달합니다 |
| `score` | 유사도(0~1) |
| `note` | 결과가 비었을 때만 사유가 들어옵니다. 정상이면 `null` |

> ⚠️ ML 서버가 꺼져 있거나 인덱스를 만드는 중이면 `cases: []` + `note` 로 내려옵니다.
> 에러가 아니라 정상 응답이니, `note` 가 있으면 그대로 보여주시면 됩니다.
> (ML 서버는 처음 뜬 뒤 인덱스를 만드는 데 몇 분 걸립니다)

---

# 7. 법령 상담 (RAG)

질문하면 **관련 조문을 찾아 그 조문만 근거로** 답변합니다.

```
POST /api/chat/sessions                       대화 시작
GET  /api/chat/sessions                       내 대화 목록
POST /api/chat/sessions/{sessionId}/messages  질문하기
GET  /api/chat/sessions/{sessionId}/messages  대화 이력
```

## 7-1. 대화 시작

```json
POST /api/chat/sessions
{ "workplaceId": 5 }        // 선택. 없어도 됨
```
**201**
```json
{ "sessionId": "9a1b7587-...", "workplaceId": 5, "title": null,
  "createdAt": "2026-08-03T22:30:00+09:00" }
```
`title` 은 첫 질문으로 자동으로 채워집니다.

## 7-2. 질문하기 ⭐

```json
POST /api/chat/sessions/{sessionId}/messages
{ "question": "사다리에서 떨어질 것 같은데 뭘 해야 하나요?" }
```

**200 — 답변 생성 모델이 있을 때**
```json
{
  "sessionId": "9a1b7587-...",
  "question": "사다리에서 떨어질 것 같은데 뭘 해야 하나요?",
  "mode": "GENERATED",
  "answer": "안전난간을 설치하셔야 합니다. ... (산업안전보건기준에 관한 규칙 제42조)",
  "citedArticles": [ { "articleNo": "제42조", "title": "추락의 방지", "...": "..." } ],
  "note": null,
  "modelName": "gemini-flash-lite-latest"
}
```

**200 — 모델이 없을 때 (현재 상태)**
```json
{
  "mode": "RETRIEVAL_ONLY",
  "answer": null,
  "citedArticles": [ ... ],
  "note": "답변 생성 모델이 설정되지 않아 관련 법령 조문만 보여드립니다.",
  "modelName": null
}
```

### `mode` 두 가지를 모두 처리해 주세요

`GEMINI_API_KEY` 환경변수가 설정된 서버에서는 `GENERATED` 로, 없으면 `RETRIEVAL_ONLY`
(조문만)로 나옵니다. **같은 API·같은 구조라 프론트 수정은 필요 없습니다.**

화면은 이렇게 만들어 주세요.

| `mode` | 화면 |
|---|---|
| `RETRIEVAL_ONLY` | `note` 를 안내로 띄우고 `citedArticles` 를 목록으로 표시 |
| `GENERATED` | `answer` 를 말풍선으로, `citedArticles` 를 근거로 아래에 표시 |

**`citedArticles` 는 두 경우 모두 채워집니다.** 답변이 있어도 근거 조문을 함께 보여주셔야
사장님이 확인할 수 있습니다.

## 7-3. 대화 이력

```json
GET /api/chat/sessions/{sessionId}/messages
[
  { "messageId": 1, "role": "USER", "content": "사다리에서...",
    "citedArticles": [], "modelName": null, "createdAt": "..." },
  { "messageId": 2, "role": "ASSISTANT", "content": "안전난간을...",
    "citedArticles": [2053, 1573], "modelName": "gemini-flash-lite-latest", "createdAt": "..." }
]
```

`role` 은 `USER` / `ASSISTANT` / `SYSTEM`. `citedArticles` 는 근거 조문의 `articleId` 배열입니다.

> `RETRIEVAL_ONLY` 일 때는 답변이 없으므로 **질문만 이력에 남습니다.**

---

# 8. 사고 대처

사고가 났을 때 쓰는 화면입니다. 입력 방식이 두 가지입니다.

| | 입력 | 쓰는 곳 |
|---|---|---|
| **8-1** | 재해유형을 **고름** | 평상시 "우리 업종에 이런 사고가 나면?" 미리 보기 |
| **8-2** ⭐ | 사고 내용을 **글로 씀** | 실제 사고 직후. 법적 대응 · 행정 처리까지 안내 |

## 8-1. 재해유형으로 조회

```
GET /api/accident-response?accidentType={재해유형}&industry={업종}
```

사고가 났을 때 **무엇부터 해야 하는지**, 법적 의무는 무엇인지, 같은 유형의
중대재해가 어떻게 났고 어떻게 막을 수 있었는지를 한 번에 반환합니다.

| 쿼리 파라미터 | 필수 | 값 |
|---|:---:|---|
| `accidentType` | ✅ | 재해유형 — 위험도 진단의 `topAccidentType`, 예방 가이드의 `accidentType` 과 같은 어휘 |
| `industry` | ✅ | 업종 코드값 (2번 참고) |

**200**
```json
{
  "accidentType": "떨어짐",
  "industry": "건설업",
  "disclaimer": "아래 절차는 산업안전보건법상 사업주 의무를 정리한 참고 자료입니다. ...",
  "actions": [
    { "step": 1, "title": "작업 중지 · 근로자 대피",
      "description": "즉시 해당 작업을 멈추고 주변 근로자를 안전한 곳으로 대피시킵니다. ...",
      "legalBasis": "산업안전보건법 제54조 제1항", "immediate": true },
    { "step": 2, "title": "119 신고 · 응급처치",
      "description": "119에 신고하고 ...", "legalBasis": null, "immediate": true }
  ],
  "lawBasis": [
    { "lawName": "산업안전보건기준에 관한 규칙", "articleNo": "제42조",
      "clauseNo": "제4항", "title": "추락의 방지", "referencedBy": 12 }
  ],
  "similarCases": [
    { "sifId": 1234, "accidentKind": "추락",
      "summary": "2019년 03월경 ○○ 현장 1층에서 피재자가 ...",
      "highRiskSituation": "...", "causalFactor": "...",
      "countermeasures": [
        "추락할 위험이 있는 개구부에는 안전난간 또는 덮개 등을 ... 튼튼하게 설치",
        "개구부 덮개를 설치할 경우 뒤집히거나 떨어지지 않도록 견고히 설치"
      ] }
  ],
  "similarCaseNote": null
}
```

| 필드 | 설명 |
|---|---|
| `disclaimer` | **반드시 화면에 노출해 주세요.** 법률 자문이 아니라는 안내입니다 |
| `actions[].legalBasis` | 법정 의무면 근거 조문, 실무 단계면 `null` |
| `actions[].immediate` | `true` = 사고 직후 즉시, `false` = 이후 처리 (탭이나 섹션을 나누면 좋습니다) |
| `lawBasis[].referencedBy` | 이 조문을 근거로 삼는 점검항목 수 — 관련도 참고용 |
| `similarCases[].countermeasures` | **배열입니다.** 원본이 한 덩어리 텍스트라 항목별로 끊어 드립니다 |
| `similarCaseNote` | 사례가 비었을 때만 사유가 들어옵니다. 있으면 `null` |

### 유사 사례가 비는 경우

중대재해 사례는 **건설업 3,459건 · 제조업 2,573건** 두 업종만 정리되어 있습니다.
(제조업은 오래 재해유형이 비어 있었는데 **2026-08-04 자 데이터에서 전부 채워졌습니다.**)

그 밖의 업종은 `similarCases` 가 빈 배열이고 `similarCaseNote` 에 사유가 들어옵니다.

```json
{ "similarCases": [],
  "similarCaseNote": "중대재해 사례는 건설업·제조업만 정리되어 있어 도소매업 사례는 표시할 수 없습니다." }
```

→ `similarCases.length === 0` 이면 `similarCaseNote` 를 그대로 보여주시면 됩니다.
조치 절차와 근거 법령은 업종과 무관하게 정상 제공됩니다.

---

## 8-2. 사고 내용을 글로 적어 대처 안내 받기 ⭐

```json
POST /api/accident-response/consult
{
  "situation": "어제 오후 4시쯤 공장에서 직원이 프레스 기계에 오른손이 끼여서 손가락이 절단됐습니다. 바로 119 불러서 병원으로 실려갔고 지금 입원 중입니다.",
  "industry": "제조업",        // 선택. 있으면 유사 사례를 같은 업종에서 찾습니다
  "accidentType": null         // 선택. 사용자가 유형을 직접 고쳤을 때만 보냅니다
}
```

사고 직후에는 "떨어짐/끼임" 중에 고를 여유가 없습니다. **있었던 일을 그대로 적으면**
재해유형을 알아내고, **즉시 조치 · 법적 의무 · 행정 처리 · 위반 시 처벌** 네 덩어리로
안내합니다. 모든 항목에 근거 조문이 붙습니다.

**200**
```json
{
  "situation": "어제 오후 4시쯤 공장에서 직원이 프레스 기계에 ...",
  "accidentType": "끼임",
  "accidentTypeCertain": false,
  "selectableTypes": ["끼임", "떨어짐", "넘어짐", "물체에맞음", "..."],

  "severity": {
    "level": "SEVERE",
    "seriousAccidentLikely": true,
    "note": "적어 주신 내용에 중대재해로 이어질 수 있는 표현이 있어 ...",
    "criteria": [
      "사망자가 1명 이상 발생한 재해",
      "3개월 이상의 요양이 필요한 부상자가 동시에 2명 이상 발생한 재해",
      "부상자 또는 직업성 질병자가 동시에 10명 이상 발생한 재해"
    ],
    "criteriaBasis": "산업안전보건법 시행규칙 제3조"
  },

  "mode": "GENERATED",
  "note": null,
  "model": "gemini-flash-lite-latest",

  "immediateActions": [ /* 8-1 의 actions 와 같은 형태 */ ],

  "legalObligations": {
    "guidance": "이번 사고가 중대재해에 해당한다면 사업주는 즉시 해당 작업을 중지시키고 ... (산업안전보건법 제54조 제1항)",
    "items": [
      { "title": "즉시 작업 중지 · 근로자 대피",
        "detail": "중대재해가 발생했을 때에는 즉시 해당 작업을 중지시키고 ...",
        "deadline": "즉시", "legalBasis": "산업안전보건법 제54조 제1항" },
      { "title": "지체 없이 고용노동부 보고",
        "detail": "중대재해가 발생한 사실을 알게 되면 지체 없이 ...",
        "deadline": "지체 없이", "legalBasis": "산업안전보건법 제54조 제2항" }
    ]
  },

  "administrativeSteps": {
    "guidance": "사고 발생일로부터 1개월 이내에 근로자대표의 확인을 받은 산업재해조사표를 작성하여 ...",
    "items": [
      { "title": "산업재해조사표 제출",
        "detail": "산업재해조사표를 작성해 관할 지방고용노동관서에 제출한다.",
        "deadline": "발생일부터 1개월 이내",
        "legalBasis": "산업안전보건법 제57조 제3항 / 시행규칙 제73조",
        "agency": "관할 지방고용노동관서",
        "formName": "산업재해조사표",
        "formUrl": "https://www.moel.go.kr/policy/policydata/view.do?bbs_seq=20210600265",
        "penalty": "미제출 시 과태료 1,500만원 이하" },
      { "title": "중대재해 발생 보고",
        "detail": "재해 개요·피해상황·조치사항을 관할 지방고용노동관서에 전화·팩스 등으로 즉시 보고한다.",
        "deadline": "지체 없이",
        "legalBasis": "산업안전보건법 제54조 제2항 / 시행규칙 제67조",
        "agency": "관할 지방고용노동관서",
        "formName": null, "formUrl": null,
        "penalty": "미보고 시 과태료 3,000만원" }
    ]
  },

  "penaltyRisk": {
    "guidance": "중대재해 발생을 지체 없이 보고하지 않으면 3,000만 원의 과태료가 부과될 수 있고 ...",
    "items": [
      { "title": "산업재해 발생 사실 은폐",
        "detail": "산업재해 발생 사실을 은폐한 자, 은폐하도록 교사하거나 공모한 자는 ...",
        "deadline": null,
        "legalBasis": "산업안전보건법 제170조 제3호 (제57조 제1항 위반)",
        "agency": null, "formName": null, "formUrl": null, "penalty": null }
    ]
  },

  "relatedPrecedents": [
    { "caseName": "산업안전보건법위반·중대재해처벌등에관한법률위반(산업재해치사)",
      "court": "대법원",
      "reference": "2023도0000 2023-05-11",
      "relevance": "끼임 재해 유사 판례",
      "summary": "중대재해처벌법은 안전ㆍ보건 조치의무를 위반하여 중대산업재해에 ...",
      "url": "https://www.law.go.kr/DRF/lawService.do?..." }
  ],

  "supportPrograms": [
    { "title": "산재예방시설 융자",
      "agency": "고용노동부",
      "relevance": "사업주 지원사업",
      "summary": "현금(융자) · 산업재해보상보험에 가입한 사업주에게 사업장당 15억원 한도의 융자지원",
      "deadline": "예산 소진 시까지",
      "url": "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/149200000006" }
  ],

  "citedArticles": [
    { "articleId": 1421, "lawName": "산업안전보건법", "articleNo": "제54조",
      "clauseNo": "제1항", "title": "중대재해 발생 시 사업주의 조치",
      "content": "① 사업주는 중대재해가 발생하였을 때에는 ...",
      "source": "STATUTE", "score": null, "matchedTerms": null }
  ],
  "similarCases": [ /* 8-1 과 같은 형태 */ ],
  "similarCaseNote": "제조업 중대재해 사례는 재해유형 분류가 아직 정리되지 않아 표시할 수 없습니다.",
  "disclaimer": "아래 절차는 산업안전보건법상 사업주 의무를 정리한 참고 자료입니다. ..."
}
```

### 화면 만들 때 꼭 봐주실 것 4가지

**① 세 덩어리는 모양이 같습니다 — 컴포넌트 하나로 그리시면 됩니다**

`legalObligations` · `administrativeSteps` · `penaltyRisk` 는 전부
`{ guidance, items[] }` 입니다. `items[]` 는 **법령에서 뽑아 둔 목록이라 항상 채워지고**,
`guidance` 는 AI 가 이 사고에 맞춰 쓴 설명이라 **없을 수 있습니다(null)**.

```
guidance 가 있으면 → 위에 문단으로
items[]           → 아래에 카드/아코디언 목록 (title · detail · deadline · legalBasis)
```

| `mode` | 뜻 | 화면 |
|---|---|---|
| `GENERATED` | AI 설명까지 채워짐 | `guidance` + `items` |
| `RETRIEVAL_ONLY` | AI 를 못 씀 (`note` 에 사유) | `note` 안내 + `items` |

> **`RETRIEVAL_ONLY` 여도 화면이 비지 않습니다.** 이 화면은 사고 직후에 쓰이므로,
> 모델이 없거나 무료 쿼터가 떨어져도 의무 목록과 근거 조문은 그대로 나갑니다.

**② `accidentTypeCertain: false` 면 유형을 확인받아 주세요**

서술에 여러 유형이 섞이면(예: "끼여서 손가락이 절단") 확정하지 않습니다.

```
"끼임 사고로 보입니다. 맞나요?  [예]  [다른 유형 선택 ▾ selectableTypes]"
```
사용자가 고치면 `accidentType` 에 담아 **다시 호출**하시면 됩니다. 고른 값이 추정보다 우선합니다.

**③ `severity.criteria` 는 체크리스트로 보여주세요**

중대재해인지는 **저희가 판정하지 않습니다.** 사망자 수·요양 기간은 글에 없을 수 있어서요.
대신 판단 기준 3가지를 그대로 내려드리니, 사용자가 직접 대조하게 해주세요.

| `level` | 뜻 |
|---|---|
| `FATAL` | 사망을 시사하는 표현이 있음 |
| `SEVERE` | 입원 · 수술 · 절단 등 중한 부상 표현이 있음 |
| `MINOR` | 경미한 부상만 언급됨 |
| `UNKNOWN` | 판단할 단서가 없음 |

`seriousAccidentLikely` 는 `MINOR` 일 때만 `false` 입니다. **모르면 켭니다** —
중대재해인데 안내를 안 하는 쪽이 훨씬 위험하기 때문입니다.
`false` 면 중대재해처벌법 항목이 빠져서 과잉 경고가 되지 않습니다.

**④ `disclaimer` 는 반드시 노출해 주세요**

법률 자문이 아니라 참고 자료라는 안내입니다.

### 알아두실 점

- **`citedArticles[].source`**
  - `STATUTE` — 사고가 나면 무조건 적용되는 조문(보고 의무 · 조사표 · 벌칙)을 **조문번호로 직접** 가져온 것.
    사고 서술에는 "조사표" 같은 말이 없어 검색으로는 안 걸리기 때문입니다.
  - `KEYWORD` / `SEMANTIC` — 사고 내용으로 **검색해서** 찾은 조문 (6-1 과 동일)
- **과태료 금액은 행정 절차 항목(`administrativeSteps.items[].penalty`)에 들어 있습니다.**
  미보고 3,000만원 · 조사표 미제출 1,500만원 이하 등입니다. AI 설명도 이 값을 근거로 씁니다.
- **형사처벌 조문은 금액 없이 조문만 인용합니다.** 법령 데이터에 산업안전보건법 제168조 ·
  제170조의 형량 문장이 빠져 있어서 지어내지 않습니다. 금액이 나오는 건
  중대재해처벌법(1년 이상 징역 / 10억원 이하, 법인 50억원 이하)뿐입니다.
- **`relatedPrecedents` 는 실제 판결문 링크입니다.** 국가법령정보센터로 연결되니
  새 탭으로 열어 주세요.
- **`supportPrograms` 는 신청 가능한 지원사업입니다.** 사고 후 재발방지에 쓸 수 있는
  융자 · 컨설팅이라 "그래서 뭘 할 수 있나"에 대한 답이 됩니다.

---

# 9. PDF 리포트

## 9-1. 생성

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

## 9-2. 다운로드

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

# 10. 에러 응답

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

# 11. TypeScript 타입

```ts
// 인증
export interface TokenResponse { accessToken: string; tokenType: string; }

export interface Me {
  userId: number; email: string; name: string;
  phone: string | null; role: 'OWNER' | 'ADMIN'; createdAt: string;
}

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
  method: 'COLDSTART' | 'HYBRID';
  riskScore: number; riskGrade: RiskGrade;
  topAccidentType: string | null;
  baseComponent: number | null;      // DB 통계
  checklistComponent: number | null; // DB 통계
  matchLevel: string | null;
  modelVersion: string; assessedAt: string;
  topRisks: TopRisk[];                     // ML 예측 (없으면 [])
  severityPrediction: SeverityPrediction[]; // ML 예측 (없으면 [])
}

export interface TopRisk { type: string; probability: number; shap_value: number | null; }
export interface SeverityPrediction { label: string; probability: number; }

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

// 법령 검색
export interface LawSearchResponse {
  query: string;
  mode: 'HYBRID' | 'KEYWORD';
  searchTerms: string[];
  totalCount: number;
  results: LawArticle[];
}

export interface LawArticle {
  articleId: number; lawName: string; articleNo: string;
  clauseNo: string | null; title: string; content: string;
  source: 'KEYWORD' | 'SEMANTIC' | 'STATUTE';  // STATUTE = 조문번호로 직접 가져옴
  score: number | null;        // SEMANTIC 일 때만
  matchedTerms: number | null; // KEYWORD 일 때만
}

// 유사 재해사례
export interface SimilarCaseResponse {
  industry: string; subIndustry: string | null;
  topKeywords: string[]; totalCount: number;
  cases: SimilarCase[];
  note: string | null;   // 비었을 때 사유
}

export interface SimilarCase {
  sifId: number; summary: string;
  countermeasures: string[]; score: number | null;
}

// 법령 상담
export interface ChatSession {
  sessionId: string; workplaceId: number | null;
  title: string | null; createdAt: string;
}

export interface AskResponse {
  sessionId: string; question: string;
  mode: 'GENERATED' | 'RETRIEVAL_ONLY';
  answer: string | null;          // RETRIEVAL_ONLY 면 null
  citedArticles: LawArticle[];    // 두 모드 모두 채워짐
  note: string | null;            // RETRIEVAL_ONLY 사유
  modelName: string | null;
}

export interface ChatMessage {
  messageId: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  citedArticles: number[];   // articleId 배열
  modelName: string | null;
  createdAt: string;
}

// 사고 대처 (8-2 서술 기반)
export interface AccidentConsultRequest {
  situation: string;          // 필수, 2000자 이내
  industry?: string;
  accidentType?: string;      // 사용자가 유형을 고쳤을 때만
}

export interface Duty {
  title: string;
  detail: string;
  deadline: string | null;    // 법이 정한 기한
  legalBasis: string | null;  // 법령 근거가 없는 실무 안내면 null
}

export interface GuidanceSection {
  guidance: string | null;    // AI 설명. RETRIEVAL_ONLY 면 null
  items: Duty[];              // 항상 채워짐
}

export interface AccidentSeverity {
  level: 'FATAL' | 'SEVERE' | 'MINOR' | 'UNKNOWN';
  seriousAccidentLikely: boolean;  // MINOR 일 때만 false
  note: string;
  criteria: string[];              // 중대재해 판단 기준 3가지
  criteriaBasis: string;
}

export interface AccidentConsultResponse {
  situation: string;
  accidentType: string;
  accidentTypeCertain: boolean;   // false 면 사용자에게 확인
  selectableTypes: string[];
  severity: AccidentSeverity;

  mode: 'GENERATED' | 'RETRIEVAL_ONLY';
  note: string | null;            // RETRIEVAL_ONLY 사유
  model: string | null;

  immediateActions: ImmediateAction[];
  legalObligations: GuidanceSection;
  administrativeSteps: GuidanceSection;
  penaltyRisk: GuidanceSection;

  citedArticles: LawArticle[];
  similarCases: AccidentSimilarCase[];
  similarCaseNote: string | null;
  disclaimer: string;
}

export interface ImmediateAction {
  step: number; title: string; description: string;
  legalBasis: string | null; immediate: boolean;
}

export interface AccidentSimilarCase {
  sifId: number; accidentKind: string; summary: string;
  highRiskSituation: string | null; causalFactor: string | null;
  countermeasures: string[];
}

// 리포트
export interface ReportCreateResponse {
  reportId: number; status: 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED';
  fileSize: number | null; generatedAt: string | null;
}
```

---

# 12. 로컬 실행

```bash
docker start safework-postgres
```
그 다음 IntelliJ 에서 `SafeworkBackendApplication` 실행 → `localhost:8080`

DB 스키마가 최신이 아니면 예방 가이드가 빈 배열로 나오거나 위험도 진단이 실패합니다.
그럴 땐 알려주세요 — `database/schema/` 의 스크립트를 순서대로 적재해야 합니다.

---

## 없어진 것 / 대신 들어간 것

- **맞춤 채용 추천은 만들지 않습니다.** `ncs_code`/`risk_ncs_mapping`/`job_posting`
  데이터가 없어서 뼈대만 만들어도 화면에 보여줄 게 없습니다.
- 대신 **8-2 사고 내용 서술 기반 대처 안내**를 넣었습니다. 사고가 났을 때
  법적 대응 · 행정 처리까지 안내하는 화면이라, 채용 추천보다 이 서비스에 훨씬 맞습니다.

## AI 답변 생성(`mode`)에 대해

7번(법령 상담)과 8-2(사고 대처)는 `GEMINI_API_KEY` 환경변수가 있으면 `GENERATED`,
없으면 `RETRIEVAL_ONLY` 로 동작합니다. **둘 다 응답 구조가 같아서 프론트 수정이 필요 없습니다.**

- 7번은 `RETRIEVAL_ONLY` 면 답변이 `null` 이고 조문만 나옵니다.
- 8-2 는 `RETRIEVAL_ONLY` 여도 **의무 목록(`items`)이 그대로 나갑니다.** 사고 직후 화면이
  비면 안 되기 때문에 목록을 법령에서 미리 뽑아 두었습니다.

> ### ⚠️ 무료 티어에는 **하루 호출 수 제한**이 있습니다
>
> 소진되면 그날은 계속 `RETRIEVAL_ONLY` 로 나옵니다(자정에 초기화). 개발 중 화면을
> 반복해서 새로고침하면 금방 닳으니, **`RETRIEVAL_ONLY` 화면도 반드시 만들어 두세요.**
> 이건 예외 상황이 아니라 **평상시에도 나올 수 있는 정상 응답**입니다.
>
> 8-2(사고 대처)는 이 경우에도 의무 목록·근거 조문·판례·지원사업이 그대로 나갑니다.
> 7번(법령 상담)은 답변이 `null` 이고 조문만 나갑니다.

> 키는 저장소·설정 어디에도 없습니다. **프론트엔드에는 절대 넣지 마세요**(브라우저에 노출됩니다).
> 자세한 규칙은 저장소 루트의 `SECURITY.md` 를 봐주세요.

필요한 필드나 형태가 있으면 미리 말씀해주세요. 만들 때 반영하겠습니다.
