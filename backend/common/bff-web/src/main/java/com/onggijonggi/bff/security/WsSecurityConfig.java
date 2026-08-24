package com.onggijonggi.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

/**
 * Class Name : WsSecurityConfig.java
 * Description : /api/ws/** 전용 인증 체인. 메인 SecurityConfig.filterChain()은 securityMatcher가 없어
 *               전 경로를 잡으므로(anyExchange().denyAll()), 이 체인이 더 높은 순위로 먼저 매칭돼야
 *               /api/ws 요청이 Authorization 헤더 부재를 이유로 메인 체인에 거부되지 않는다.
 *               이슈 #7 스파이크(WsSecurityContextSpikeTest)가 이 배선으로 인증 컨텍스트가 WS 메시지
 *               루프까지 전파됨을 먼저 확인했다 — 다만 그 스파이크는 Authorization 헤더 조건이었고,
 *               여기서는 서브프로토콜(WsSubProtocolBearerTokenConverter)로 토큰을 받는 점이 다르다.
 *               CORS(Origin 검증)와 레이트리밋은 의도적으로 넣지 않았다 — 각각 별도 이슈(#5, #6) 범위다.
 */
@Configuration
public class WsSecurityConfig {

	private final EdgeAuthenticationEntryPoint authenticationEntryPoint;
	private final EdgeAccessDeniedHandler accessDeniedHandler;
	private final IdentityProviderService identityProviderService;

	public WsSecurityConfig(EdgeAuthenticationEntryPoint authenticationEntryPoint,
			EdgeAccessDeniedHandler accessDeniedHandler, IdentityProviderService identityProviderService) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.identityProviderService = identityProviderService;
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityWebFilterChain wsFilterChain(ServerHttpSecurity http) {
		return http
				.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/ws/**"))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchange -> exchange.anyExchange().hasRole("USER"))
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenConverter(new WsSubProtocolBearerTokenConverter())
						.jwt(jwt -> jwt.jwtDecoder(identityProviderService.jwtDecoder())
								.jwtAuthenticationConverter(identityProviderService.jwtAuthenticationConverter()))
						.authenticationEntryPoint(authenticationEntryPoint))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

}
