package com.onggijonggi.bff.chat;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Class Name : CollabWebSocketHandlerTest.java
 * Description : 이슈 #3 — /api/ws의 실제 프로덕션 배선(WsSecurityConfig + CollabWebSocketHandler)을
 *               서브프로토콜 인증 기준으로 검증한다. 이슈 #7 스파이크가 확인했던 "인증 컨텍스트가
 *               메시지 루프까지 전파된다"는 사실을, Authorization 헤더가 아니라 Sec-WebSocket-Protocol
 *               조건에서 다시 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ChatControllerTest.FakeChatModelConfig.class, FakeJwtDecoderConfig.class})
class CollabWebSocketHandlerTest {

	@LocalServerPort
	private int port;

	@Test
	void acceptsHandshakeAndEchoesAuthenticatedSubjectWhenTokenOfferedViaSubProtocol() {
		String subject = "collab-ws-user";
		String token = TestJwtSupport.signedJwt(subject, List.of("USER"));

		AtomicReference<String> received = new AtomicReference<>();
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

		// ReactorNettyWebSocketClient는 Sec-WebSocket-Protocol 요청 헤더를 handler.getSubProtocols()로
		// 직접 구성한다 — HttpHeaders로 수동으로 실은 값은 무시되고 덮어써진다(디버그 로그로 실측 확인).
		// 그래서 실제 브라우저가 new WebSocket(url, ["access_token", token])으로 보내는 것과 동등하게,
		// 여기서도 두 값을 getSubProtocols()에 순서대로 담아야 한다.
		client.execute(URI.create("ws://localhost:" + port + "/api/ws"), new HttpHeaders(), new WebSocketHandler() {

					@Override
					public List<String> getSubProtocols() {
						return List.of("access_token", token);
					}

					@Override
					public Mono<Void> handle(WebSocketSession session) {
						return session.receive()
								.take(1)
								.doOnNext(message -> received.set(message.getPayloadAsText()))
								.then();
					}
				})
				.block(Duration.ofSeconds(5));

		assertThat(received.get()).isEqualTo("connected:" + subject);
	}

	/** 서브프로토콜을 아예 안 보내면 인증 컨버터가 빈 Mono를 반환하고, authorizeExchange가 미인증으로 거부한다(핸드셰이크 단계 401). */
	@Test
	void rejectsHandshakeWhenNoSubProtocolOffered() {
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

		// RuntimeException 하나만 확인하면 포트 오류 등 무관한 실패도 통과해버린다 — 실제로 401
		// 응답 때문에 핸드셰이크가 거부됐는지까지 확인한다.
		assertThatThrownBy(() -> client
				.execute(URI.create("ws://localhost:" + port + "/api/ws"), new HttpHeaders(), session -> Mono.empty())
				.block(Duration.ofSeconds(5)))
				.isInstanceOf(WebSocketClientHandshakeException.class)
				.hasMessageContaining("401");
	}

}
