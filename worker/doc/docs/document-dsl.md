# DOCX renderer DSL

This document defines the current worker-facing DOCX renderer DSL. It is not the format-neutral `DocumentPlan` used by the BFF; see [document-plan.md](document-plan.md) for that contract and the planned XLSX, PPTX, and PDF boundaries.

## API convention

외부 API는 camelCase를 사용하고 Python 내부 모델은 snake_case를 사용한다. 요청은 JSON만 허용하며 정의되지 않은 필드는 거절한다.

`POST /api/v1/documents`는 `X-Internal-Api-Key`, `X-Tenant-Id` 헤더를 요구한다. `X-Request-Id`, `X-Trace-Id`, `X-User-Id`는 선택 사항이며 tenant ID는 body에 포함하지 않는다.

## Request shape

```json
{
  "documentType": "DOCX",
  "outputFormat": "DOCX",
  "fileName": "weekly-report.docx",
  "title": "Weekly report",
  "templateData": {},
  "operations": [],
  "metadata": {"source": "spring-ai"}
}
```

현재 구현은 `documentType: "DOCX"`의 `outputFormat: "DOCX" | "PDF" | "MD"`를 지원한다. PDF는 Worker가 생성한 DOCX를 LibreOffice headless로 변환한 출력 형식이고, MD는 같은 operation DSL을 Markdown으로 직접 렌더링한 출력 형식이다. XLSX/PPTX renderer는 후속 단계다.

## Initial limits

| Item | Maximum |
| --- | ---: |
| JSON request bytes | 1,048,576 |
| Output bytes | 26,214,400 |
| Operations | 500 |
| Render seconds | 30 |

| Operation text/cell length | 10,000 characters |
| Table columns | 20 |
| Table rows | 200 |

## DOCX operations

| Type | Fields |
| --- | --- |
| `paragraph` | `text` |
| `heading` | `level` (1–6), `text` |
| `bullet_list`, `numbered_list` | `items` (1–200 strings) |
| `table` | `columns` (1–20 strings), `rows` (up to 200 rows; every row has the same length as `columns`) |
| `page_break` | none |

`outputFormat: "PDF"`일 때 `fileName`은 `.pdf` 확장자를 사용해야 한다. Worker는 같은 임시 디렉터리에서 먼저 DOCX를 생성하고, 고정된 `soffice` 명령으로 PDF를 변환한 뒤 PDF 헤더와 파일 크기를 검증한다.

`outputFormat: "MD"`일 때 `fileName`은 `.md` 확장자를 사용해야 한다. `heading`, `paragraph`, `bullet_list`, `numbered_list`, `table`은 각각 Markdown 문법으로 렌더링되며, `page_break`는 `<!-- page-break -->` 주석으로 표현된다.

`templateData`는 필수 객체다. 현재는 빈 객체 `{}`를 보내며 렌더링에 사용하지 않는다. 후속 템플릿 적용 버전에서 BFF의 LLM이 생성한 구조화 데이터를 이 필드에 전달한다. `metadata`는 문자열 key/value만 저장하는 감사용 정보다. 템플릿 적용은 후속 버전에서 제공하며, 현재 생성 요청은 `templateId`를 허용하지 않는다.

## Safety rules

`fileName`은 basename만 허용하며 `/`, `\\`, `..`, 제어문자, 빈 문자열을 포함할 수 없다. API는 `python`, `script`, `command`, `shell`, `templatePath`, `localPath`, `url`, `expression` 필드를 지원하지 않는다.

## Template upload

`POST /api/v1/documents/templates` accepts a `multipart/form-data` request with one `template` field. It requires the same `X-Internal-Api-Key` and `X-Tenant-Id` headers as document generation. Supported upload formats are DOCX, XLSX, PPTX, and PDF; the filename extension and MIME type must match.

The response returns `templateId`, `fileName`, `objectKey`, `contentType`, `size`, `createdAt`, and `requestId`. The worker stores templates under `templates/{tenantId}/{YYYY}/{MM}/{DD}/{templateId}/{fileName}`. `objectKey` is an internal identifier for the BFF and must not be sent to a browser. This version stores templates only; template application is a later feature for every supported format.
