import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models.document import DocumentRequest


def load_example_request() -> dict[str, object]:
    path = Path(__file__).parents[1] / "fixtures" / "meeting-minutes.json"
    return json.loads(path.read_text(encoding="utf-8"))


def test_test_json_matches_document_request_contract() -> None:
    request = DocumentRequest.model_validate(load_example_request())

    assert request.document_type == "DOCX"
    assert request.output_format == "DOCX"
    assert request.template_data == {}
    assert request.operations[0].type == "heading"


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("templateId", "meeting-minutes-v1"),
        ("document", {"elements": []}),
    ],
)
def test_unknown_top_level_fields_are_rejected(field: str, value: object) -> None:
    payload = load_example_request()
    payload[field] = value

    with pytest.raises(ValidationError):
        DocumentRequest.model_validate(payload)


def test_heading_level_must_be_between_one_and_six() -> None:
    payload = load_example_request()
    payload["operations"][0]["level"] = 7

    with pytest.raises(ValidationError):
        DocumentRequest.model_validate(payload)


def test_template_data_is_required() -> None:
    payload = load_example_request()
    del payload["templateData"]

    with pytest.raises(ValidationError):
        DocumentRequest.model_validate(payload)


def test_file_name_must_not_contain_a_path() -> None:
    payload = load_example_request()
    payload["fileName"] = "../meeting.docx"

    with pytest.raises(ValidationError):
        DocumentRequest.model_validate(payload)


def test_pdf_output_is_accepted() -> None:
    payload = load_example_request()
    payload["outputFormat"] = "PDF"
    payload["fileName"] = "meeting-minutes.pdf"

    request = DocumentRequest.model_validate(payload)

    assert request.output_format == "PDF"


def test_markdown_output_is_accepted() -> None:
    payload = load_example_request()
    payload["outputFormat"] = "MD"
    payload["fileName"] = "meeting-minutes.md"

    request = DocumentRequest.model_validate(payload)

    assert request.output_format == "MD"
