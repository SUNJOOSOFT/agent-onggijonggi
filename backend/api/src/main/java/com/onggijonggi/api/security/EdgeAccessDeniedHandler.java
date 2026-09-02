package com.onggijonggi.api.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : EdgeAccessDeniedHandler.java
 * Description : 인증은 됐으나 인가에서 거부된 요청(role 불충분, denyAll 경로 등)을 403 + 공통
 *               ErrorResponse 봉투로 변환한다.
 */
@Component
public class EdgeAccessDeniedHandler implements ServerAccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public EdgeAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
		return EdgeErrorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.", objectMapper);
	}

}
