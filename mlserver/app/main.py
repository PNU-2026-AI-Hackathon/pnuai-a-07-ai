import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import cases, rag, risk
from app.core import model_loader
from app.core.config import settings

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    model_loader.load_all_models()
    yield


app = FastAPI(title=settings.PROJECT_NAME, lifespan=lifespan)

app.include_router(risk.router)
app.include_router(rag.router)
app.include_router(cases.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "models_loaded": model_loader.is_loaded()}
