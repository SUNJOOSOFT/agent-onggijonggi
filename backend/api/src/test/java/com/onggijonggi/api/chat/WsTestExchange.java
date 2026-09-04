package com.onggijonggi.api.chat;

import java.time.Duration;
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
 *               첫 프레임 전송은 {@link #FIRST_FRAME_SEND_DELAY}만큼 늦춘다(이슈 #102).
 */
final class WsTestExchange {

	/**
	 * reactor-netty의 WebSocket 업그레이드 경로에는 핸드셰이크 응답을 플러시하는 시점과, 채널을
	 * 실제 WebSocket 세션으로 전환하는 rebind 리스너 콜백이 실행되는 시점 사이에 좁은 창이 있다
	 * ({@code WebsocketServerOperations.initHandshaker}). 그 창에 도착한 프레임은 이미 끝난
	 * 이전 HTTP 교환의 {@code FluxReceive}로 잘못 배달돼 예외·로그 없이 조용히 release된다
	 * ({@code FluxReceive.onInboundNext}, 리시버가 이미 종결된 경우). 실제 클라이언트는 네트워크
	 * 왕복 지연 때문에 이 창을 사실상 항상 넘기지만, 같은 JVM 안에서 지연 없이 곧바로 응답하는
	 * 테스트 클라이언트는 이 창을 맞힐 수 있다. 첫 프레임 전송을 살짝 늦춰 실제 클라이언트가
	 * 자연스럽게 갖는 지연을 흉내 낸다.
	 */
	private static final Duration FIRST_FRAME_SEND_DELAY = Duration.ofMillis(20);

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
				.then(Mono.delay(FIRST_FRAME_SEND_DELAY))
				.then(Mono.defer(() -> session.send(outbound.apply(session))));
		return Mono.when(receive, send);
	}

}
