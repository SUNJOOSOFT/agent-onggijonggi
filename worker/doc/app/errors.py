from fastapi import Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.core.request_context import request_id


class ErrorResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    code: str
    message: str
    request_id: str = Field(alias="requestId")


class WorkerError(Exception):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        self.status_code = status_code
        self.code = code
        self.message = message


def error_response(request: Request, error: WorkerError) -> JSONResponse:
    body = ErrorResponse(code=error.code, message=error.message, request_id=request_id(request))
    return JSONResponse(status_code=error.status_code, content=body.model_dump(by_alias=True))
