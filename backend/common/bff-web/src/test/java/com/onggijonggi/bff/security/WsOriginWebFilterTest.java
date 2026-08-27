package com.onggijonggi.bff.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class Name : WsOriginWebFilterTest.java
 * Description : 이슈 #5 — Origin 판정 규칙 세 가지를 필터 단위로 본다. 배선(WsSecurityConfig가 이
 *               필터를 인증 앞에 끼웠는지)은 WsOriginHandshakeTest가 실서버로 확인한다.
 *               "Origin 없음"을 여기서 보는 이유는 Netty의 WebSocket 클라이언트가 호출자 대신
 *               Origin을 만들어 붙여, 통합 테스트로는 헤더 없는 핸드셰이크를 만들 수 없기 때문이다.
 */
class WsOriginWebFilterTest {

	private static final List<String> ALLOWED = List.of("http://localhost:3000", "http://localhost:3010");

	@Test
	void passesThroughWhenOriginIsWhitelisted() {
		AtomicBoolean reachedChain = new AtomicBoolean(false);
		MockServerWebExchange exchange = exchangeWithOrigin("http://localhost:3010");

		filter().filter(exchange, passThrough(reachedChain)).block();

		assertThat(reachedChain).isTrue();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	/** 브라우저가 아닌 클라이언트는 Origin을 원하는 값으로 지어낼 수 있어, 헤더가 없다는 이유로
	 * 거부해봐야 공격자는 화이트리스트 값을 붙이면 그만이다. 막히는 건 정직한 클라이언트뿐이다. */
	@Test
	void passesThroughWhenOriginIsAbsent() {
		AtomicBoolean reachedChain = new AtomicBoolean(false);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ws"));

		filter().filter(exchange, passThrough(reachedChain)).block();

		assertThat(reachedChain).isTrue();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void rejectsWithForbiddenWhenOriginIsNotWhitelisted() {
		AtomicBoolean reachedChain = new AtomicBoolean(false);
		MockServerWebExchange exchange = exchangeWithOrigin("http://evil.example");

		filter().filter(exchange, passThrough(reachedChain)).block();

		assertThat(reachedChain).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	/** 포트만 다른 오리진도 남이다 — 화이트리스트는 문자열 완전 일치로만 본다. */
	@Test
	void rejectsWhenOnlyThePortDiffers() {
		AtomicBoolean reachedChain = new AtomicBoolean(false);
		MockServerWebExchange exchange = exchangeWithOrigin("http://localhost:3001");

		filter().filter(exchange, passThrough(reachedChain)).block();

		assertThat(reachedChain).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	private static WsOriginWebFilter filter() {
		return new WsOriginWebFilter(ALLOWED, new ObjectMapper());
	}

	private static MockServerWebExchange exchangeWithOrigin(String origin) {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/ws").header("Origin", origin));
	}

	/** 통과 여부를 기록하는 체인. 체인이 불렸다면 필터가 요청을 넘겼다는 뜻이다. */
	private static WebFilterChain passThrough(AtomicBoolean reached) {
		return exchange -> {
			reached.set(true);
			return Mono.empty();
		};
	}

}
