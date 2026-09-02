import hmac
import re
from typing import Annotated

from fastapi import APIRouter, Depends, File, Header, Request, UploadFile

from app.core.config import Settings
from app.core.request_context import request_id
from app.errors import WorkerError
from app.models.document import DocumentRequest, DocumentResponse, TemplateResponse
from app.services.document_service import DocumentService

_TENANT_ID = re.compile(r"^[A-Za-z0-9_-]{1,100}$")

router = APIRouter(prefix="/api/v1/documents", tags=["documents"])


def get_settings() -> Settings:
    return Settings()


def get_document_service(settings: Annotated[Settings, Depends(get_settings)]) -> DocumentService:
    return DocumentService(settings)


def authorize(settings: Settings, internal_api_key: str, tenant_id: str) -> None:
    if not hmac.compare_digest(internal_api_key, settings.internal_api_key):
        raise WorkerError(401, "UNAUTHORIZED", "Invalid internal API key")
    if not _TENANT_ID.fullmatch(tenant_id):
        raise WorkerError(400, "VALIDATION_ERROR", "X-Tenant-Id is invalid")


@router.post("", status_code=201, response_model=DocumentResponse)
def create_document(
    document_request: DocumentRequest,
    request: Request,
    settings: Annotated[Settings, Depends(get_settings)],
    service: Annotated[DocumentService, Depends(get_document_service)],
    internal_api_key: Annotated[str, Header(alias="X-Internal-Api-Key")],
    tenant_id: Annotated[str, Header(alias="X-Tenant-Id")],
) -> DocumentResponse:
    authorize(settings, internal_api_key, tenant_id)
    return service.create(document_request, tenant_id, request_id(request))


@router.post("/templates", status_code=201, response_model=TemplateResponse)
async def save_template(
    request: Request,
    settings: Annotated[Settings, Depends(get_settings)],
    service: Annotated[DocumentService, Depends(get_document_service)],
    internal_api_key: Annotated[str, Header(alias="X-Internal-Api-Key")],
    tenant_id: Annotated[str, Header(alias="X-Tenant-Id")],
    template: Annotated[UploadFile, File()],
) -> TemplateResponse:
    authorize(settings, internal_api_key, tenant_id)
    return await service.save_template(template, tenant_id, request_id(request))
