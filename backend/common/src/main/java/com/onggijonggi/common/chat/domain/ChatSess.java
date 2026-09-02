package com.onggijonggi.common.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Class Name : ChatSess.java
 * Description : 세션(V2__chat_and_audit.sql `chat_sess`). id는 클라이언트가 생성해 보내는 sessionId를
 *               그대로 쓴다. user_id는 app_user FK지만 패키지 간 결합을 최소화하기 위해
 *               AppUser와 연관관계 매핑 없이 평문 UUID 컬럼으로 둔다.
 */
@Entity
@Table(name = "chat_sess")
public class ChatSess {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/** 세션 최초 저장 시 마지막 user 메시지 앞 50자로 채운다(PersistingChatStreamService). */
	@Column(nullable = false)
	private String title;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ChatSess() {
	}

	public ChatSess(UUID id, UUID userId, String title) {
		this.id = id;
		this.userId = userId;
		this.title = title;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTitle() {
		return title;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setTitle(String title) {
		this.title = title;
		this.updatedAt = Instant.now();
	}

}
