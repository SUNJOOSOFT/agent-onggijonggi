import json
from pathlib import Path

from app.models.document import DocumentRequest
from app.renderers.markdown import MARKDOWN_CONTENT_TYPE, MarkdownRenderer


def test_markdown_renderer_creates_markdown_from_document_dsl(tmp_path: Path) -> None:
    payload = json.loads((Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json").read_text(encoding="utf-8"))
    payload["outputFormat"] = "MD"
    payload["fileName"] = "meeting-minutes.md"

    rendered = MarkdownRenderer().render(DocumentRequest.model_validate(payload), tmp_path)

    assert rendered.content_type == MARKDOWN_CONTENT_TYPE
    assert rendered.path.read_text(encoding="utf-8") == (
        "# AX 개발팀 회의록\n\n"
        "# 회의 개요\n\n"
        "2026년 8월 31일 AX 개발 진행사항을 논의하였다.\n\n"
        "# 주요 논의사항\n\n"
        "| 항목 | 내용 | 담당 |\n"
        "| --- | --- | --- |\n"
        "| Document Worker | Python 기반으로 구현 | 개발팀 |\n"
        "| Tool Calling | Spring AI에서 Worker 호출 | 플랫폼팀 |\n"
    )
