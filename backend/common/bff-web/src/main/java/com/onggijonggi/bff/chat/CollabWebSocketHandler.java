package com.onggijonggi.bff.chat;

import java.security.Principal;
import java.util.List;
import com.onggijonggi.bff.security.WsSubProtocolBearerTokenConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Class Name : CollabWebSocketHandler.java
 * Description : 협업채팅 WS 연결의 단일 진입점(이슈 #3) — 지금은 핸드셰이크 인증이 실제로 통과해
 *               핸들러까지 도달하는지만 증명하는 자리표시자다. 인증된 사용자의 subject를 첫 프레임으로
 *               돌려주고 세션을 닫는다. 방 레지스트리·메시지 방송(#16)이 이 핸들러의 본체를 이어받는다.
 */
@Component
public class CollabWebSocketHandler implements WebSocketHandler {

	@Override
	public List<String> getSubProtocols() {
		return List.of(WsSubProtocolBearerTokenConverter.PROTOCOL_NAME);
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return session.getHandshakeInfo().getPrincipal()
				.map(CollabWebSocketHandler::subjectOf)
				.defaultIfEmpty("EMPTY")
				.flatMap(subject -> session.send(Mono.just(session.textMessage("connected:" + subject))))
				.then(session.close());
	}

	/** JwtAuthenticationToken이면 실제 JWT sub 클레임을 쓴다 — getName()만으로는 principal 구현체에 따라 다른 값이 나올 수 있다. */
	private static String subjectOf(Principal principal) {
		if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
			return jwtAuthentication.getToken().getSubject();
		}
		return principal.getName();
	}

}
