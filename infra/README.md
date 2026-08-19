# infra — 오케스트레이션·공유 설정

서비스 조립/실행 인프라 산출물. (각 서비스 Dockerfile은 해당 서비스 디렉터리가 소유)
- `docker-compose.yml` — postgres·redis·keycloak·litellm·bff·nextjs 6개 서비스 조립. caddy는 `profiles: [caddy]`라 기본 `up`에 포함되지 않는다. 프로젝트(그룹) 이름은 `name: agent-ogjg`로 고정 — 볼륨·네트워크도 이 접두사를 쓴다(`agent-ogjg_postgres-data` 등)
- `.env.example` — 환경변수 템플릿. **바로 도는 고정 자격증명이 채워져 있다**(`appuser`/`appuser`, `admin`/`admin`, 토큰 5개) — 공개된 값이니 시험용으로만. 접속 주소까지 채워져 있어 `.env`로 복사하면 그대로 돈다 — 모델 연결(쓰는 공급자의 API 키 한 줄)만 정한다(실제 `.env`는 커밋 금지)
- `utils/init-env.ps1`(Windows) · `utils/init-env.sh`(macOS·Linux) — 자격증명을 직접 정하고 싶을 때 쓰는 `.env` 생성 스크립트. **기본 설치 절차에는 쓰이지 않는다** — `.env.example`을 복사하면 그대로 돌기 때문이다. 비밀번호 2개만 입력받고 나머지 5개는 무작위로 채운다(사용자명·URL은 그대로 둔다). 두 스크립트의 결과는 무작위값을 빼면 동일하다
- `Caddyfile` — 리버스 프록시 라우팅 규칙. caddy 프로필을 쓸 때만 읽힌다
- `config/init-db.sql` — PostgreSQL 초기화
- `config/litellm_config.yaml` — LiteLLM 모델 라우팅
- `config/realm-app.json` — Keycloak realm·client·역할·계정 최초 기동 시 자동 임포트

서비스끼리는 compose가 만드는 `app-net`(bridge)으로 통신한다. `proxy-net`(`external: true`)은 **caddy 프로필에만** 걸려 있다 — 다른 compose 스택을 이 caddy 뒤에 붙이기 위한 공용 네트워크라, caddy를 켤 때만 `docker network create proxy-net`이 한 번 필요하다.
브라우저가 보는 공개 주소와 컨테이너끼리 쓰는 내부 주소는 분리돼 있다 — nextjs는 Keycloak을 `KEYCLOAK_INTERNAL_ISSUER`(`http://keycloak:8080`), BFF를 `BFF_INTERNAL_URL`(`http://bff:8090`)로 부른다. 덕분에 `.env`의 공개 주소를 `localhost`로 둬도 서버 쪽 호출이 깨지지 않는다.
`caddy-local-root.crt`만 커밋되지 않는다 — caddy 프로필을 켤 때 인증서를 추출해 만든다(브라우저 신뢰 등록용).

전체 스택 기동 절차는 루트 [`INSTALL.md`](../INSTALL.md), 리버스 프록시·HTTPS 구성은 [`INSTALL_r-proxy.md`](../INSTALL_r-proxy.md) 참조.