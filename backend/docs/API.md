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
  "modelName": "gemini-2.0-flash"
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

### ⚠️ 지금은 `RETRIEVAL_ONLY` 입니다

LLM API 키가 아직 설정되지 않아 **답변 문장은 생성되지 않고 관련 조문만** 나옵니다.
키가 들어오면 **같은 API 가 그대로 답변까지** 하게 됩니다 — 프론트 수정이 필요 없도록
설계했습니다.

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
    "citedArticles": [2053, 1573], "modelName": "gemini-2.0-flash", "createdAt": "..." }
]
```

`role` 은 `USER` / `ASSISTANT` / `SYSTEM`. `citedArticles` 는 근거 조문의 `articleId` 배열입니다.

> `RETRIEVAL_ONLY` 일 때는 답변이 없으므로 **질문만 이력에 남습니다.**

---

# 8. 사고 대처 가이드

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

### ⚠️ 제조업은 현재 유사 사례가 나오지 않습니다

중대재해 사례 데이터에서 **건설업 3,459건은 재해유형이 분류돼 있지만, 제조업 2,573건은
분류가 비어 있습니다.** 그래서 제조업으로 조회하면 `similarCases` 가 빈 배열이고
`similarCaseNote` 에 사유가 들어옵니다.

```json
{ "similarCases": [],
  "similarCaseNote": "제조업 중대재해 사례는 재해유형 분류가 아직 정리되지 않아 표시할 수 없습니다." }
```

→ `similarCases.length === 0` 이면 `similarCaseNote` 를 그대로 보여주시면 됩니다.
조치 절차와 근거 법령은 업종과 무관하게 정상 제공됩니다.

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
  source: 'KEYWORD' | 'SEMANTIC';
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

## 아직 없는 API (예정)

- 맞춤 채용 추천 — `ncs_code`/`risk_ncs_mapping`/`job_posting` 데이터가 아직 없습니다.

## 키가 들어오면 켜지는 것

- **법령 상담 답변 생성**(7번) — 지금은 `RETRIEVAL_ONLY` 로 조문만 나옵니다.
  `GEMINI_API_KEY` 환경변수만 넣으면 같은 API 가 답변까지 반환합니다. **프론트 수정 불필요.**

필요한 필드나 형태가 있으면 미리 말씀해주세요. 만들 때 반영하겠습니다.
