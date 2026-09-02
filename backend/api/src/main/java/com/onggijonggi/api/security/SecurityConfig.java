package com.onggijonggi.api.security;

import com.onggijonggi.common.auth.IdentityProviderService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : SecurityConfig.java
 * Description : 필터체인 CORS → JWT → RBAC → RateLimit 조립.
 *               감사 로그는 아직 구현하지 않았다. CORS가 최상단이라 프리플라이트(OPTIONS)는
 *               CorsWebFilter가 JWT 검증 전에 응답하고, authorizeExchange의 OPTIONS permitAll()은
 *               방어적 이중 안전장치다.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	private final EdgeAuthenticationEntryPoint authenticationEntryPoint;
	private final EdgeAccessDeniedHandler accessDeniedHandler;
	private final ObjectMapper objectMapper;
	private final IdentityProviderService identityProviderService;

	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Value("${app.ratelimit.window-seconds:60}")
	private long rateLimitWindowSeconds;

	@Value("${app.ratelimit.per-minute:20}")
	private int rateLimitPerMinute;

	public SecurityConfig(EdgeAuthenticationEntryPoint authenticationEntryPoint,
			EdgeAccessDeniedHandler accessDeniedHandler, ObjectMapper objectMapper,
			IdentityProviderService identityProviderService) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.objectMapper = objectMapper;
		this.identityProviderService = identityProviderService;
	}

	/**
	* CSRF·formLogin·httpBasic을 비활성화한다 — Bearer 토큰 전용 무상태 API라 세션·폼 로그인을 전제로
	* 한 보호 장치가 필요 없다. 아래 DSL 호출 순서는 실행 순서가 아니다(실행 순서는 클래스 설명 참조).
	*/
	@Bean
	public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeExchange(exchange -> exchange
						.pathMatchers(HttpMethod.OPTIONS).permitAll()
						.pathMatchers("/actuator/health").permitAll()
						.pathMatchers("/api/**").hasRole("USER")
						.anyExchange().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtDecoder(identityProviderService.jwtDecoder())
								.jwtAuthenticationConverter(identityProviderService.jwtAuthenticationConverter()))
						.authenticationEntryPoint(authenticationEntryPoint))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.addFilterAfter(rateLimitWebFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
				.build();
	}

	private RateLimitWebFilter rateLimitWebFilter() {
		return new RateLimitWebFilter(objectMapper, rateLimitWindowSeconds, rateLimitPerMinute);
	}

	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("POST", "GET", "DELETE", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
		configuration.setAllowCredentials(false);
		configuration.setExposedHeaders(List.of("X-Trace-Id", "Retry-After"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
