from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from app.models.document import DocumentRequest


@dataclass(frozen=True)
class RenderedFile:
    path: Path
    file_name: str
    content_type: str


class DocumentRenderer(Protocol):
    def render(self, request: DocumentRequest, output_dir: Path) -> RenderedFile: ...
