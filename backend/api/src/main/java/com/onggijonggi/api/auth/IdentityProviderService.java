package com.onggijonggi.api.auth;

import com.onggijonggi.api.auth.keycloak.KeycloakIdentityProviderService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import reactor.core.publisher.Flux;

/**
 * Class Name : IdentityProviderService.java
 * Description : 특정 IdP 구현에 종속되지 않는 인증 부품 인터페이스 — JWT 디코더
 *               조립과 role→GrantedAuthority 매핑을 캡슐화해, 향후 Okta 등으로 교체할 때 이 인터페이스의
 *               새 구현체 하나만 추가하면 SecurityConfig를 다시 건드리지 않아도 되게 한다. 지금은
 *               Keycloak(KeycloakIdentityProviderService)이 유일한 구현체다.
 */
public interface IdentityProviderService {

	/** 로그·진단에서 "지금 어느 IdP로 붙어 있는지"를 식별하는 용도(예: "keycloak"). */
	String providerName();

	ReactiveJwtDecoder jwtDecoder();

	Converter<Jwt, Flux<GrantedAuthority>> grantedAuthoritiesConverter();

	/** grantedAuthoritiesConverter()를 물린 조립을 한 곳에 둬, SecurityConfig·WsSecurityConfig가 각자 반복하지 않게 한다. */
	default ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
		ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter());
		return converter;
	}

}
