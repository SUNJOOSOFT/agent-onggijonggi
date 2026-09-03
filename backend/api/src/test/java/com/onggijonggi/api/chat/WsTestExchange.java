package com.onggijonggi.api.chat;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Class Name : WsTestExchange.java
 * Description : WebSocket 통합 테스트에서 수신 demand가 생긴 뒤에만 송신하도록 교환 순서를 보장한다.
 *               관심 없는 프레임을 걸러낼 수도 있다 — 입퇴장 방송(이슈 #25)이 생기면서 한 커넥션에
 *               섞여 오는 프레임이 늘었고, 서버의 방 등록 순서는 클라이언트가 알 수 없어 도착
 *               순서에 기대면 테스트가 흔들린다. take 이전에 걸러 관심 프레임만 세게 한다.
 */
final class WsTestExchange {

	private WsTestExchange() {
	}

	static Mono<Void> exchange(WebSocketSession session,
			Function<WebSocketSession, Publisher<WebSocketMessage>> outbound, long expectedFrames,
			Consumer<WebSocketMessage> onInbound) {
		return exchange(session, outbound, expectedFrames, onInbound, () -> {
		});
	}

	static Mono<Void> exchange(WebSocketSession session,
			Function<WebSocketSession, Publisher<WebSocketMessage>> outbound, long expectedFrames,
			Consumer<WebSocketMessage> onInbound, Runnable onReceiveRequested) {
		return exchange(session, outbound, expectedFrames, onInbound, onReceiveRequested, message -> true);
	}

	static Mono<Void> exchange(WebSocketSession session,
			Function<WebSocketSession, Publisher<WebSocketMessage>> outbound, long expectedFrames,
			Consumer<WebSocketMessage> onInbound, Runnable onReceiveRequested,
			Predicate<WebSocketMessage> interesting) {
		Sinks.One<Void> receiveRequested = Sinks.one();
		Mono<Void> receive = session.receive()
				.doOnRequest(ignored -> {
					receiveRequested.tryEmitEmpty();
					onReceiveRequested.run();
				})
				.filter(interesting)
				.take(expectedFrames)
				.doOnNext(onInbound)
				.then();
		Mono<Void> send = receiveRequested.asMono()
				.then(Mono.defer(() -> session.send(outbound.apply(session))));
		return Mono.when(receive, send);
	}

}
