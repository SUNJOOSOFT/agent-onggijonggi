package com.onggijonggi.api.chat;

import com.onggijonggi.api.auth.UserIdentityService;
import com.onggijonggi.common.chat.domain.Thr;
import com.onggijonggi.common.chat.domain.ThrMbr;
import com.onggijonggi.common.chat.domain.ThrMbrRole;
import com.onggijonggi.common.chat.persistence.ThrMbrRepository;
import com.onggijonggi.common.chat.persistence.ThrRepository;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Class Name : CollabRoomFixture.java
 * Description : WS 통합 테스트가 붙을 방을 실제 행으로 만들어 준다. 이슈 #22로 방 입장에 참가자
 *               검증이 붙어, 임의의 threadId로는 더 이상 연결이 서지 않는다.
 *
 *               첫 번째 subject가 방을 만든 사람(OWNER)이고 나머지는 MEMBER다. app_user는 WS
 *               핸들러와 같은 경로(UserIdentityService)로 미리 만들어 둔다 — 그래야 연결 시점에
 *               같은 id로 해석돼 참가자 조회가 맞는다.
 */
@TestConfiguration
public class CollabRoomFixture {

	@Bean
	CollabRooms collabRooms(ThrRepository thrRepository, ThrMbrRepository thrMbrRepository,
			UserIdentityService userIdentityService) {
		return new CollabRooms(thrRepository, thrMbrRepository, userIdentityService);
	}

	public static final class CollabRooms {

		private final ThrRepository thrRepository;
		private final ThrMbrRepository thrMbrRepository;
		private final UserIdentityService userIdentityService;

		CollabRooms(ThrRepository thrRepository, ThrMbrRepository thrMbrRepository,
				UserIdentityService userIdentityService) {
			this.thrRepository = thrRepository;
			this.thrMbrRepository = thrMbrRepository;
			this.userIdentityService = userIdentityService;
		}

		public UUID openRoom(String... subjects) {
			UUID ownerId = userIdentityService.resolveOrProvision(subjects[0]).block();
			Thr thr = thrRepository.save(Thr.collab(ownerId, "테스트 협업방"));
			thrMbrRepository.save(new ThrMbr(thr.getId(), ownerId, ThrMbrRole.OWNER, ownerId));
			for (int i = 1; i < subjects.length; i++) {
				UUID memberId = userIdentityService.resolveOrProvision(subjects[i]).block();
				thrMbrRepository.save(new ThrMbr(thr.getId(), memberId, ThrMbrRole.MEMBER, ownerId));
			}
			return thr.getId();
		}

	}

}
