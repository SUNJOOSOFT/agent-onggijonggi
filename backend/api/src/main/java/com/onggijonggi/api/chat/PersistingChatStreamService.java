package com.onggijonggi.api.chat;

import com.onggijonggi.api.auth.CurrentActor;
import com.onggijonggi.api.auth.CurrentActorProvider;
import com.onggijonggi.common.chat.domain.ChatMsg;
import com.onggijonggi.common.chat.domain.ChatSess;
import com.onggijonggi.common.chat.persistence.ChatMsgRepository;
import com.onggijonggi.common.chat.persistence.ChatSessRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Class Name : PersistingChatStreamService.java
 * Description : ChatStreamService 데코레이터(03·CORE). LlmChatStreamService는 그대로 두고 그
 *               바깥에서 app_user JIT 프로비저닝 + 세션(chat_sess)/메시지(chat_msg) 저장을 얹는다.
 *               클라이언트가 매 요청마다 전체 이력을 보내므로 이번 요청의 마지막 user 메시지와, 이번
 *               스트림이 만든 assistant 응답만 저장한다(전량 재저장 금지). 저장 실패는 채팅 응답을
 *               막지 않는다(로그만 남기고 삼킨다) — JPA(블로킹) 호출은 전부 boundedElastic으로
 *               오프로딩한다.
 */
@Service
@Primary
public class PersistingChatStreamService implements ChatStreamService {

	private static final Logger log = LoggerFactory.getLogger(PersistingChatStreamService.class);

	private static final int TITLE_MAX_LENGTH = 50;

	private final LlmChatStreamService delegate;
	private final CurrentActorProvider currentActorProvider;
	private final ChatSessRepository chatSessRepository;
	private final ChatMsgRepository chatMsgRepository;

	public PersistingChatStreamService(
			LlmChatStreamService delegate,
			CurrentActorProvider currentActorProvider,
			ChatSessRepository chatSessRepository,
			ChatMsgRepository chatMsgRepository) {
		this.delegate = delegate;
		this.currentActorProvider = currentActorProvider;
		this.chatSessRepository = chatSessRepository;
		this.chatMsgRepository = chatMsgRepository;
	}

	@Override
	public Flux<String> streamChat(ChatStreamRequest request) {
		return currentActorProvider.currentActor()
				.map(CurrentActor::userId)
				.flatMap(userId -> persistUserTurn(request, userId))
				.onErrorResume(e -> {
					log.error("세션/메시지 저장 실패, 채팅은 계속 진행합니다. sessionId={}", request.sessionId(), e);
					return Mono.just(true);
				})
				.flatMapMany(mayPersist -> streamAndPersistReply(request, mayPersist));
	}

	/**
	* 이미 있는 세션인데 호출자 소유가 아니면 저장을 건너뛴다 — sessionId를 추측·재사용해 남의
	* 세션에 메시지가 끼어드는 것을 막기 위함.
	*/
	private Mono<Boolean> persistUserTurn(ChatStreamRequest request, UUID userId) {
		String latestUserContent = latestUserMessageContent(request);
		return Mono.fromCallable(() -> {
					if (!ownsOrCreatesSession(request.sessionId(), userId, latestUserContent)) {
						log.warn("세션 소유자가 아닌 사용자의 저장 요청을 건너뜁니다. sessionId={}", request.sessionId());
						return false;
					}
					chatMsgRepository.save(new ChatMsg(request.sessionId(), "user", latestUserContent));
					return true;
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	/**
	* 세션이 이미 있으면 title 갱신 등은 하지 않는다 — 메타데이터 수정은 전용 엔드포인트
	* (ChatController.renameSession, PATCH)가 맡는다.
	*/
	private boolean ownsOrCreatesSession(UUID sessionId, UUID userId, String latestUserContent) {
		return chatSessRepository.findById(sessionId)
				.map(sess -> sess.getUserId().equals(userId))
				.orElseGet(() -> {
					String title = latestUserContent.length() > TITLE_MAX_LENGTH
							? latestUserContent.substring(0, TITLE_MAX_LENGTH)
							: latestUserContent;
					chatSessRepository.save(new ChatSess(sessionId, userId, title));
					return true;
				});
	}

	private String latestUserMessageContent(ChatStreamRequest request) {
		List<ChatMessage> messages = request.messages();
		return messages.get(messages.size() - 1).content();
	}

	/**
	* mayPersist는 세션이 호출자 소유가 아니라서 명시적으로 건너뛴 경우에만 false. 프로비저닝 실패
	* 등 다른 예외 발생 시에는(streamChat의 onErrorResume) true로 넘어와 assistant 응답 저장을
	* 계속 시도한다.
	*/
	private Flux<String> streamAndPersistReply(ChatStreamRequest request, boolean mayPersist) {
		StringBuilder buffer = new StringBuilder();
		Flux<String> stream = delegate.streamChat(request).doOnNext(buffer::append);
		if (!mayPersist) {
			return stream;
		}
		return stream.doOnComplete(() -> persistAssistantReply(request.sessionId(), buffer.toString()));
	}

	/**
	* 스트림이 클라이언트에 이미 다 전달된 뒤 호출되므로 반환 Flux 체인 밖에서 fire-and-forget으로
	* 구독한다 — 여기서 에러가 나도 이미 끝난 채팅 응답에 영향을 줄 수 없고, onErrorComplete로
	* 삼켜서 구독 자체가 예외로 끊기지 않게 한다(로그만 남김).
	*/
	private void persistAssistantReply(UUID sessionId, String content) {
		Mono.fromRunnable(() -> chatMsgRepository.save(new ChatMsg(sessionId, "assistant", content)))
				.subscribeOn(Schedulers.boundedElastic())
				.doOnError(e -> log.error("assistant 응답 저장 실패. sessionId={}", sessionId, e))
				.onErrorComplete()
				.subscribe();
	}

}
