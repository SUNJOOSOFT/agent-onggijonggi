# DocumentPlan and renderer DSL

## Purpose and ownership

`DocumentPlan` is the format-neutral, versioned intermediate contract between the BFF and a renderer compiler. It represents the meaning and input data of a document; it does not contain DOCX, XLSX, PPTX, PDF, Markdown, file-path, or executable instructions.

The BFF owns plan creation, including LLM structured output, schema validation, document-kind policy, filename policy, template selection, and version resolution. The document worker owns only the validated renderer DSL it receives.

## Common DocumentPlan

```json
{
  "schemaVersion": "1",
  "documentKind": "MEETING_MINUTES",
  "output": {
    "format": "DOCX",
    "fileName": "2026-08-31-meeting-minutes.docx"
  },
  "templateData": {
    "title": "AX 개발팀 회의록",
    "overview": "AX 개발 진행사항을 논의하였다.",
    "discussions": [
      {
        "topic": "Document Worker",
        "content": "Python 기반으로 구현",
        "owner": "개발팀"
      }
    ]
  },
  "metadata": {
    "source": "spring-ai"
  }
}
```

| Field | Responsibility |
| --- | --- |
| `schemaVersion` | BFF schema registry version. A new incompatible plan uses a new version. |
| `documentKind` | Business document category such as `MEETING_MINUTES`, `EXPENSE_REQUEST`, `NOTICE`, or `PROPOSAL`. It is not a file format. |
| `output` | BFF-controlled target format and safe filename. |
| `templateData` | Required JSON object produced by the LLM or BFF. It is empty (`{}`) until a document-kind schema is introduced. |
| `metadata` | Auditing context only; it must not affect renderer selection or execute code. |

Each `(documentKind, schemaVersion)` maps to a registered JSON schema. The LLM must return structured output that validates against that schema. It must not return renderer operations, template object keys, file paths, URLs, commands, or template source code.

## Compilation boundary

```text
LLM / source material
  → DocumentPlan validation in BFF
  → document-kind compiler
  → format-specific renderer DSL validation in worker
  → generated file
```

The BFF chooses the compiler and the target renderer. The worker never infers a document kind from prose and never calls an LLM.

## Renderer DSLs

Renderer DSLs are format-specific and contain only operations that the target renderer supports.

### DOCX renderer DSL (implemented)

The current `POST /api/v1/documents` request is the initial DOCX renderer DSL. It supports `paragraph`, `heading`, `bullet_list`, `numbered_list`, `table`, and `page_break`. `templateData` is required by the request contract but is not rendered yet.

The current `documentType: "DOCX"` is a renderer source-format field, not `DocumentPlan.documentKind`. A later API version may rename this payload to `DocxRenderRequest`; that rename must preserve the current endpoint during migration.

### XLSX renderer DSL (planned)

XLSX is the first planned renderer expansion. Its initial DSL should use `worksheet`, `row`, and `cell`; `merge_cells` plus an allowlisted set of widths, number formats, styles, and formulas follow after the minimal renderer. Chart, pivot, and complex print-layout support are separate scope. It must not reuse DOCX paragraph or heading operations.

### PPTX renderer DSL (planned)

PPTX follows XLSX. Its initial DSL should use `slide`, `title`, `text_box`, `bullet_list`, and limited `table`; allowlisted layouts, themes, images, shapes, and charts follow later. It must not accept arbitrary coordinates, URLs, or slide-master XML.

### PDF output (implemented for DOCX)

PDF is an output adapter, not a semantic or renderer DSL. It is created by converting an already-rendered DOCX with a fixed LibreOffice headless command and then validating the result. XLSX/PPTX PDF conversion is deferred until those source renderers exist.

### Markdown output (implemented for DOCX DSL)

Markdown is a direct output adapter for the current DOCX operation DSL. It maps headings, paragraphs, lists and tables to Markdown text; it does not convert a DOCX binary. The `page_break` operation is represented as a Markdown comment because Markdown has no page layout model.

## Template application (planned)

When template application is introduced, the BFF resolves a tenant-authorized template version and its `TemplateManifest`. The manifest maps only approved slots and repeat regions to `templateData`. The FrontEnd does not send `templateId`, `objectKey`, or a manifest; the worker receives only an already-authorized internal render request.

Template source changes create a new version and require manifest analysis and approval before use. A renderer must reject a plan whose `templateData` does not satisfy the approved manifest.
