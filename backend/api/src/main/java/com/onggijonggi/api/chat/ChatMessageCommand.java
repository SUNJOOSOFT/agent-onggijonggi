package com.onggijonggi.api.chat;

import java.util.UUID;

/**
 * Class Name : ChatMessageCommand.java
 * Description : 인증된 클라이언트의 채팅 발화를 내부 처리용으로 표현하며, traceId는 개별 메시지
 *               처리 시도를 식별한다.
 */
record ChatMessageCommand(UUID threadId, UUID from, String content, String traceId) {
}
