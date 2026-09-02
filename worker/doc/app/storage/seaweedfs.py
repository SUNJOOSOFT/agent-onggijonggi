from pathlib import Path
from urllib.parse import quote

import httpx

from app.errors import WorkerError


class SeaweedFsStorage:
    def __init__(self, filer_url: str, timeout_seconds: int) -> None:
        self._filer_url = filer_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    def upload(self, object_key: str, path: Path, content_type: str) -> None:
        url = f"{self._filer_url}/{quote(object_key, safe='/')}"
        try:
            with path.open("rb") as file:
                response = httpx.post(
                    url,
                    files={"file": (path.name, file, content_type)},
                    timeout=self._timeout_seconds,
                )
            response.raise_for_status()
        except (OSError, httpx.HTTPError) as error:
            raise WorkerError(503, "STORAGE_UNAVAILABLE", "Document storage is unavailable") from error
