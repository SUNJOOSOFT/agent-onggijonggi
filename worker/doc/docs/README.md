# Document Worker

Spring AI BFF가 검증된 DOCX renderer DSL을 전달하면 문서를 생성해 SeaweedFS에 저장하는 내부 동기식 서비스입니다.

현재는 DOCX·Markdown 생성, DOCX→PDF 변환, SeaweedFS 저장, 그리고 DOCX/XLSX/PPTX/PDF 템플릿 원본 저장을 제공합니다. 템플릿 적용, `TemplateManifest`, LLM 호출, XLSX/PPTX 생성은 후속 단계입니다.

## Run

```bash
cp .env.example .env
docker compose up --build
```

개발 환경에서만 worker는 기본값 `127.0.0.1:8100`으로 노출됩니다. SeaweedFS 서비스는 Compose 내부 네트워크에만 연결됩니다.

## Test

```bash
uv sync --group dev
uv run pytest
```

MVP에는 영속 메타데이터 DB가 없습니다. BFF는 생성 응답의 `fileId`와 `objectKey`를 보관해야 합니다.

현재 DOCX renderer DSL은 [document-dsl.md](document-dsl.md), BFF의 공통 `DocumentPlan` 및 포맷별 DSL 경계는 [document-plan.md](document-plan.md)를 참고하세요.

BFF의 현재 Worker 호출 방식과 템플릿 원본 저장 방식은 [bff-integration.md](bff-integration.md)를 참고하세요.

XLSX는 최소 spreadsheet renderer부터, PPTX는 최소 slide renderer부터 순차적으로 추가할 계획입니다. 상세한 범위와 순서는 [RD_Document_Worker.md](RD_Document_Worker.md)를 참고하세요.

메인 프로젝트 Compose와 환경변수 병합 항목은 [main-compose-merge.md](main-compose-merge.md)를 참고하세요.
