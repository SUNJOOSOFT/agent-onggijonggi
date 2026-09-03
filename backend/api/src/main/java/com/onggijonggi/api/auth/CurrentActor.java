package com.onggijonggi.api.auth;

import java.util.UUID;

/**
 * Class Name : CurrentActor.java
 * Description : 인증된 요청자 — 외부 IdP subject와 내부 app_user.id를 함께 들고 다닌다. Controller와
 *               서비스가 JwtAuthenticationToken을 직접 알지 않게 하는 공급자 중립 표현이다.
 *               역할(roles)은 담지 않는다 — 02·EDGE(SecurityConfig)가 필터체인에서 hasRole("USER")를
 *               이미 강제하므로 읽는 쪽이 없다. 필요해지면 그때 필드를 더한다.
 */
public record CurrentActor(UUID userId, String subject) {
}
