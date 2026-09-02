package com.onggijonggi.api.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * Class Name : ChatMessage.java
 * Description : 대화 메시지 최소 스키마. AI SDK UI 메시지의 id/parts/createdAt 등 잉여 필드는
 *               클라이언트가 전송 전 트리밍하므로 서버는 role/content만 받는다.
 * @param role 메시지 역할(user|assistant|system)
 * @param content 메시지 본문
 */
public record ChatMessage(
		@NotBlank String role,
		@NotBlank String content
) {
}
