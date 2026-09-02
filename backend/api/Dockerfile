# syntax=docker/dockerfile:1

### 1단계: 빌드 (JDK로 Gradle 빌드) ###
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 래퍼·설정을 먼저 복사해 의존성 레이어 캐시를 활용한다.
# build context는 backend/ 전체다(멀티모듈이라 settings.gradle이 두 모듈을 함께 조립한다).
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle ./common/
COPY api/build.gradle ./api/

# .gitattributes(eol=lf) 도입 전에 체크아웃한 Windows 워킹트리에는 gradlew가 CRLF로 남아 있다.
# 그 경우 셔뱅이 `#!/bin/sh\r`이 되어 커널이 인터프리터를 못 찾고 "./gradlew: not found"로 실패한다.
# 각자 PC 상태와 무관하게 빌드되도록 이미지 안에서 정규화한다. (실패하면 즉시 드러나야 하므로 || true 없음)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 의존성 레이어 캐시 워밍 — 네트워크 사정으로 실패할 수 있어 빌드를 막지 않는다.
# 캐시 마운트로 Gradle 홈을 빌드 간에 유지한다(이 스테이지는 root라 홈이 /root/.gradle).
RUN --mount=type=cache,target=/root/.gradle \
	./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능한 jar 생성 (테스트는 CI에서 별도 수행).
COPY common/src ./common/src
COPY api/src ./api/src
RUN --mount=type=cache,target=/root/.gradle \
	./gradlew :api:bootJar --no-daemon -x test

### 2단계: 실행 (가벼운 JRE) ###
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# 헬스체크(compose)가 컨테이너 안에서 curl을 쓰므로 설치한다.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

# 루트가 아닌 사용자로 실행한다.
RUN useradd -r -u 1001 appuser

COPY --from=build /app/api/build/libs/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]