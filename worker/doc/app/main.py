from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from uuid import uuid4

from app.api.routes.documents import router as documents_router
from app.errors import WorkerError, error_response

app = FastAPI(title="document-worker", docs_url=None, redoc_url=None)
app.include_router(documents_router)


@app.middleware("http")
async def add_request_id(request: Request, call_next):
    request.state.request_id = request.headers.get("X-Request-Id") or uuid4().hex
    return await call_next(request)


@app.exception_handler(WorkerError)
async def handle_worker_error(request: Request, error: WorkerError):
    return error_response(request, error)


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, error: RequestValidationError):
    return error_response(request, WorkerError(400, "VALIDATION_ERROR", "Document request is invalid"))


@app.get("/health/live")
def liveness() -> dict[str, str]:
    return {"status": "ok"}
