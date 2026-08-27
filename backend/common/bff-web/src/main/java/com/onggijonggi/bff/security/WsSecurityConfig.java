package com.onggijonggi.bff.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : WsSecurityConfig.java
 * Description : /api/ws/** 전용 인증 체인. 메인 SecurityConfig.filterChain()은 securityMatcher가 없어
 *               전 경로를 잡으므로(anyExchange().denyAll()), 이 체인이 더 높은 순위로 먼저 매칭돼야
 *               /api/ws 요청이 Authorization 헤더 부재를 이유로 메인 체인에 거부되지 않는다.
 *               이슈 #7 스파이크(WsSecurityContextSpikeTest)가 이 배선으로 인증 컨텍스트가 WS 메시지
 *               루프까지 전파됨을 먼저 확인했다 — 다만 그 스파이크는 Authorization 헤더 조건이었고,
 *               여기서는 서브프로토콜(WsSubProtocolBearerTokenConverter)로 토큰을 받는 점이 다르다.
 *               Origin 검증은 WsOriginWebFilter가 인증보다 먼저 맡는다(이슈 #5) — SecurityConfig의
 *               CORS 설정은 이 체인에 닿지 않으므로 그 필터가 이 경로의 유일한 Origin 검사다.
 *               레이트리밋은 아직 넣지 않았다 — 별도 이슈(#6) 범위다.
 */
@Configuration
public class WsSecurityConfig {

	private final EdgeAuthenticationEntryPoint authenticationEntryPoint;
	private final EdgeAccessDeniedHandler accessDeniedHandler;
	private final IdentityProviderService identityProviderService;
	private final ObjectMapper objectMapper;

	/** HTTP CORS와 같은 화이트리스트를 쓴다 — 프론트 오리진은 하나뿐이라 값이 두 곳으로 갈리는 편이 더 위험하다. */
	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;

	public WsSecurityConfig(EdgeAuthenticationEntryPoint authenticationEntryPoint,
			EdgeAccessDeniedHandler accessDeniedHandler, IdentityProviderService identityProviderService,
			ObjectMapper objectMapper) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.identityProviderService = identityProviderService;
		this.objectMapper = objectMapper;
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public SecurityWebFilterChain wsFilterChain(ServerHttpSecurity http) {
		return http
				.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/ws/**"))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				// 토큰을 검증하기 전에 거른다 — Origin이 틀린 요청은 JWT를 파싱해볼 이유가 없다.
				.addFilterBefore(new WsOriginWebFilter(allowedOrigins, objectMapper),
						SecurityWebFiltersOrder.AUTHENTICATION)
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
