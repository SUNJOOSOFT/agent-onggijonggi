from pathlib import Path

from app.models.document import (
    BulletList,
    DocumentRequest,
    Heading,
    NumberedList,
    PageBreak,
    Paragraph,
    Table,
)
from app.renderers.base import RenderedFile

MARKDOWN_CONTENT_TYPE = "text/markdown"


class MarkdownRenderer:
    def render(self, request: DocumentRequest, output_dir: Path) -> RenderedFile:
        lines: list[str] = []
        if request.title:
            lines.extend((f"# {request.title}", ""))

        for operation in request.operations:
            if isinstance(operation, Paragraph):
                lines.extend((operation.text, ""))
            elif isinstance(operation, Heading):
                lines.extend((f"{'#' * operation.level} {operation.text}", ""))
            elif isinstance(operation, BulletList):
                lines.extend(f"- {item}" for item in operation.items)
                lines.append("")
            elif isinstance(operation, NumberedList):
                lines.extend(f"{index}. {item}" for index, item in enumerate(operation.items, start=1))
                lines.append("")
            elif isinstance(operation, Table):
                lines.append(self._table_row(operation.columns))
                lines.append(self._table_row(["---"] * len(operation.columns)))
                lines.extend(self._table_row(row) for row in operation.rows)
                lines.append("")
            elif isinstance(operation, PageBreak):
                lines.extend(("<!-- page-break -->", ""))

        path = output_dir / request.file_name
        path.write_text("\n".join(lines), encoding="utf-8")
        return RenderedFile(path=path, file_name=request.file_name, content_type=MARKDOWN_CONTENT_TYPE)

    @staticmethod
    def _table_row(values: list[str]) -> str:
        return "| " + " | ".join(value.replace("|", "\\|").replace("\n", "<br>") for value in values) + " |"
