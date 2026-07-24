from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# mlserver/app/core/config.py 기준 3단계 위 = 저장소 루트
REPO_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    PROJECT_NAME: str = "SafeWork AI ML Server"

    # 데이터팀이 관리하는 predict.py / kosha_encodings.py / models/*.txt 가 있는 경로.
    # sys.path에 추가해서 그대로 import — 인코딩 로직을 mlserver에 복제하지 않는다.
    ML_MODEL_SOURCE_DIR: Path = REPO_ROOT / "데이터모델링" / "ML모델"

    # 참조 데이터 스냅샷 (PG 접속정보 확정 전까지 사용)
    REFERENCE_DATA_DIR: Path = Path(__file__).resolve().parents[1] / "data"

    # PostgreSQL (ai_safework) 읽기전용 접속정보 — 아직 미확정, 값이 없으면
    # coldstart 서비스는 자동으로 스냅샷 모드로 동작한다.
    PG_HOST: str | None = None
    PG_PORT: int = 5432
    PG_DB: str | None = None
    PG_USER: str | None = None
    PG_PASSWORD: str | None = None

    # True로 바뀌면 coldstart_baseline/checklist_item을 스냅샷 대신 PG에서 직접 조회한다.
    COLDSTART_USE_LIVE_DB: bool = False

    OPENAI_API_KEY: str | None = None

    @property
    def pg_dsn(self) -> str | None:
        if not (self.PG_HOST and self.PG_DB and self.PG_USER):
            return None
        return (
            f"postgresql+psycopg2://{self.PG_USER}:{self.PG_PASSWORD or ''}"
            f"@{self.PG_HOST}:{self.PG_PORT}/{self.PG_DB}"
        )


settings = Settings()
