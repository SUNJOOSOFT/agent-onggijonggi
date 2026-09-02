import subprocess
from pathlib import Path

from app.errors import WorkerError
from app.renderers.base import RenderedFile

PDF_CONTENT_TYPE = "application/pdf"


class PdfConverter:
    def __init__(self, timeout_seconds: int) -> None:
        self._timeout_seconds = timeout_seconds

    def convert(self, source: RenderedFile, output_dir: Path, file_name: str) -> RenderedFile:
        profile_dir = output_dir / "libreoffice-profile"
        profile_dir.mkdir()
        command = [
            "soffice",
            "--headless",
            f"-env:UserInstallation={profile_dir.as_uri()}",
            "--convert-to",
            "pdf:writer_pdf_Export",
            "--outdir",
            str(output_dir),
            str(source.path),
        ]
        try:
            result = subprocess.run(
                command,
                check=False,
                capture_output=True,
                timeout=self._timeout_seconds,
            )
        except FileNotFoundError as error:
            raise WorkerError(500, "RENDER_FAILED", "PDF converter is unavailable") from error
        except subprocess.TimeoutExpired as error:
            raise WorkerError(504, "RENDER_TIMEOUT", "PDF conversion timed out") from error
        if result.returncode != 0:
            raise WorkerError(500, "RENDER_FAILED", "PDF conversion failed")

        converted_path = output_dir / source.path.with_suffix(".pdf").name
        if not converted_path.is_file() or not converted_path.read_bytes().startswith(b"%PDF-"):
            raise WorkerError(500, "RENDER_FAILED", "PDF conversion did not produce a valid PDF")
        target_path = output_dir / file_name
        if converted_path != target_path:
            converted_path.replace(target_path)
        return RenderedFile(path=target_path, file_name=file_name, content_type=PDF_CONTENT_TYPE)
