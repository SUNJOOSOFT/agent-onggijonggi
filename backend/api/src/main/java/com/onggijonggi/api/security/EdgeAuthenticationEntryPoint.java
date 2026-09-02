package com.onggijonggi.api.security;

import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : EdgeAuthenticationEntryPoint.java
 * Description : JWT 인증 실패를 401 + 공통 ErrorResponse 봉투로 변환한다.
 *               401 세분화는 베스트에포트다: Authorization 헤더 자체가 없으면 UNAUTHENTICATED,
 *               있는데 만료 문구가 감지되면 TOKEN_EXPIRED, 그 외(서명 불일치·형식 오류 등)는 TOKEN_INVALID.
 *               계약상 CLIENT는 401이면 코드 무관하게 동일 처리하므로 세분화 실패가 UX를 깨지 않는다.
 */
@Component
public class EdgeAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public EdgeAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
		String code = resolveCode(exchange, ex);
		return EdgeErrorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, code, message(code), objectMapper);
	}

	private String resolveCode(ServerWebExchange exchange, AuthenticationException ex) {
		if (exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
			return "UNAUTHENTICATED";
		}
		if (ex instanceof OAuth2AuthenticationException oauth2Ex) {
			String description = oauth2Ex.getError().getDescription();
			if (description != null && description.toLowerCase(Locale.ROOT).contains("expired")) {
				return "TOKEN_EXPIRED";
			}
			return "TOKEN_INVALID";
		}
		return "UNAUTHENTICATED";
	}

	private String message(String code) {
		return switch (code) {
			case "TOKEN_EXPIRED" -> "인증 토큰이 만료되었습니다.";
			case "TOKEN_INVALID" -> "인증 토큰이 유효하지 않습니다.";
			default -> "인증이 필요합니다.";
		};
	}

}
