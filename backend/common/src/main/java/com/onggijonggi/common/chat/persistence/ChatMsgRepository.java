package com.onggijonggi.common.chat.persistence;

import com.onggijonggi.common.chat.domain.ChatMsg;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class Name : ChatMsgRepository.java
 * Description : chat_msg JPA 레포지토리.
 */
public interface ChatMsgRepository extends JpaRepository<ChatMsg, UUID> {

	/** idx_chat_msg_sess_created 인덱스가 (sess_id, created_at)라 이 정렬 조회에 그대로 맞는다. */
	List<ChatMsg> findBySessIdOrderByCreatedAtAsc(UUID sessId);

}
