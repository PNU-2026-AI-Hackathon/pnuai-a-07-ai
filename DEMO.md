# 시연 실행 가이드

SafeWork AI 를 시연하려면 **5개**가 떠 있어야 합니다. 스크립트가 순서와 대기를 알아서 처리합니다.

---

## 한 줄 요약

PowerShell 을 열고 두 줄만 입력하면 끝입니다.

```powershell
cd C:\dev\pnuai-a-07-ai\backend\.claude\worktrees\safework-ai-hackathon-4c9b65
```
```powershell
.\start-demo.ps1 -Tunnel
```

**3~5분 뒤** 아래처럼 공개 주소가 출력됩니다. 이 주소를 심사위원·팀원에게 주면 됩니다.

```
  화면      http://localhost:5173
  공개 주소  https://xxxx-xxxx-xxxx.trycloudflare.com
  Swagger   http://localhost:8080/swagger-ui/index.html
```

정리할 때는 이것만:

```powershell
.\stop-demo.ps1
```

---

## 무엇이 뜨는가

| # | 이름 | 포트 | 없으면 |
|---|---|---|---|
| 1 | **PostgreSQL** (Docker) | 5432 | 백엔드가 아예 안 뜸 |
| 2 | **백엔드** (Spring Boot) | 8080 | 화면이 전부 빔 |
| 3 | **ML 서버** (FastAPI) | 8000 | 유사 재해사례·AI 예측이 빠짐 (나머지는 동작) |
| 4 | **프론트** (Vite) | 5173 | 화면 없음 |
| 5 | **공개 주소** (터널) | — | 이 PC 에서만 접속 가능 |

### 순서가 중요한 이유

```
PostgreSQL  →  백엔드  →  프론트
                  ↑
               ML 서버
```

- 백엔드는 **Postgres 가 없으면 기동에 실패**합니다
- 프론트는 `/api` 를 백엔드로 넘기는 프록시라 **백엔드가 없으면 화면이 빕니다**

스크립트가 각 단계마다 **실제로 응답할 때까지 기다린 뒤** 다음으로 넘어갑니다.

### 예열을 왜 하는가

| | 예열 전 | 예열 후 |
|---|---|---|
| 체크리스트 제출 | 20초+ | **8초** |

ML 서버는 첫 요청에 모델과 임베딩을 올립니다. **무대에서 첫 클릭이 20초 걸리면 곤란해서** 스크립트가 미리 한 번 돌려 둡니다.

---

## 시연 당일 순서

```
□ 발표 30분 전   .\start-demo.ps1 -Tunnel
□ 출력된 공개 주소를 폰으로 한 번 열어 확인
□ 데모 계정·사업장 미리 만들어 두기 (무대에서 폼 타이핑 금지)
□ 노트북 절전·화면보호기 끄기
□ 브라우저 150% 확대 (뒤에서 봅니다)
□ 핸드폰 테더링 준비 (터널·AI 둘 다 인터넷 필요)
```

---

## 알아두실 것

**공개 주소는 켤 때마다 바뀝니다.** 시연 직전에 띄우고 그때 나온 주소를 쓰세요.

**노트북이 꺼지면 전부 죽습니다.** 5개가 전부 이 PC 에서 돕니다. 절전·재부팅 금지.

**AI 답변에는 하루 호출 한도가 있습니다.** 소진되면 `RETRIEVAL_ONLY` 로 내려가 AI 문장 대신 근거 조문만 나옵니다. 서비스가 죽는 건 아니지만, 여러 명이 챗봇을 많이 누르면 닳으니 시연 전에는 아껴 두세요.

**`-Tunnel` 을 빼면** 공개 주소 없이 이 PC 에서만 씁니다(`http://localhost:5173`). 영상 촬영만 할 거면 이걸로 충분합니다.

---

## 안 될 때

로그가 **`%TEMP%\safework\`** 에 쌓입니다. 무엇이 안 떴는지 스크립트가 마지막에 알려주니, 그 이름의 로그부터 보세요.

```
backend.log / backend.err     백엔드
ml.log / ml.err               ML 서버
frontend.log                  프론트
tunnel.log                    공개 주소
```

| 증상 | 확인 |
|---|---|
| Docker 가 안 뜬다 | Docker Desktop 을 직접 실행한 뒤 다시 시도 |
| 백엔드가 안 뜬다 | `backend.err` — 대개 5432 연결 실패(Postgres 먼저 확인) |
| ML 이 안 뜬다 | `ml.err` — venv 경로(`C:\swml`) 확인 |
| 화면은 뜨는데 데이터가 없다 | 백엔드(8080)가 떠 있는지 확인 |
| 유사 재해사례만 비어 있다 | ML 서버(8000) 확인 — 나머지는 정상 동작이 맞음 |

### 스크립트 실행이 막힐 때

`.\start-demo.ps1` 이 "이 시스템에서 스크립트를 실행할 수 없으므로" 로 막히면:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1 -Tunnel
```

---

## 다른 PC 에서 쓰려면

`start-demo.ps1` 맨 위 경로 네 줄만 고치면 됩니다.

```powershell
$Repo        = "...\safework-ai-hackathon-4c9b65"   # 저장소 위치
$MlPython    = "C:\swml\Scripts\python.exe"          # ML 가상환경
$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$DockerApp   = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

미리 준비해야 할 것:

| | |
|---|---|
| DB | `database/dump/ai_safework_full.sql` 을 넣고 `docker compose up -d` 로 한 번 적재 |
| ML | `mlserver` 에 가상환경 + `pip install -r requirements.txt` |
| 프론트 | `frontend` 에서 `npm install` (**Node 18 이상**) |
| AI 키 | `GEMINI_API_KEY` 를 사용자 환경변수에 저장 |

> `start-demo.ps1` 은 **UTF-8 BOM** 으로 저장해야 합니다. BOM 이 없으면 Windows PowerShell 5.1 이 한글을 깨뜨리고 스크립트가 오작동합니다.
