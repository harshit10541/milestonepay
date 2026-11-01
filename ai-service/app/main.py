from fastapi import APIRouter, Depends, FastAPI

from .auth import require_secret

app = FastAPI(title="MilestonePay AI service")

protected = APIRouter(dependencies=[Depends(require_secret)])


@protected.get("/ping")
def ping() -> dict:
    return {"ok": True}


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


app.include_router(protected)