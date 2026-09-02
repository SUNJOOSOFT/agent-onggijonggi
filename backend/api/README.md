# bff — Spring Boot WebFlux BFF

02·03·04 계층. 프론트(`frontend/`)의 **단일 진입점**.
- 02 EDGE: Spring Security 필터체인 + Keycloak(OIDC/RBAC) · 분당 요청 제한
- 03 CORE: Spring AI `ChatClient` 스트리밍(WebFlux, `Flux<String>` — 프레이밍 없는 `text/plain`)
- 04 DATA: PostgreSQL(JPA/Hibernate + Flyway)

RAG Advisor Chain · Elasticsearch(벡터 검색) · Spring AI ETL은 다음 단계에서 구현 예정이다.
`ChatClient`의 `.advisors()`를 빈 채로 호출해 advisor 삽입 지점을 미리 잡아뒀다.
