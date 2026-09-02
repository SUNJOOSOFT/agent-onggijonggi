package com.onggijonggi.common.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Class Name : ChatMsg.java
 * Description : 메시지(V2__chat_and_audit.sql `chat_msg`). role은 클라이언트 계약(ChatMessage.role)과
 *               동일한 소문자 값을 그대로 저장한다. src_json(근거 스냅샷)은 RAG Advisor Chain이
 *               생기기 전까지 쓰지 않아 필드를 두지 않는다.
 */
@Entity
@Table(name = "chat_msg")
public class ChatMsg {

	@Id
	private UUID id;

	@Column(name = "sess_id", nullable = false)
	private UUID sessId;

	@Column(nullable = false)
	private String role;

	@Column(nullable = false)
	private String content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ChatMsg() {
	}

	public ChatMsg(UUID sessId, String role, String content) {
		this.id = UUID.randomUUID();
		this.sessId = sessId;
		this.role = role;
		this.content = content;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getSessId() {
		return sessId;
	}

	public String getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
