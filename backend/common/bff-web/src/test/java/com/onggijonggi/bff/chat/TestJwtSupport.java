package com.onggijonggi.bff.chat;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Class Name : TestJwtSupport.java
 * Description : 02·EDGE JWT 검증 테스트용 — 실 Keycloak 없이 로컬 RSA 키쌍으로 서명한 토큰을 만든다.
 *               FakeJwtDecoderConfig가 RSA_KEY의 공개키로 디코더를
 *               구성해 이 토큰들을 검증한다.
 */
final class TestJwtSupport {

	static final RSAKey RSA_KEY = generateKey();

	/** Keycloak ogjg-client에 Audience 매퍼를 추가한 뒤 실제 토큰에서 확인한 aud 조합. */
	private static final List<String> DEFAULT_AUDIENCE = List.of("ogjg-client", "account");

	private TestJwtSupport() {
	}

	/**
	* signedJwt: 지정한 subject·realm role로 서명된 JWT 문자열을 만든다. aud는 기본값(DEFAULT_AUDIENCE)을 쓴다.
	* @param subject sub·preferred_username 클레임에 쓰는 값
	* @param roles realm_access.roles 클레임(빈 리스트면 role 없는 사용자)
	* @return "Authorization: Bearer " 헤더에 그대로 쓸 수 있는 서명된 JWT
	*/
	static String signedJwt(String subject, List<String> roles) {
		return signedJwt(subject, roles, DEFAULT_AUDIENCE);
	}

	/**
	* signedJwt: aud 클레임까지 지정할 수 있는 오버로드 — audience 불일치 테스트 전용.
	* @param subject sub·preferred_username 클레임에 쓰는 값
	* @param roles realm_access.roles 클레임(빈 리스트면 role 없는 사용자)
	* @param audience aud 클레임 값
	* @return "Authorization: Bearer " 헤더에 그대로 쓸 수 있는 서명된 JWT
	*/
	static String signedJwt(String subject, List<String> roles, List<String> audience) {
		return sign(claimsBuilder(subject, roles).audience(audience));
	}

	/**
	* signedJwtWithoutAudience: aud 클레임 자체를 아예 생략한 토큰을 만든다 — Keycloak admin-cli처럼
	* audience 매퍼가 없는 클라이언트가 발급하는 토큰을 재현한다(RFC 7519 §4.1.3상 aud는 OPTIONAL).
	* @param subject sub·preferred_username 클레임에 쓰는 값
	* @param roles realm_access.roles 클레임(빈 리스트면 role 없는 사용자)
	* @return aud 클레임이 없는, 서명된 JWT
	*/
	static String signedJwtWithoutAudience(String subject, List<String> roles) {
		return sign(claimsBuilder(subject, roles));
	}

	/** signedJwtExpiringAt: exp 클레임을 지정한 시각으로 덮어쓴 토큰을 만든다 — 이슈 #62의 토큰 만료
	 * 강제 종료 타이머를 테스트에서 짧은 지연으로 재현하기 위한 전용 오버로드. aud는 signedJwt와
	 * 동일하게 DEFAULT_AUDIENCE를 쓴다.
	 * @param subject sub·preferred_username 클레임에 쓰는 값
	 * @param roles realm_access.roles 클레임(빈 리스트면 role 없는 사용자)
	 */
	static String signedJwtExpiringAt(String subject, List<String> roles, Instant expiresAt) {
		return sign(claimsBuilder(subject, roles).audience(DEFAULT_AUDIENCE).expirationTime(Date.from(expiresAt)));
	}

	private static JWTClaimsSet.Builder claimsBuilder(String subject, List<String> roles) {
		return new JWTClaimsSet.Builder()
				.subject(subject)
				.issuer("http://localhost:8081/realms/app-realm")
				.issueTime(new Date())
				.expirationTime(new Date(System.currentTimeMillis() + 300_000))
				.claim("preferred_username", subject)
				.claim("realm_access", Map.of("roles", roles));
	}

	private static String sign(JWTClaimsSet.Builder claimsBuilder) {
		try {
			SignedJWT signedJwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
					claimsBuilder.build());
			signedJwt.sign(new RSASSASigner(RSA_KEY));
			return signedJwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("테스트 JWT 서명 실패", e);
		}
	}

	private static RSAKey generateKey() {
		try {
			return new RSAKeyGenerator(2048).keyID("test-key").generate();
		} catch (JOSEException e) {
			throw new IllegalStateException("테스트 RSA 키 생성 실패", e);
		}
	}

}
