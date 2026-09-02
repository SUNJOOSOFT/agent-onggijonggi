package com.onggijonggi.api.security;

import com.onggijonggi.api.common.ErrorResponse;
import com.onggijonggi.api.common.TraceIdWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : EdgeErrorResponseWriter.java
 * Description : EdgeAuthenticationEntryPoint(401)·EdgeAccessDeniedHandler(403)가 공통으로 쓰는
 *               ErrorResponse 봉투 직접 write 로직. TraceIdWebFilter가 담아둔
 *               traceId를 exchange attribute에서 읽어 그대로 응답에 실어, 감사 로그와 대조 가능하게 한다.
 *               Boot 4가 기본 자동구성하는 ObjectMapper는 Jackson 3(tools.jackson.databind)이다.
 */
final class EdgeErrorResponseWriter {

	private EdgeErrorResponseWriter() {
	}

	static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message,
			ObjectMapper objectMapper) {
		String traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTR);
		ErrorResponse body = ErrorResponse.of(code, message, traceId);

		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

		byte[] bytes = objectMapper.writeValueAsBytes(body);
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

}
