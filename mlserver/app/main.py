import logging

from fastapi import FastAPI

from app.api import risk
from app.core import model_loader
from app.core.config import settings

logging.basicConfig(level=logging.INFO)

app = FastAPI(title=settings.PROJECT_NAME)

app.include_router(risk.router)


@app.on_event("startup")
def on_startup() -> None:
    model_loader.load_all_models()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "models_loaded": model_loader.is_loaded()}
