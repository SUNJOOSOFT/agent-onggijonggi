import json
from pathlib import Path

from app.core.config import Settings
from app.errors import WorkerError
from app.models.document import DocumentRequest
from app.services.document_service import DocumentService


class FakeStorage:
    def __init__(self) -> None:
        self.path_exists_during_upload = False
        self.object_key = ""
        self.uploaded_path: Path | None = None
        self.content_type = ""

    def upload(self, object_key: str, path: Path, content_type: str) -> None:
        self.object_key = object_key
        self.path_exists_during_upload = path.exists()
        self.uploaded_path = path
        self.content_type = content_type


class FakePdfConverter:
    def __init__(self) -> None:
        self.source_file_name = ""

    def convert(self, source, output_dir: Path, file_name: str):
        self.source_file_name = source.file_name
        path = output_dir / file_name
        path.write_bytes(b"%PDF-1.7\n")
        return type(source)(path=path, file_name=file_name, content_type="application/pdf")


def test_document_service_uploads_docx_and_cleans_temporary_file() -> None:
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))
    storage = FakeStorage()
    service = DocumentService(
        Settings(internal_api_key="test-key", seaweed_root_prefix="documents"),
        storage=storage,
    )

    response = service.create(DocumentRequest.model_validate(payload), "tenant-a", "request-1")

    assert storage.path_exists_during_upload
    assert response.object_key == (
        f"documents/tenant-a/{response.created_at:%Y/%m/%d}/{response.file_id}/{payload['fileName']}"
    )
    assert storage.object_key == response.object_key
    assert response.size > 0
    assert storage.uploaded_path is not None
    assert not storage.uploaded_path.exists()


def test_document_service_converts_docx_to_pdf() -> None:
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))
    payload["outputFormat"] = "PDF"
    payload["fileName"] = "2026-08-31_회의록.pdf"
    storage = FakeStorage()
    converter = FakePdfConverter()
    service = DocumentService(
        Settings(internal_api_key="test-key", seaweed_root_prefix="documents"),
        storage=storage,
        pdf_converter=converter,
    )

    response = service.create(DocumentRequest.model_validate(payload), "tenant-a", "request-1")

    assert converter.source_file_name == "2026-08-31_회의록.docx"
    assert response.content_type == "application/pdf"
    assert response.file_name == "2026-08-31_회의록.pdf"


def test_document_service_generates_markdown() -> None:
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))
    payload["outputFormat"] = "MD"
    payload["fileName"] = "2026-08-31_회의록.md"
    storage = FakeStorage()
    service = DocumentService(
        Settings(internal_api_key="test-key", seaweed_root_prefix="documents"),
        storage=storage,
    )

    response = service.create(DocumentRequest.model_validate(payload), "tenant-a", "request-1")

    assert response.content_type == "text/markdown"
    assert response.file_name == "2026-08-31_회의록.md"
    assert storage.content_type == "text/markdown"
