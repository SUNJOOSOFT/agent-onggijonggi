package com.onggijonggi.api.chat;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Class Name : CollabWebSocketHandlerTest.java
 * Description : 이슈 #3 — /api/ws/{threadId}의 실제 프로덕션 배선(WsSecurityConfig +
 *               CollabWebSocketHandler)을 서브프로토콜 인증 기준으로 검증한다. 이슈 #7 스파이크가
 *               확인했던 "인증 컨텍스트가 메시지 루프까지 전파된다"는 사실을, Authorization 헤더가
 *               아니라 Sec-WebSocket-Protocol 조건에서 다시 확인한다.
 *               이슈 #16이 방 단위 방송을 들이면서, 같은 방의 두 실제 클라이언트가 한 메시지를
 *               함께 받는지와 잘못된 threadId를 거르는지도 여기서 함께 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ChatControllerTest.FakeChatModelConfig.class, FakeJwtDecoderConfig.class})
class CollabWebSocketHandlerTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	private final ObjectMapper objectMapper = new JsonMapper();

	@LocalServerPort
	private int port;

	@Test
	void broadcastsAValidatedFrameBackToTheAuthenticatedSender() throws Exception {
		UUID threadId = UUID.randomUUID();
		String received = exchange("collab-ws-user", threadId,
				List.of("""
						{"type":"chat.message","content":"hello","sessionId":"ignored","from":"ignored"}
						"""), 1).get(0);

		WsFrame frame = objectMapper.readValue(received, WsFrame.class);

		assertThat(frame).isInstanceOfSatisfying(ChatMessageFrame.class, message -> {
			assertThat(message.sessionId()).isEqualTo(threadId);
			assertThat(message.from()).isNotNull();
			assertThat(message.content()).isEqualTo("hello");
		});
		assertThat(received).doesNotContain("connected:", "presence.join");
	}

	@Test
	void keepsConnectionAfterMalformedFrameAndUsesDistinctTraceIds() throws Exception {
		UUID threadId = UUID.randomUUID();
		List<String> received = exchange("malformed-user", threadId,
				List.of("not-json", "{\"type\":\"chat.message\",\"content\":\"   \"}",
						"{\"type\":\"unknown\",\"content\":\"ignored\"}",
						"{\"type\":\"chat.message\",\"content\":\"valid\"}"), 4);

		ErrorFrame first = (ErrorFrame) objectMapper.readValue(received.get(0), WsFrame.class);
		ErrorFrame second = (ErrorFrame) objectMapper.readValue(received.get(1), WsFrame.class);
		ErrorFrame third = (ErrorFrame) objectMapper.readValue(received.get(2), WsFrame.class);
		ChatMessageFrame valid = (ChatMessageFrame) objectMapper.readValue(received.get(3), WsFrame.class);

		assertThat(first.code()).isEqualTo("MALFORMED_REQUEST");
		assertThat(second.code()).isEqualTo("MALFORMED_REQUEST");
		assertThat(third.code()).isEqualTo("MALFORMED_REQUEST");
		assertThat(List.of(first.traceId(), second.traceId(), third.traceId())).doesNotHaveDuplicates();
		assertThat(first.traceId()).isNotBlank();
		assertThat(valid.content()).isEqualTo("valid");
	}

	@Test
	void ignoresKnownServerOnlyFrameTypes() throws Exception {
		UUID threadId = UUID.randomUUID();
		List<String> received = exchange("server-frame-user", threadId,
				List.of("{\"type\":\"presence.join\",\"sessionId\":\"" + threadId + "\"}",
						"{\"type\":\"chat.message\",\"content\":\"accepted\"}"), 1);

		ChatMessageFrame frame = (ChatMessageFrame) objectMapper.readValue(received.get(0), WsFrame.class);
		assertThat(frame.content()).isEqualTo("accepted");
	}

	@Test
	void rejectsBinaryFramesWithoutClosingTheConnection() throws Exception {
		UUID threadId = UUID.randomUUID();
		String token = TestJwtSupport.signedJwt("binary-user", List.of("USER"));
		List<String> received = new CopyOnWriteArrayList<>();

		new ReactorNettyWebSocketClient()
				.execute(wsUri(threadId), allowedHeaders(), protocolHandler(token, session -> {
					return WsTestExchange.exchange(session, active -> Flux.just(
							active.binaryMessage(factory -> factory.wrap(new byte[] {1, 2, 3})),
							active.textMessage("{\"type\":\"chat.message\",\"content\":\"after binary\"}")),
							2, message -> received.add(message.getPayloadAsText()));
				}))
				.block(WsTestTimeouts.BLOCK);

		ErrorFrame error = (ErrorFrame) objectMapper.readValue(received.get(0), WsFrame.class);
		ChatMessageFrame valid = (ChatMessageFrame) objectMapper.readValue(received.get(1), WsFrame.class);
		assertThat(error.code()).isEqualTo("MALFORMED_REQUEST");
		assertThat(valid.content()).isEqualTo("after binary");
	}

	@Test
	void broadcastsOneMessageToTwoRealClientsInTheSameRoom() throws Exception {
		UUID threadId = UUID.randomUUID();
		Sinks.Many<String> firstOutbound = Sinks.many().unicast().onBackpressureBuffer();
		Sinks.Many<String> secondOutbound = Sinks.many().unicast().onBackpressureBuffer();
		List<String> firstReceived = new CopyOnWriteArrayList<>();
		List<String> secondReceived = new CopyOnWriteArrayList<>();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch received = new CountDownLatch(2);
		CountDownLatch completed = new CountDownLatch(2);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Disposable first = openClient("room-user-1", threadId, firstOutbound,
				firstReceived, ready, received, completed, failure);
		Disposable second = openClient("room-user-2", threadId, secondOutbound,
				secondReceived, ready, received, completed, failure);

		try {
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			firstOutbound.tryEmitNext("{\"type\":\"chat.message\",\"content\":\"for everyone\"}");
			assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(failure.get()).isNull();

			ChatMessageFrame firstFrame = (ChatMessageFrame) objectMapper.readValue(firstReceived.get(0), WsFrame.class);
			ChatMessageFrame secondFrame = (ChatMessageFrame) objectMapper.readValue(secondReceived.get(0), WsFrame.class);
			assertThat(firstFrame).isEqualTo(secondFrame);
			assertThat(firstFrame.sessionId()).isEqualTo(threadId);
		} finally {
			firstOutbound.tryEmitComplete();
			secondOutbound.tryEmitComplete();
			first.dispose();
			second.dispose();
		}
	}

	@Test
	void sendsMalformedRequestAndClosesNormallyForInvalidThreadId() throws Exception {
		String token = TestJwtSupport.signedJwt("invalid-thread-user", List.of("USER"));
		HttpHeaders headers = allowedHeaders();
		AtomicReference<String> received = new AtomicReference<>();
		AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

		new ReactorNettyWebSocketClient()
				.execute(URI.create("ws://localhost:" + port + "/api/ws/not-a-uuid"), headers, new WebSocketHandler() {
					@Override
					public List<String> getSubProtocols() {
						return List.of("access_token", token);
					}

					@Override
					public Mono<Void> handle(WebSocketSession session) {
						return session.receive()
								.doOnNext(message -> received.set(message.getPayloadAsText()))
								.then(session.closeStatus().doOnNext(closeStatus::set))
								.then();
					}
				})
				.block(WsTestTimeouts.BLOCK);

		ErrorFrame error = (ErrorFrame) objectMapper.readValue(received.get(), WsFrame.class);
		assertThat(error.sessionId()).isNull();
		assertThat(error.code()).isEqualTo("MALFORMED_REQUEST");
		assertThat(closeStatus.get().getCode()).isEqualTo(1000);
	}

	@Test
	void closesConnectionWithCode1000WhenClientClosesNormally() {
		UUID threadId = UUID.randomUUID();
		String token = TestJwtSupport.signedJwt("normal-close-user", List.of("USER"));
		AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient(
				HttpClient.create(ConnectionProvider.newConnection()));

		client.execute(wsUri(threadId), allowedHeaders(), new WebSocketHandler() {
			@Override
			public List<String> getSubProtocols() {
				return List.of("access_token", token);
			}

			@Override
			public Mono<Void> handle(WebSocketSession session) {
				return Mono.when(session.receive().then(),
						session.close(), session.closeStatus().doOnNext(closeStatus::set).then());
			}
		}).block(WsTestTimeouts.BLOCK);

		assertThat(closeStatus.get().getCode()).isEqualTo(1000);
	}

	@Test
	void rejectsHandshakeWithoutSubProtocolAndRejectsTheOldPath() {
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

		assertThatThrownBy(() -> client.execute(wsUri(UUID.randomUUID()), allowedHeaders(), session -> Mono.empty())
				.block(WsTestTimeouts.BLOCK))
				.isInstanceOf(WebSocketClientHandshakeException.class)
				.hasMessageContaining("401");

		String token = TestJwtSupport.signedJwt("old-path-user", List.of("USER"));
		assertThatThrownBy(() -> client.execute(URI.create("ws://localhost:" + port + "/api/ws"),
				allowedHeaders(), protocolHandler(token, session -> Mono.empty())).block(WsTestTimeouts.BLOCK))
				.isInstanceOf(WebSocketClientHandshakeException.class);

		assertThatThrownBy(() -> client.execute(
				URI.create("ws://localhost:" + port + "/api/ws/" + UUID.randomUUID() + "/extra"),
				allowedHeaders(), protocolHandler(token, session -> Mono.empty())).block(WsTestTimeouts.BLOCK))
				.isInstanceOf(WebSocketClientHandshakeException.class);
	}

	private List<String> exchange(String subject, UUID threadId, List<String> outbound, int expectedFrames) {
		String token = TestJwtSupport.signedJwt(subject, List.of("USER"));
		List<String> received = new CopyOnWriteArrayList<>();

		new ReactorNettyWebSocketClient()
				.execute(wsUri(threadId), allowedHeaders(), protocolHandler(token, session -> {
					return WsTestExchange.exchange(session,
							active -> Flux.fromIterable(outbound).map(active::textMessage), expectedFrames,
							message -> received.add(message.getPayloadAsText()));
				}))
				.block(WsTestTimeouts.BLOCK);

		return received;
	}

	private Disposable openClient(String subject, UUID threadId, Sinks.Many<String> outbound,
			List<String> frames, CountDownLatch ready, CountDownLatch received,
			CountDownLatch completed, AtomicReference<Throwable> failure) {
		String token = TestJwtSupport.signedJwt(subject, List.of("USER"));
		return new ReactorNettyWebSocketClient()
				.execute(wsUri(threadId), allowedHeaders(), protocolHandler(token, session -> {
					return WsTestExchange.exchange(session, active -> outbound.asFlux().map(active::textMessage), 1,
							message -> {
								frames.add(message.getPayloadAsText());
								received.countDown();
							}, ready::countDown)
							.doFinally(ignored -> outbound.tryEmitComplete())
							.then(session.close(CloseStatus.NORMAL));
				}))
				.doOnError(error -> failure.compareAndSet(null, error))
				.doFinally(ignored -> completed.countDown())
				.subscribe();
	}

	private WebSocketHandler protocolHandler(String token,
			java.util.function.Function<WebSocketSession, Mono<Void>> body) {
		return new WebSocketHandler() {
			@Override
			public List<String> getSubProtocols() {
				return List.of("access_token", token);
			}

			@Override
			public Mono<Void> handle(WebSocketSession session) {
				return body.apply(session);
			}
		};
	}

	private URI wsUri(UUID threadId) {
		return URI.create("ws://localhost:" + port + "/api/ws/" + threadId);
	}

	private static HttpHeaders allowedHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);
		return headers;
	}

}
