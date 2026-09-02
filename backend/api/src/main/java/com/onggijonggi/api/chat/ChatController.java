package com.onggijonggi.api.chat;

import com.onggijonggi.common.auth.CurrentActor;
import com.onggijonggi.common.auth.CurrentActorProvider;
import com.onggijonggi.common.chat.domain.ChatSess;
import com.onggijonggi.common.chat.persistence.ChatMsgRepository;
import com.onggijonggi.common.chat.persistence.ChatSessRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Class Name : ChatController.java
 * Description : 01·CLIENT ↔ 03·CORE 채팅 스트리밍·이력 조회 계약 구현체.
 *               02·EDGE(SecurityConfig)가 JWT 인증·hasRole("USER")를 필터체인에서 이미 강제한다.
 */
@RestController
public class ChatController {

	private final ChatStreamService chatStreamService;

	private final ChatSessRepository chatSessRepository;
	private final ChatMsgRepository chatMsgRepository;
	private final CurrentActorProvider currentActorProvider;

	public ChatController(ChatStreamService chatStreamService, ChatSessRepository chatSessRepository,
			ChatMsgRepository chatMsgRepository, CurrentActorProvider currentActorProvider) {
		this.chatStreamService = chatStreamService;
		this.chatSessRepository = chatSessRepository;
		this.chatMsgRepository = chatMsgRepository;
		this.currentActorProvider = currentActorProvider;
	}

	/**
	* Content-Type을 text/event-stream이 아닌 text/plain으로 고정한다. text/event-stream을 쓰면
	* Spring이 Flux<String>을 "data:<chunk>\n\n" SSE 프레이밍으로 자동으로 감싸는데, 클라이언트
	* useChat의 streamProtocol:'text'는 이 프레이밍을 파싱하지 않아 화면에 접두사가 그대로 노출된다.
	*/
	@PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
	public Flux<String> streamChat(@Valid @RequestBody ChatStreamRequest request) {
		return chatStreamService.streamChat(request);
	}

	@GetMapping("/api/chat/sessions")
	public Flux<ChatSessSummary> listSessions() {
		return currentUserId()
				.flatMap(userId -> Mono
						.fromCallable(() -> chatSessRepository.findByUserIdOrderByCreatedAtDesc(userId))
						.subscribeOn(Schedulers.boundedElastic()))
				.flatMapMany(Flux::fromIterable)
				.map(ChatSessSummary::from);
	}

	/** 본인 소유가 아니거나 존재하지 않는 세션이면 404 — 타인 세션의 존재 여부를 노출하지 않는다. */
	@GetMapping("/api/chat/sessions/{sessionId}/messages")
	public Flux<ChatMsgItem> listMessages(@PathVariable UUID sessionId) {
		return currentUserId()
				.flatMap(userId -> findOwnedSessionOrNotFound(sessionId, userId))
				.flatMap(sess -> Mono
						.fromCallable(() -> chatMsgRepository.findBySessIdOrderByCreatedAtAsc(sessionId))
						.subscribeOn(Schedulers.boundedElastic()))
				.flatMapMany(Flux::fromIterable)
				.map(ChatMsgItem::from);
	}

	/** chat_msg는 FK on delete cascade로 DB가 함께 지운다(V2__chat_and_audit.sql). */
	@DeleteMapping("/api/chat/sessions/{sessionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> deleteSession(@PathVariable UUID sessionId) {
		return currentUserId()
				.flatMap(userId -> findOwnedSessionOrNotFound(sessionId, userId))
				.flatMap(sess -> Mono
						.fromRunnable(() -> chatSessRepository.delete(sess))
						.subscribeOn(Schedulers.boundedElastic()))
				.then();
	}

	@PatchMapping("/api/chat/sessions/{sessionId}")
	public Mono<ChatSessSummary> renameSession(@PathVariable UUID sessionId,
			@Valid @RequestBody RenameSessionRequest request) {
		return currentUserId()
				.flatMap(userId -> findOwnedSessionOrNotFound(sessionId, userId))
				.flatMap(sess -> Mono.fromCallable(() -> {
							sess.setTitle(request.title());
							return chatSessRepository.save(sess);
						})
						.subscribeOn(Schedulers.boundedElastic()))
				.map(ChatSessSummary::from);
	}

	/** listMessages와 동일한 소유권 검증 패턴을 delete/rename에서도 재사용한다. */
	private Mono<ChatSess> findOwnedSessionOrNotFound(UUID sessionId, UUID userId) {
		return Mono.fromCallable(() -> chatSessRepository.findByIdAndUserId(sessionId, userId))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(Mono::justOrEmpty)
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	/** 소유권 검사에 쓰는 것은 app_user.id뿐이라 CurrentActor에서 그 값만 꺼낸다. */
	private Mono<UUID> currentUserId() {
		return currentActorProvider.currentActor().map(CurrentActor::userId);
	}

}
