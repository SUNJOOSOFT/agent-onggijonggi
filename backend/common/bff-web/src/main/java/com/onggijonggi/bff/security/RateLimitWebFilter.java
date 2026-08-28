package com.onggijonggi.bff.security;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : RateLimitWebFilter.java
 * Description : sub(JWT subject)별 in-memory 고정 윈도우 RateLimit. Spring이
 *               자동으로 전역 등록하지 않도록 @Component를 붙이지 않고, 각 시큐리티 설정이
 *               addFilterAfter(AUTHORIZATION)로 자기 체인 내부에만 직접 끼워 넣는다 — 인가 이후에
 *               붙어야 JWT sub를 읽을 수 있고, 전역 WebFilter로도 등록되면 요청마다 두 번 카운트된다.
 *               지금 이걸 꽂는 곳은 둘이다: SecurityConfig(HTTP)와 WsSecurityConfig(WS 핸드셰이크, 이슈 #6).
 *               카운터가 인스턴스 필드라 인스턴스가 다르면 버킷도 갈린다 — 두 체인이 한도를 나눠
 *               갖는 것은 그 성질에 기댄 의도된 설계이니, 카운터를 static이나 공용 빈으로 끌어올리면
 *               두 정책이 조용히 한 통이 된다.
 *               미인증(permitAll) 경로는 SecurityContext에 JwtAuthenticationToken이 없으므로 통과시킨다.
 *               sub 추출은 Optional&lt;String&gt; 단계에서 완결한다 — chain.filter()의 결과인 Mono&lt;Void&gt;는
 *               성공해도 onNext 없이 완료되어 "빈 Mono"와 구별되지 않으므로, 여기에 switchIfEmpty를
 *               걸면 정상 요청도 매번 체인이 두 번 실행되는 버그가 생긴다(요청 바디 재소비로 두 번째
 *               실행이 실패해 이미 커밋된 응답 위에 에러를 쓰려다 커넥션이 강제 종료됨 — 실제로 겪음).
 */
final class RateLimitWebFilter implements WebFilter {

	private record Window(long windowIndex, AtomicInteger count) {
	}

	private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;
	private final long windowSeconds;
	private final int limit;

	RateLimitWebFilter(ObjectMapper objectMapper, long windowSeconds, int limit) {
		this.objectMapper = objectMapper;
		this.windowSeconds = windowSeconds;
		this.limit = limit;
	}

	/** 인증된 요청만 sub 기준으로 카운트하고, 그 외(permitAll 경로 등)는 그대로 통과시킨다. */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.map(RateLimitWebFilter::extractSubject)
				.defaultIfEmpty(Optional.empty())
				.flatMap(subject -> subject.isPresent()
						? applyLimit(exchange, chain, subject.get())
						: chain.filter(exchange));
	}

	private static Optional<String> extractSubject(Authentication authentication) {
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
			return Optional.of(jwtAuthentication.getToken().getSubject());
		}
		return Optional.empty();
	}

	private Mono<Void> applyLimit(ServerWebExchange exchange, WebFilterChain chain, String sub) {
		long now = Instant.now().getEpochSecond();
		long windowIndex = now / windowSeconds;
		Window window = counters.compute(sub, (key, existing) -> {
			if (existing == null || existing.windowIndex() != windowIndex) {
				return new Window(windowIndex, new AtomicInteger(1));
			}
			existing.count().incrementAndGet();
			return existing;
		});

		if (window.count().get() > limit) {
			long retryAfterSeconds = windowSeconds - (now % windowSeconds);
			exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
			return EdgeErrorResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", objectMapper);
		}
		return chain.filter(exchange);
	}

}
