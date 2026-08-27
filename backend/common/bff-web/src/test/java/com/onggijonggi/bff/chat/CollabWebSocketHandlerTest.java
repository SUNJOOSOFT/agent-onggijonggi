package com.onggijonggi.bff.chat;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

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

	/** app.cors.allowed-origins에 들어 있는 값 — Origin 검사(이슈 #5)를 통과시키기 위한 것이고,
	 * 이 테스트의 관심사는 어디까지나 서브프로토콜 인증이다. */
	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@LocalServerPort
	private int port;

	@Test
	void acceptsHandshakeAndEchoesAuthenticatedSubjectWhenTokenOfferedViaSubProtocol() {
		String subject = "collab-ws-user";
		String token = TestJwtSupport.signedJwt(subject, List.of("USER"));

		AtomicReference<String> received = new AtomicReference<>();
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

		// 이슈 #5 이후 WsOriginWebFilter가 Origin을 검사한다. 넘기지 않으면 Netty가 ws://localhost:<랜덤포트>
		// 기준으로 Origin을 스스로 만들어 붙이고, 그 값은 화이트리스트에 없어 403으로 끊긴다.
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);

		// ReactorNettyWebSocketClient는 Sec-WebSocket-Protocol 요청 헤더를 handler.getSubProtocols()로
		// 직접 구성한다 — HttpHeaders로 수동으로 실은 값은 무시되고 덮어써진다(디버그 로그로 실측 확인).
		// 그래서 실제 브라우저가 new WebSocket(url, ["access_token", token])으로 보내는 것과 동등하게,
		// 여기서도 두 값을 getSubProtocols()에 순서대로 담아야 한다.
		client.execute(URI.create("ws://localhost:" + port + "/api/ws"), headers, new WebSocketHandler() {

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

	/** 이슈 #62 — 연결 유지 중 토큰이 만료되면 서버가 연결을 끊지 않고 그대로 두는 게 아니라, 커스텀 close
	 *  code(4000)로 강제 종료해 클라이언트(#4)가 일반 네트워크 끊김(1006)과 구분해 재조회·재연결하게 한다. */
	@Test
	void closesConnectionWithCode4000WhenTokenExpiresWhileConnected() {
		String subject = "token-expiry-user";
		String token = TestJwtSupport.signedJwtExpiringAt(subject, List.of("USER"), Instant.now().plusSeconds(2));

		AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
		// 다른 테스트가 반환한 풀링 커넥션을 재사용하면 서버가 닫는 중인 커넥션을 물려받아 핸드셰이크가
		// 꼬일 수 있다 — 이 테스트만 풀 없이 매번 새 커넥션을 맺는다.
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient(HttpClient.create(ConnectionProvider.newConnection()));

		// 빈 HttpHeaders를 넘기면 Netty가 Origin을 스스로 만들어 붙여 403으로 끊긴다(위 테스트 주석 참고).
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);

		client.execute(URI.create("ws://localhost:" + port + "/api/ws"), headers, new WebSocketHandler() {

					@Override
					public List<String> getSubProtocols() {
						return List.of("access_token", token);
					}

					@Override
					public Mono<Void> handle(WebSocketSession session) {
						// receive()도 함께 구독해야 인바운드 프레임에 수요(demand)가 걸린다 — closeStatus()만
						// 구독하면 서버가 보낸 프레임이 전혀 소비되지 않아 close 프레임도 도달하지 않는다.
						Mono<Void> drainInbound = session.receive().then();
						Mono<Void> captureCloseStatus = session.closeStatus().doOnNext(closeStatus::set).then();
						return Mono.when(drainInbound, captureCloseStatus);
					}
				})
				.block(Duration.ofSeconds(5));

		assertThat(closeStatus.get().getCode()).isEqualTo(4000);
	}

	/** 이슈 #62 — 토큰 만료 타이머를 걸어도, 클라이언트가 먼저 정상 종료하면 그 종료(1000)가 그대로
	 *  전달돼야 한다. 타이머 쪽 Mono가 경합에서 져도 뒤늦게 4000으로 덮어쓰지 않는지 확인한다. */
	@Test
	void closesConnectionWithCode1000WhenClientClosesNormallyBeforeTokenExpires() {
		String subject = "normal-close-user";
		String token = TestJwtSupport.signedJwt(subject, List.of("USER"));

		AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient(HttpClient.create(ConnectionProvider.newConnection()));

		// 빈 HttpHeaders를 넘기면 Netty가 Origin을 스스로 만들어 붙여 403으로 끊긴다(위 테스트 주석 참고).
		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);

		client.execute(URI.create("ws://localhost:" + port + "/api/ws"), headers, new WebSocketHandler() {

					@Override
					public List<String> getSubProtocols() {
						return List.of("access_token", token);
					}

					@Override
					public Mono<Void> handle(WebSocketSession session) {
						// receive()를 take(1)로 취소하면서 닫으면 클라이언트 쪽 취소가 종료 핸드셰이크보다
						// 먼저 커넥션을 끊어버려 1006으로 관측된다 — 취소 없이 끝까지 구독해야 한다.
						Mono<Void> drainInbound = session.receive().then();
						Mono<Void> initiateClose = session.close();
						Mono<Void> captureCloseStatus = session.closeStatus().doOnNext(closeStatus::set).then();
						return Mono.when(drainInbound, initiateClose, captureCloseStatus);
					}
				})
				.block(Duration.ofSeconds(5));

		assertThat(closeStatus.get().getCode()).isEqualTo(1000);
	}

	/** 서브프로토콜을 아예 안 보내면 인증 컨버터가 빈 Mono를 반환하고, authorizeExchange가 미인증으로 거부한다(핸드셰이크 단계 401). */
	@Test
	void rejectsHandshakeWhenNoSubProtocolOffered() {
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

		HttpHeaders headers = new HttpHeaders();
		headers.setOrigin(ALLOWED_ORIGIN);

		// RuntimeException 하나만 확인하면 포트 오류 등 무관한 실패도 통과해버린다 — 실제로 401
		// 응답 때문에 핸드셰이크가 거부됐는지까지 확인한다.
		assertThatThrownBy(() -> client
				.execute(URI.create("ws://localhost:" + port + "/api/ws"), headers, session -> Mono.empty())
				.block(Duration.ofSeconds(5)))
				.isInstanceOf(WebSocketClientHandshakeException.class)
				.hasMessageContaining("401");
	}

}
