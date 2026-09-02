package com.onggijonggi.api.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Class Name : WsSubProtocolBearerTokenConverter.java
 * Description : 표준 WebSocket API는 핸드셰이크 요청에 Authorization 헤더를 못 실으므로, 클라이언트가
 *               대신 Sec-WebSocket-Protocol에 [PROTOCOL_NAME, "<jwt>"] 두 값을 실어 보내고 이 컨버터가
 *               두 번째 값을 꺼내 표준 Bearer 토큰처럼 넘긴다. 반환된 인증되지 않은 토큰은 이후
 *               oauth2ResourceServer(jwt) 파이프라인이 그대로 검증한다(WsSecurityConfig 참고).
 *               두 값이 형식대로 안 오면 빈 Mono를 반환해 이후 authorizeExchange가 미인증으로 처리하게 둔다.
 */
public class WsSubProtocolBearerTokenConverter implements ServerAuthenticationConverter {

	/** 클라이언트·서버가 합의한 첫 번째 서브프로토콜 값 — CollabWebSocketHandler.getSubProtocols()와 반드시 같아야 한다. */
	public static final String PROTOCOL_NAME = "access_token";

	/** Spring의 HttpHeaders에는 이 헤더명 상수가 없어 리터럴로 직접 든다. */
	private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		List<String> offered = exchange.getRequest().getHeaders().get(SEC_WEBSOCKET_PROTOCOL);
		if (offered == null || offered.isEmpty()) {
			return Mono.empty();
		}
		// 클라이언트·프록시가 값을 한 줄로 합쳐 보내든(offered.size()==1) 여러 줄로 나눠 보내든
		// (offered.size()>1) 동일하게 처리한다 — 앞엣것만 읽으면 후자에서 토큰을 놓친다.
		String[] parts = String.join(",", offered).split(",", 2);
		if (parts.length != 2 || !PROTOCOL_NAME.equals(parts[0].trim())) {
			return Mono.empty();
		}
		String token = parts[1].trim();
		if (token.isEmpty()) {
			return Mono.empty();
		}
		return Mono.just(new BearerTokenAuthenticationToken(token));
	}

}
