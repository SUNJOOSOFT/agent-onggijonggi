# syntax=docker/dockerfile:1

### 1단계: 빌드 (의존성 설치 + Next 빌드) ###
# bun이 node_modules/.bin/next 심볼릭 링크를 realpath로 풀지 않아 next 내부 모듈
# (require-hook)을 못 찾는 문제가 있다. 그래서 의존성 설치와 빌드를 같은 스테이지에서
# 수행하고, .bin 심링크 대신 next 실제 경로를 직접 호출한다.
FROM oven/bun:1-alpine AS build
WORKDIR /app
COPY package.json bun.lock ./
# 캐시 마운트로 내려받은 패키지를 빌드 간에 유지한다. 설치가 중간에 실패해도
# 재시도할 때 처음부터 다시 받지 않는다(메모리·디스크가 빠듯한 PC에서 next 같은
# 큰 패키지를 푸는 중 실패하는 일이 있다 — INSTALL.md 3단계).
RUN --mount=type=cache,target=/root/.bun/install/cache \
	bun install --frozen-lockfile
COPY . .

# NEXT_PUBLIC_* 값은 빌드 시점에 번들에 박히므로 빌드 인자로 받는다.
# 변수명은 frontend/lib/api/config.ts가 읽는 이름과 반드시 일치해야 한다(다르면 목업으로 폴백).
ARG NEXT_PUBLIC_BFF_BASE_URL
ENV NEXT_PUBLIC_BFF_BASE_URL=$NEXT_PUBLIC_BFF_BASE_URL
# 캐시 마운트로 Next 빌드 캐시를 빌드 간에 유지한다(소스 일부만 바뀌면 재컴파일 범위가 준다).
RUN --mount=type=cache,target=/app/.next/cache \
	bun node_modules/next/dist/bin/next build

### 2단계: 실행 ###
FROM oven/bun:1-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/next.config.ts ./next.config.ts

# 이 이미지에는 sharp가 의존하는 libvips(LGPL-3.0-or-later)가 포함된다.
# 고지 의무는 이미지를 배포할 때 발생하므로 고지 파일을 이미지 안에 함께 둔다.
# 라이선스 고지 3종을 이미지에 담는다.
#   LICENSE                    — 이 프로젝트 라이선스(Apache-2.0 전문). §4(a)가 요구하는
#                                "라이선스 사본"이라 URL 참조가 아니라 전문을 넣는다.
#   LICENSE.vercel-ai-chatbot  — 포크 원본(Vercel)의 저작권 고지. 이름을 구분해
#                                이미지가 Vercel 것으로 오해되지 않게 한다.
#   THIRD-PARTY-NOTICES.md     — libvips(LGPL) 등 배포물에 포함되는 서드파티 고지.
COPY LICENSE.apache-2.0 ./LICENSE
COPY LICENSE ./LICENSE.vercel-ai-chatbot
COPY THIRD-PARTY-NOTICES.md ./
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.source="https://github.com/SUNJOOSOFT/onggijonggi-chat"

EXPOSE 3000
# .bin/next 심링크 우회 — next 실제 경로 직접 호출
CMD ["bun", "node_modules/next/dist/bin/next", "start"]
