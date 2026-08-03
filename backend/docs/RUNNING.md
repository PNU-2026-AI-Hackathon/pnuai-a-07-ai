# 백엔드 실행 방법

## 방법 1. Docker Compose (권장)

Postgres 와 백엔드를 한 번에 띄웁니다.

### 준비

기초 데이터 덤프를 넣어야 합니다. 사고 64만건·SIF·법령 원문이라 용량이 커서 git 에 없습니다.
DB 담당(강주호)에게 받은 `ai_safework_full.sql` 을 아래 위치에 둡니다.

```
database/dump/ai_safework_full.sql
```

### 실행

저장소 루트에서:

```bash
docker compose up -d
```

- 첫 실행은 덤프 적재 때문에 **5~10분** 걸립니다. 이후에는 수십 초입니다.
- 진행 상황: `docker compose logs -f postgres`
- 완료 후 `http://localhost:8080/swagger-ui/index.html`

### 자주 쓰는 명령

```bash
docker compose logs -f backend     # 백엔드 로그
docker compose restart backend     # 백엔드만 재시작
docker compose down                # 중지 (데이터 유지)
docker compose down -v             # 중지 + DB 초기화 (다시 적재됨)
docker compose up -d --build       # 코드 변경 후 재빌드
```

DB 를 다시 적재하려면 **반드시 `-v` 로 볼륨을 지워야** 합니다.
초기화 스크립트는 데이터 디렉터리가 비어 있을 때만 실행되기 때문입니다.

---

## 방법 2. IntelliJ + 기존 컨테이너

이미 `safework-postgres` 컨테이너를 쓰고 계시면 그대로 두고 서버만 실행하면 됩니다.

```bash
docker start safework-postgres
```

그다음 IntelliJ 에서 `SafeworkBackendApplication` 실행.

> ⚠️ Compose 와 동시에 쓰지 마세요. 둘 다 5432 포트를 씁니다.

---

## 스키마 적재 순서

`database/docker/init/00-load.sh` 가 이 순서로 실행합니다. **순서가 틀리면 조용히 깨집니다.**

| 순서 | 내용 | 왜 이 순서인가 |
|---|---|---|
| 1 | `pgcrypto`, `pg_trgm` 확장 | UUID 생성, 법령 검색의 유사도 연산에 필요 |
| 2 | **기초 덤프** | `sif_case`·`accident_case` 등 원천 테이블. 이게 먼저 있어야 이후 스키마가 참조 가능 |
| 3 | `SCHEMA_3` ~ `SCHEMA_8` | 서비스 테이블, 코드 마스터, 콜드스타트 함수 |
| 4 | `SCHEMA_9`, `15`, `16a` | 컬럼 추가 (`work_type`, `law_ref` 등) + 예측 함수 |
| 5 | `checklist_item_insert.sql` | SIF 점검문항 835건. 위 컬럼이 있어야 들어감 |
| 6 | `SCHEMA_16b` ~ `22` | 후처리 및 함수 (예방 가이드, 근거 법령, 위험도 진단) |

`SCHEMA_21` 은 `SCHEMA_22` 없이 실행하면 **함수 생성은 성공하지만 호출할 때 실패**합니다.
plpgsql 은 `CREATE FUNCTION` 시점에 본문을 검증하지 않기 때문입니다.

---

## 환경변수

Compose 가 자동으로 넣어주며, 로컬 실행 시에는 `application.yaml` 기본값이 쓰입니다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ai_safework` | |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | |
| `SPRING_DATASOURCE_PASSWORD` | `1234` | Compose 는 `POSTGRES_PASSWORD` 로도 바꿀 수 있음 |
| `JWT_SECRET` | (개발용 고정값) | **배포 시 반드시 교체** |
| `APP_REPORT_STORAGE_DIR` | `reports` | 생성된 PDF 저장 위치 |

---

## 문제 해결

**`Schema-validation: missing table [...]` 로 백엔드가 안 뜬다**
덤프가 적재되지 않았습니다. `database/dump/` 에 덤프를 넣고 `docker compose down -v && docker compose up -d`.

**예방 가이드가 빈 배열로 나온다**
`accident_type_dist` 가 비었을 수 있습니다. `SCHEMA_15` 가 `accident_case` 를 집계하므로 덤프가 먼저 있어야 합니다.

**위험도 진단이 500 이다**
`risk_assessment` 에 `base_component` 컬럼이 없는 경우입니다. `SCHEMA_22` 가 적용됐는지 확인하세요.

**포트 충돌**
기존 `safework-postgres` 컨테이너나 IntelliJ 서버가 떠 있는지 확인하세요.
