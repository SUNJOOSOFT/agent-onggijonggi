package com.onggijonggi.api.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Class Name : RenameSessionRequest.java
 * Description : PATCH /api/chat/sessions/{sessionId} 요청 바디. max 길이는 chat_sess.title의
 *               DB 제약(varchar(255))과 맞춘다.
 * @param title 사용자가 지정할 새 세션 제목
 */
public record RenameSessionRequest(
		@NotBlank @Size(max = 255) String title
) {
}
