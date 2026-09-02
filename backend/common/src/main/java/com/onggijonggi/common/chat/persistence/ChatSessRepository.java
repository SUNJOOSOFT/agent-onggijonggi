package com.onggijonggi.common.chat.persistence;

import com.onggijonggi.common.chat.domain.ChatSess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class Name : ChatSessRepository.java
 * Description : chat_sess JPA 레포지토리.
 */
public interface ChatSessRepository extends JpaRepository<ChatSess, UUID> {

	/**
	* updated_at이 아니라 created_at으로 정렬한다 — 세션이 이미 있으면 손대지 않아 updated_at이 최초
	* 생성 후 안 바뀌므로, "최근 활동순"을 기대하게 되는 updated_at 정렬은 오해를 부른다.
	*/
	List<ChatSess> findByUserIdOrderByCreatedAtDesc(UUID userId);

	/** userId가 다르면 빈 Optional을 반환해 타인 세션 접근을 막는다. */
	Optional<ChatSess> findByIdAndUserId(UUID id, UUID userId);

}
