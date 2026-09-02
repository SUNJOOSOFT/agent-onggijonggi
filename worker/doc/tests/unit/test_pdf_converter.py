import subprocess
from pathlib import Path

import pytest

from app.errors import WorkerError
from app.renderers.base import RenderedFile
from app.services.pdf_converter import PdfConverter


def test_pdf_converter_uses_soffice_and_validates_output(monkeypatch, tmp_path: Path) -> None:
    source_path = tmp_path / "meeting.docx"
    source_path.write_bytes(b"docx")
    source = RenderedFile(source_path, "meeting.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    def fake_run(command, check, capture_output, timeout):
        assert command[0] == "soffice"
        assert "--headless" in command
        assert timeout == 30
        (tmp_path / "meeting.pdf").write_bytes(b"%PDF-1.7\n")
        return subprocess.CompletedProcess(command, 0)

    monkeypatch.setattr(subprocess, "run", fake_run)

    rendered = PdfConverter(30).convert(source, tmp_path, "result.pdf")

    assert rendered.path.name == "result.pdf"
    assert rendered.content_type == "application/pdf"
    assert rendered.path.read_bytes().startswith(b"%PDF-")


def test_pdf_converter_maps_timeout_to_worker_error(monkeypatch, tmp_path: Path) -> None:
    source_path = tmp_path / "meeting.docx"
    source_path.write_bytes(b"docx")
    source = RenderedFile(source_path, "meeting.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    def fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired("soffice", 30)

    monkeypatch.setattr(subprocess, "run", fake_run)

    with pytest.raises(WorkerError, match="PDF conversion timed out"):
        PdfConverter(30).convert(source, tmp_path, "result.pdf")
