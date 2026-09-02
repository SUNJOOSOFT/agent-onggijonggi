package com.onggijonggi.api.common;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Class Name : TraceIdWebFilter.java
 * Description : 모든 요청에 traceId를 발급하는 글로벌 필터.
 *               시큐리티 체인보다 앞선 최상단에서 동작해, 401/403 등 인증 실패 응답에도
 *               traceId가 포함되도록 한다. exchange attribute와 Reactor Context 양쪽에 저장한다
 *               (exchange를 든 필터/핸들러는 attribute를, exchange가 없는 서비스 계층은 Context를 읽는다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

	/** exchange attribute / Reactor Context에서 traceId를 꺼낼 때 쓰는 키. */
	public static final String TRACE_ID_ATTR = "traceId";

	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String traceId = UUID.randomUUID().toString();
		exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
		exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);
		return chain.filter(exchange).contextWrite(ctx -> ctx.put(TRACE_ID_ATTR, traceId));
	}

}
