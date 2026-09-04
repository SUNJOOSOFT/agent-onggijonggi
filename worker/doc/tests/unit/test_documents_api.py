import json
from datetime import UTC, datetime
from pathlib import Path

from fastapi.testclient import TestClient

from app.api.routes.documents import get_document_service
from app.main import app
from app.models.document import DocumentResponse, TemplateResponse


class FakeDocumentService:
    def create(self, request, tenant_id: str, request_id: str) -> DocumentResponse:
        return DocumentResponse(
            file_id="01JZ8R13BFA6QX2GJ1H4M9X8V7",
            file_name=request.file_name,
            object_key=f"documents/{tenant_id}/2026/09/01/01JZ8R13BFA6QX2GJ1H4M9X8V7/{request.file_name}",
            content_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            size=128,
            created_at=datetime(2026, 9, 1, tzinfo=UTC),
            request_id=request_id,
        )

    async def save_template(self, template, tenant_id: str, request_id: str) -> TemplateResponse:
        return TemplateResponse(
            template_id="01JZ8R13BFA6QX2GJ1H4M9X8V7",
            file_name=template.filename,
            object_key=f"templates/{tenant_id}/2026/09/01/01JZ8R13BFA6QX2GJ1H4M9X8V7/{template.filename}",
            content_type=template.content_type,
            size=128,
            created_at=datetime(2026, 9, 1, tzinfo=UTC),
            request_id=request_id,
        )


def test_documents_endpoint_returns_created_metadata(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_API_KEY", "test-key")
    app.dependency_overrides[get_document_service] = lambda: FakeDocumentService()
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))

    response = TestClient(app).post(
        "/api/v1/documents",
        json=payload,
        headers={
            "X-Internal-Api-Key": "test-key",
            "X-Tenant-Id": "tenant-a",
            "X-Request-Id": "request-1",
        },
    )

    app.dependency_overrides.clear()

    assert response.status_code == 201
    assert response.json()["requestId"] == "request-1"
    assert response.json()["fileName"] == payload["fileName"]


def test_documents_endpoint_returns_standard_validation_error(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_API_KEY", "test-key")
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))
    payload["templateId"] = "not-allowed"

    response = TestClient(app).post(
        "/api/v1/documents",
        json=payload,
        headers={
            "X-Internal-Api-Key": "test-key",
            "X-Tenant-Id": "tenant-a",
            "X-Request-Id": "request-2",
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "code": "VALIDATION_ERROR",
        "message": "Document request is invalid",
        "requestId": "request-2",
    }


def test_templates_endpoint_returns_template_metadata(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_API_KEY", "test-key")
    app.dependency_overrides[get_document_service] = lambda: FakeDocumentService()

    response = TestClient(app).post(
        "/api/v1/documents/templates",
        files={
            "template": (
                "quarterly-report.xlsx",
                b"template-content",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
        headers={
            "X-Internal-Api-Key": "test-key",
            "X-Tenant-Id": "tenant-a",
            "X-Request-Id": "template-1",
        },
    )

    app.dependency_overrides.clear()

    assert response.status_code == 201
    assert response.json()["templateId"] == "01JZ8R13BFA6QX2GJ1H4M9X8V7"
    assert response.json()["fileName"] == "quarterly-report.xlsx"
