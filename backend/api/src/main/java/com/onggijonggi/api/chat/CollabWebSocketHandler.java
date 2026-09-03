package com.onggijonggi.api.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onggijonggi.api.auth.WsSubProtocolBearerTokenConverter;
import com.onggijonggi.api.auth.UserIdentityService;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

/**
 * Class Name : CollabWebSocketHandler.java
 * Description : 협업 채팅 WebSocket 연결의 수신·송신 수명과 프레임 처리를 담당한다.
 */
@Component
public class CollabWebSocketHandler implements WebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(CollabWebSocketHandler.class);

	private static final String WS_PATH_PREFIX = "/api/ws/";

	private static final int CONNECTION_BUFFER_SIZE = 256;

	private static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4000, "token expired");

	private static final CloseStatus SLOW_CONSUMER = new CloseStatus(1011, "outbound buffer overflow");

	private static final Set<String> SERVER_ONLY_TYPES = Set.of("chat.answer", "presence.join", "error");

	private final ObjectMapper objectMapper;

	private final RoomSessionRegistry roomSessionRegistry;

	private final UserIdentityService userIdentityService;

	public CollabWebSocketHandler(ObjectMapper objectMapper, RoomSessionRegistry roomSessionRegistry,
			UserIdentityService userIdentityService) {
		this.objectMapper = objectMapper;
		this.roomSessionRegistry = roomSessionRegistry;
		this.userIdentityService = userIdentityService;
	}

	@Override
	public List<String> getSubProtocols() {
		return List.of(WsSubProtocolBearerTokenConverter.PROTOCOL_NAME);
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		UUID threadId = threadIdOf(session);
		if (threadId == null) {
			return sendErrorAndClose(session, null, "MALFORMED_REQUEST",
					"threadId는 UUID 형식이어야 합니다.", newTraceId());
		}

		return session.getHandshakeInfo().getPrincipal()
				.map(CollabWebSocketHandler::sessionInfoOf)
				.defaultIfEmpty(new SessionInfo("EMPTY", null))
				.flatMap(info -> userIdentityService.resolveOrProvision(info.subject())
						.onErrorMap(UserProvisioningFailure::new)
						.flatMap(userId -> handleRoomSession(session, threadId, userId, info.tokenExpiresAt()))
						.onErrorResume(UserProvisioningFailure.class, error -> {
							String traceId = newTraceId();
							log.error("WebSocket user provisioning failed threadId={} traceId={}",
									threadId, traceId, error.getCause());
							return sendErrorAndClose(session, threadId, "INTERNAL_ERROR",
									"WebSocket 세션을 초기화하지 못했습니다.", traceId);
						}));
	}

	private Mono<Void> handleRoomSession(WebSocketSession session, UUID threadId, UUID userId,
			Instant tokenExpiresAt) {
		UUID connectionId = UUID.randomUUID();
		Sinks.One<Void> inboundDone = Sinks.one();
		Sinks.One<Void> outboundOverflow = Sinks.one();

		Flux<WsFrame> roomFrames = bufferForConnection(
				roomSessionRegistry.join(threadId, connectionId), outboundOverflow);

		Flux<WsFrame> inboundResponses = session.receive()
				.concatMap(message -> handleInbound(message, threadId, userId))
				.doFinally(ignored -> inboundDone.tryEmitEmpty());

		Flux<WebSocketMessage> outbound = Flux.merge(roomFrames, inboundResponses)
				.takeUntilOther(inboundDone.asMono())
				.map(frame -> session.textMessage(serialize(frame)));

		Mono<Void> messageLoop = session.send(outbound).then(session.close(CloseStatus.NORMAL));
		Mono<Void> tokenExpiry = tokenExpiresAt == null
				? Mono.never()
				: Mono.delay(durationUntil(tokenExpiresAt)).then(session.close(TOKEN_EXPIRED));
		Mono<Void> slowConsumer = outboundOverflow.asMono()
				.doOnSuccess(ignored -> log.warn(
						"Closing slow WebSocket consumer threadId={} connectionId={}", threadId, connectionId))
				.then(session.close(SLOW_CONSUMER));

		return Mono.firstWithSignal(messageLoop, tokenExpiry, slowConsumer)
				.doFinally(ignored -> roomSessionRegistry.leave(threadId, connectionId));
	}

	private Mono<WsFrame> handleInbound(WebSocketMessage message, UUID threadId, UUID userId) {
		String traceId = newTraceId();
		String payload = textPayload(message);
		if (payload == null) {
			return Mono.just(malformed(threadId, traceId));
		}

		InboundMessage inbound;
		try {
			inbound = objectMapper.readValue(payload, InboundMessage.class);
		} catch (Exception error) {
			log.debug("Malformed WebSocket frame threadId={} traceId={}", threadId, traceId, error);
			return Mono.just(malformed(threadId, traceId));
		}
		if (SERVER_ONLY_TYPES.contains(inbound.type())) {
			return Mono.empty();
		}
		if (!"chat.message".equals(inbound.type()) || inbound.content() == null || inbound.content().isBlank()) {
			return Mono.just(malformed(threadId, traceId));
		}

		ChatMessageCommand command = new ChatMessageCommand(threadId, userId, inbound.content(), traceId);
		try {
			roomSessionRegistry.broadcast(command.threadId(),
					new ChatMessageFrame(command.threadId(), command.from(), command.content()));
			return Mono.empty();
		} catch (RuntimeException error) {
			log.error("WebSocket room broadcast failed threadId={} traceId={}", threadId, traceId, error);
			return Mono.just(new ErrorFrame(threadId, "INTERNAL_ERROR",
					"메시지를 방송하지 못했습니다.", traceId));
		}
	}

	private Mono<Void> sendErrorAndClose(WebSocketSession session, UUID sessionId, String code,
			String message, String traceId) {
		ErrorFrame frame = new ErrorFrame(sessionId, code, message, traceId);
		return session.send(Mono.just(session.textMessage(serialize(frame))))
				.then(Mono.defer(() -> session.close(CloseStatus.NORMAL)))
				.doOnError(error -> log.debug("Failed to send WebSocket error frame traceId={}", traceId, error))
				.onErrorResume(ignored -> Mono.empty());
	}

	private ErrorFrame malformed(UUID threadId, String traceId) {
		return new ErrorFrame(threadId, "MALFORMED_REQUEST", "WebSocket 프레임 형식이 올바르지 않습니다.", traceId);
	}

	private String serialize(WsFrame frame) {
		try {
			return objectMapper.writeValueAsString(frame);
		} catch (Exception error) {
			throw new IllegalStateException("WebSocket frame serialization failed", error);
		}
	}

	static Flux<WsFrame> bufferForConnection(Flux<WsFrame> frames, Sinks.One<Void> outboundOverflow) {
		return frames.onBackpressureBuffer(CONNECTION_BUFFER_SIZE,
				ignored -> outboundOverflow.tryEmitEmpty(), BufferOverflowStrategy.DROP_LATEST);
	}

	static String textPayload(WebSocketMessage message) {
		return message.getType() == WebSocketMessage.Type.TEXT ? message.getPayloadAsText() : null;
	}

	private static UUID threadIdOf(WebSocketSession session) {
		String path = session.getHandshakeInfo().getUri().getPath();
		if (!path.startsWith(WS_PATH_PREFIX)) {
			return null;
		}
		String value = path.substring(WS_PATH_PREFIX.length());
		if (value.isEmpty() || value.contains("/")) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static Duration durationUntil(Instant instant) {
		Duration remaining = Duration.between(Instant.now(), instant);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	private static SessionInfo sessionInfoOf(Principal principal) {
		if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
			var jwt = jwtAuthentication.getToken();
			return new SessionInfo(jwt.getSubject(), jwt.getExpiresAt());
		}
		return new SessionInfo(principal.getName(), null);
	}

	private static String newTraceId() {
		return UUID.randomUUID().toString();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record InboundMessage(String type, String content) {
	}

	private record SessionInfo(String subject, Instant tokenExpiresAt) {
	}

	private static final class UserProvisioningFailure extends RuntimeException {

		UserProvisioningFailure(Throwable cause) {
			super(cause);
		}
	}

}
