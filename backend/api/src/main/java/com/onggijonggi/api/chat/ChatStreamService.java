package com.onggijonggi.api.chat;

import reactor.core.publisher.Flux;

/**
 * Class Name : ChatStreamService.java
 * Description : 채팅 스트리밍 응답 생성을 추상화하는 인터페이스. ChatController는 이 인터페이스에만
 *               의존하고, 실제 LLM 연동 구현체는 데코레이터(PersistingChatStreamService)로 감싼다.
 */
public interface ChatStreamService {

	Flux<String> streamChat(ChatStreamRequest request);

}
