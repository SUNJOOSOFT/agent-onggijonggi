package com.onggijonggi.api.chat;

import com.onggijonggi.common.chat.domain.ThrMbrStatus;
import com.onggijonggi.common.chat.persistence.ThrMbrRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Class Name : ThreadMembershipService.java
 * Description : Thread 참가 여부 조회. 테이블 매핑은 04·DATA 계층에 있고 이 판정만 03·CORE에
 *               둔다 — 그 경계는 이슈 #22를 확인한다. JPA는 블로킹이라 boundedElastic으로
 *               오프로딩한다.
 */
@Service
public class ThreadMembershipService {

	private final ThrMbrRepository thrMbrRepository;

	public ThreadMembershipService(ThrMbrRepository thrMbrRepository) {
		this.thrMbrRepository = thrMbrRepository;
	}

	/** 끝난 참가 행이 같은 (thr_id, user_id)로 남아 있어서, 활성 행만 참가로 센다. */
	public Mono<Boolean> isActiveParticipant(UUID threadId, UUID userId) {
		return Mono.fromCallable(() ->
						thrMbrRepository.existsByThrIdAndUserIdAndStatus(threadId, userId, ThrMbrStatus.ACTIVE))
				.subscribeOn(Schedulers.boundedElastic());
	}

}
