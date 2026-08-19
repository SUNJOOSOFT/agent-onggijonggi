package com.onggijonggi.bff.chat;

import com.onggijonggi.bff.security.IdentityProviderService;
import com.onggijonggi.bff.security.KeycloakIdentityProviderService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Flux;

/**
 * Class Name : FakeJwtDecoderConfig.java
 * Description : 실 Keycloak 없이 로컬 RSA 공개키(TestJwtSupport.RSA_KEY)로 검증하는 디코더로
 *               교체한다(RestTestClient.bindToServer()가 실서버 바인딩이라 mockJwt() 등
 *               WebTestClient 모의 서버 전용 mutator를 쓸 수 없기 때문).
 *               SecurityConfig가 IdentityProviderService를 통해 명시적으로 디코더를 배선하므로
 *               ReactiveJwtDecoder 빈만으로는 교체되지 않아 IdentityProviderService 전체를
 *               오버라이드한다. audience(aud) 검증과 role 매핑은 KeycloakIdentityProviderService의
 *               로직을 그대로 재사용한다.
 *               02·EDGE 보안을 통과해야 하는 컨트롤러 테스트가 공유한다
 *               (ChatControllerTest·ModelControllerTest).
 */
@TestConfiguration
class FakeJwtDecoderConfig {

	@Bean
	@Primary
	IdentityProviderService fakeIdentityProviderService() {
		return new IdentityProviderService() {

			@Override
			public String providerName() {
				return "fake";
			}

			@Override
			public ReactiveJwtDecoder jwtDecoder() {
				try {
					NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
							.withPublicKey(TestJwtSupport.RSA_KEY.toRSAPublicKey()).build();
					decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
							KeycloakIdentityProviderService.audienceValidator("ogjg-client")));
					return decoder;
				} catch (com.nimbusds.jose.JOSEException e) {
					throw new IllegalStateException("테스트 JWT 디코더 구성 실패", e);
				}
			}

			@Override
			public Converter<Jwt, Flux<GrantedAuthority>> grantedAuthoritiesConverter() {
				return KeycloakIdentityProviderService.realmRoleGrantedAuthoritiesConverter();
			}
		};
	}

}
