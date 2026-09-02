package com.onggijonggi.api.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Class Name : WsTestExchangeTest.java
 * Description : WsTestExchange가 수신 demand 전에 송신을 시작하지 않는지 검증한다.
 */
class WsTestExchangeTest {

	@Test
	void startsSendingOnlyAfterTheReceiveFluxHasDemand() {
		WebSocketSession session = mock(WebSocketSession.class);
		WebSocketMessage inbound = mock(WebSocketMessage.class);
		AtomicBoolean receiveRequested = new AtomicBoolean();
		AtomicBoolean receiveRequestedCallbackRan = new AtomicBoolean();
		AtomicBoolean sendStartedAfterReceiveDemand = new AtomicBoolean();

		when(session.receive()).thenReturn(Flux.defer(() -> Flux.just(inbound)
				.doOnRequest(ignored -> receiveRequested.set(true))));
		when(session.send(ArgumentMatchers.<Publisher<WebSocketMessage>>any())).thenAnswer(ignored -> {
			sendStartedAfterReceiveDemand.set(receiveRequested.get() && receiveRequestedCallbackRan.get());
			return Mono.empty();
		});

		WsTestExchange.exchange(session, ignored -> Mono.empty(), 1, ignored -> {
		}, () -> receiveRequestedCallbackRan.set(true)).block();

		assertThat(sendStartedAfterReceiveDemand).isTrue();
	}

}
