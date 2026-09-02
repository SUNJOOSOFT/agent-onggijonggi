package com.onggijonggi.api.chat;

import java.util.function.Consumer;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Class Name : WsTestExchange.java
 * Description : WebSocket 통합 테스트에서 수신 demand가 생긴 뒤에만 송신하도록 교환 순서를 보장한다.
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
		Sinks.One<Void> receiveRequested = Sinks.one();
		Mono<Void> receive = session.receive()
				.doOnRequest(ignored -> {
					receiveRequested.tryEmitEmpty();
					onReceiveRequested.run();
				})
				.take(expectedFrames)
				.doOnNext(onInbound)
				.then();
		Mono<Void> send = receiveRequested.asMono()
				.then(Mono.defer(() -> session.send(outbound.apply(session))));
		return Mono.when(receive, send);
	}

}
