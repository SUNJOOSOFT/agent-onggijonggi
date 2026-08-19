package com.onggijonggi.bff.security;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Class Name : KeycloakIdentityProviderService.java
 * Description : IdentityProviderService의 Keycloak 구현체 — jwk-set-uri 기반
 *               디코더(issuer·audience 수동 검증 포함)와 realm_access.roles 클레임 기반
 *               role 매핑을 한 곳으로 합친다. 컨테이너 내부 DNS(jwk-set-uri, 항상 도달 가능)와
 *               브라우저가 보는 공개 주소(issuer)가 서로 달라 discovery 기반 issuer-uri 검증을 쓸 수
 *               없기 때문에 두 값을 분리해서 관리한다.
 */
@Component
public class KeycloakIdentityProviderService implements IdentityProviderService {

	private final String jwkSetUri;
	private final String publicIssuer;
	private final String expectedAudience;

	public KeycloakIdentityProviderService(
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
			@Value("${app.security.public-issuer}") String publicIssuer,
			@Value("${app.security.expected-audience}") String expectedAudience) {
		this.jwkSetUri = jwkSetUri;
		this.publicIssuer = publicIssuer;
		this.expectedAudience = expectedAudience;
	}

	@Override
	public String providerName() {
		return "keycloak";
	}

	@Override
	public ReactiveJwtDecoder jwtDecoder() {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(), issuerValidator(publicIssuer), audienceValidator(expectedAudience));
		decoder.setJwtValidator(validator);
		return decoder;
	}

	@Override
	public Converter<Jwt, Flux<GrantedAuthority>> grantedAuthoritiesConverter() {
		return realmRoleGrantedAuthoritiesConverter();
	}

	private OAuth2TokenValidator<Jwt> issuerValidator(String publicIssuer) {
		return jwt -> {
			String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
			if (publicIssuer.equals(issuer)) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_issuer", "issuer가 신뢰할 수 없는 값입니다.", null));
		};
	}

	/**
	* - RSA 서명 기반 로컬 테스트(FakeJwtDecoderConfig)에서도 issuer 검증과 달리
	*   이 검증기를 그대로 재사용하므로 public static으로 노출한다.
	* - jwt.getAudience()는 aud 클레임이 RFC 7519 §4.1.3상 허용되는 단일 문자열이든 배열이든 항상
	*   List&lt;String&gt;으로 정규화해준다 — JwtClaimValidator&lt;Collection&lt;String&gt;&gt;의 언체크
	*   캐스트는 단일 문자열 aud에서 ClassCastException 위험이 있었다.
	* - aud 클레임 자체가 없는 토큰(RFC 7519상 OPTIONAL)에서는 getAudience()가 null을 반환하므로
	*   바로 위 issuerValidator와 동일하게 null-safe하게 방어한다.
	*/
	public static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
		return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(
						new OAuth2Error("invalid_token", "audience가 일치하지 않습니다.", null));
	}

	/**
	* 인스턴스 상태(jwkSetUri 등) 없이 클레임만으로 동작해, FakeJwtDecoderConfig가
	* 전체 인스턴스를 만들지 않고도 이 로직을 재사용할 수 있도록 public static으로 노출한다.
	*/
	@SuppressWarnings("unchecked")
	public static Converter<Jwt, Flux<GrantedAuthority>> realmRoleGrantedAuthoritiesConverter() {
		return jwt -> {
			Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
			List<String> roles = realmAccess == null
					? List.of()
					: (List<String>) realmAccess.getOrDefault("roles", List.of());
			return Flux.fromIterable(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role));
		};
	}

}
