package com.onggijonggi.common.chat.persistence;

import com.onggijonggi.common.chat.domain.ThrMbr;
import com.onggijonggi.common.chat.domain.ThrMbrStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class Name : ThrMbrRepository.java
 * Description : thr_mbr JPA 레포지토리. 조회는 전부 status를 조건에 넣는다 — 끝난 참가 행이 같은
 *               (thr_id, user_id)로 함께 남아 있어서, 빼면 나간 사람도 참가자로 잡힌다.
 */
public interface ThrMbrRepository extends JpaRepository<ThrMbr, UUID> {

	/** 방 입장 인가에 쓴다. 활성 참가자 부분 유니크 인덱스가 있어 조건에 맞는 행은 최대 하나다. */
	boolean existsByThrIdAndUserIdAndStatus(UUID thrId, UUID userId, ThrMbrStatus status);

	List<ThrMbr> findByUserIdAndStatus(UUID userId, ThrMbrStatus status);

}
