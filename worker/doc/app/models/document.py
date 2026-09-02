from __future__ import annotations

import re
from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue, model_validator

MAX_TEXT_LENGTH = 10_000
MAX_TABLE_COLUMNS = 20
MAX_TABLE_ROWS = 200
MAX_OPERATIONS = 500

_FILENAME_INVALID = re.compile(r"[\\/\x00-\x1f]")
Text = Annotated[str, Field(min_length=1, max_length=MAX_TEXT_LENGTH)]
TEMPLATE_CONTENT_TYPES = {
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".pdf": "application/pdf",
}


def validate_file_name(file_name: str, expected_extension: str | None = None) -> None:
    if (
        not file_name
        or file_name in {".", ".."}
        or ".." in file_name
        or _FILENAME_INVALID.search(file_name)
        or (expected_extension is not None and not file_name.lower().endswith(expected_extension))
    ):
        raise ValueError("fileName must be a safe basename")


class DslModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class Paragraph(DslModel):
    type: Literal["paragraph"]
    text: Text


class Heading(DslModel):
    type: Literal["heading"]
    level: int = Field(ge=1, le=6)
    text: Text


class BulletList(DslModel):
    type: Literal["bullet_list"]
    items: list[Text] = Field(min_length=1, max_length=MAX_TABLE_ROWS)


class NumberedList(DslModel):
    type: Literal["numbered_list"]
    items: list[Text] = Field(min_length=1, max_length=MAX_TABLE_ROWS)


class Table(DslModel):
    type: Literal["table"]
    columns: list[Text] = Field(min_length=1, max_length=MAX_TABLE_COLUMNS)
    rows: list[list[Text]] = Field(max_length=MAX_TABLE_ROWS)

    @model_validator(mode="after")
    def rows_match_columns(self) -> Table:
        if any(len(row) != len(self.columns) for row in self.rows):
            raise ValueError("each table row must have the same number of cells as columns")
        return self


class PageBreak(DslModel):
    type: Literal["page_break"]


Operation = Annotated[
    Paragraph | Heading | BulletList | NumberedList | Table | PageBreak,
    Field(discriminator="type"),
]


class DocumentRequest(DslModel):
    document_type: Literal["DOCX"] = Field(alias="documentType")
    output_format: Literal["DOCX", "PDF", "MD"] = Field(alias="outputFormat")
    file_name: str = Field(alias="fileName", min_length=1, max_length=255)
    title: str | None = Field(default=None, max_length=MAX_TEXT_LENGTH)
    template_data: dict[str, JsonValue] = Field(alias="templateData")
    operations: list[Operation] = Field(min_length=1, max_length=MAX_OPERATIONS)
    metadata: dict[str, str] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_file_name_and_metadata(self) -> DocumentRequest:
        expected_extension = {"DOCX": ".docx", "PDF": ".pdf", "MD": ".md"}[self.output_format]
        validate_file_name(self.file_name, expected_extension)
        if any(not key or len(key) > 100 or len(value) > MAX_TEXT_LENGTH for key, value in self.metadata.items()):
            raise ValueError("metadata key or value exceeds the allowed length")
        return self


class DocumentResponse(DslModel):
    file_id: str = Field(alias="fileId")
    file_name: str = Field(alias="fileName")
    object_key: str = Field(alias="objectKey")
    content_type: str = Field(alias="contentType")
    size: int
    created_at: datetime = Field(alias="createdAt")
    request_id: str = Field(alias="requestId")


class TemplateResponse(DslModel):
    template_id: str = Field(alias="templateId")
    file_name: str = Field(alias="fileName")
    object_key: str = Field(alias="objectKey")
    content_type: str = Field(alias="contentType")
    size: int
    created_at: datetime = Field(alias="createdAt")
    request_id: str = Field(alias="requestId")
