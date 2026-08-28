package com.onggijonggi.bff.chat;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.onggijonggi.bff.security.WsSubProtocolBearerTokenConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Class Name : CollabWebSocketHandler.java
 * Description : 협업채팅 WS 연결의 단일 진입점(이슈 #3) — 인증된 사용자의 subject를 첫 프레임으로 돌려준 뒤,
 *               클라이언트가 정상 종료하거나 인증 토큰이 만료될 때까지 세션을 유지한다(이슈 #62).
 *               방 레지스트리·메시지 방송(#16)이 이 핸들러의 본체를 이어받는다.
 */
@Component
public class CollabWebSocketHandler implements WebSocketHandler {

	/** #2에서 확정된 재연결 전략의 서버 측 신호 — 클라이언트(#4)가 이 코드를 보고 세션 재조회 후 재연결한다. */
	private static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4000, "token expired");

	@Override
	public List<String> getSubProtocols() {
		return List.of(WsSubProtocolBearerTokenConverter.PROTOCOL_NAME);
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return session.getHandshakeInfo().getPrincipal()
				.map(CollabWebSocketHandler::sessionInfoOf)
				.defaultIfEmpty(new SessionInfo("EMPTY", null))
				.flatMap(info -> session.send(Mono.just(session.textMessage("connected:" + info.subject())))
						.then(awaitClosedOrTokenExpiry(session, info.tokenExpiresAt())));
	}

	/** 클라이언트가 먼저 닫으면 그대로 끝나고, 토큰 만료 시각이 먼저 오면 서버가 4000으로 강제 종료한다. */
	private static Mono<Void> awaitClosedOrTokenExpiry(WebSocketSession session, Instant tokenExpiresAt) {
		if (tokenExpiresAt == null) {
			return session.close();
		}
		// receive()가 끝났다고 종료 핸드셰이크가 저절로 완료되는 게 아니다 — 여기서 close()를 명시적으로
		// 부르지 않으면 클라이언트는 정상 종료도 1006(비정상)으로 본다.
		Mono<Void> untilPeerOrServerCloses = session.receive().then(session.close());
		Mono<Void> untilTokenExpires = Mono.delay(durationUntil(tokenExpiresAt)).then(session.close(TOKEN_EXPIRED));
		return Mono.firstWithSignal(untilPeerOrServerCloses, untilTokenExpires);
	}

	/** exp가 이미 지났으면(검증 통과 직후 clock skew 안에서도 있을 수 있음) 곧바로 타이머가 울리도록 0으로 clamp한다. */
	private static Duration durationUntil(Instant instant) {
		Duration remaining = Duration.between(Instant.now(), instant);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	/** JwtAuthenticationToken이면 실제 JWT의 sub·exp 클레임을 쓴다 — getName()만으로는 principal 구현체에 따라 다른 값이 나올 수 있다. */
	private static SessionInfo sessionInfoOf(Principal principal) {
		if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
			var jwt = jwtAuthentication.getToken();
			return new SessionInfo(jwt.getSubject(), jwt.getExpiresAt());
		}
		return new SessionInfo(principal.getName(), null);
	}

	private record SessionInfo(String subject, Instant tokenExpiresAt) {
	}

}
