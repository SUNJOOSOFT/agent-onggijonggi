package com.onggijonggi.api.chat;

import com.onggijonggi.api.auth.CurrentActor;
import com.onggijonggi.api.auth.CurrentActorProvider;
import com.onggijonggi.common.chat.domain.Thr;
import com.onggijonggi.common.chat.domain.ThrKind;
import com.onggijonggi.common.chat.domain.ThrMbr;
import com.onggijonggi.common.chat.domain.ThrMbrStatus;
import com.onggijonggi.common.chat.persistence.ThrMbrRepository;
import com.onggijonggi.common.chat.persistence.ThrRepository;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Class Name : CollabThreadController.java
 * Description : 협업 스레드 조회·참가자 관리 계약 구현체. 1:1 대화를 다루는 ChatController와 저장
 *               테이블도 소유 모델도 달라 컨트롤러를 나눈다.
 *
 *               방을 만드는 경로는 아직 여기 없다 — 부를 화면이 없어 이슈 #23과 함께 진행한다
 *               (이슈 #22 코멘트). 그래서 참가자 관리는 이미 있는 방 위에서만 동작한다.
 *
 *               초대·제거·위임은 대상을 Keycloak subject로 지목한다. 사용자 검색 API가 없어
 *               호출자가 이미 아는 식별자를 그대로 받는 것 말고는 성립하는 계약이 없다(이슈 #20).
 */
@RestController
public class CollabThreadController {

	private final CurrentActorProvider currentActorProvider;
	private final ThrRepository thrRepository;
	private final ThrMbrRepository thrMbrRepository;
	private final ThreadParticipantService threadParticipantService;

	public CollabThreadController(CurrentActorProvider currentActorProvider, ThrRepository thrRepository,
			ThrMbrRepository thrMbrRepository, ThreadParticipantService threadParticipantService) {
		this.currentActorProvider = currentActorProvider;
		this.thrRepository = thrRepository;
		this.thrMbrRepository = thrMbrRepository;
		this.threadParticipantService = threadParticipantService;
	}

	/** 어떤 방을 내려줄지 고르는 것은 서버 몫이라, 호출자가 참가자인 방만 나간다. */
	@GetMapping("/api/collab/threads")
	public Flux<CollabThreadSummary> listThreads() {
		return actorUserId()
				.flatMap(userId -> Mono.fromCallable(() -> joinedCollabThreads(userId))
						.subscribeOn(Schedulers.boundedElastic()))
				.flatMapMany(Flux::fromIterable);
	}

	/** 명단은 참가자면 누구나 본다 — 제거·위임 대상을 지목하려면 먼저 누가 있는지 알아야 한다. */
	@GetMapping("/api/collab/threads/{threadId}/participants")
	public Flux<ThreadParticipant> listParticipants(@PathVariable UUID threadId) {
		return actorUserId()
				.flatMap(userId -> threadParticipantService.list(threadId, userId))
				.flatMapMany(Flux::fromIterable);
	}

	/** 초대는 OWNER만 한다. 이미 참가 중인 사람을 다시 초대해도 성공으로 답한다(멱등). */
	@PostMapping("/api/collab/threads/{threadId}/participants")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> inviteParticipant(@PathVariable UUID threadId,
			@Valid @RequestBody ParticipantSubjectRequest request) {
		return actorUserId()
				.flatMap(userId -> threadParticipantService.invite(threadId, userId, request.subject()));
	}

	/**
	* 자진 탈퇴와 OWNER의 타인 제거를 한 경로로 받는다 — 같은 리소스(참가)를 끝내는 일이라
	* 나누지 않고, 누구를 지목했는지에 따라 서비스가 규칙을 가른다.
	*/
	@DeleteMapping("/api/collab/threads/{threadId}/participants/{subject}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> removeParticipant(@PathVariable UUID threadId, @PathVariable String subject) {
		return currentActorProvider.currentActor()
				.flatMap(actor -> threadParticipantService.remove(threadId, actor.userId(), actor.subject(), subject));
	}

	/** 스레드의 OWNER 자리를 교체한다. OWNER가 방을 떠나려면 이걸 먼저 해야 한다. */
	@PutMapping("/api/collab/threads/{threadId}/owner")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> transferOwner(@PathVariable UUID threadId,
			@Valid @RequestBody ParticipantSubjectRequest request) {
		return actorUserId()
				.flatMap(userId -> threadParticipantService.transferOwner(threadId, userId, request.subject()));
	}

	private Mono<UUID> actorUserId() {
		return currentActorProvider.currentActor().map(CurrentActor::userId);
	}

	/**
	* 참가 행을 먼저 읽고 그 id로 Thread를 가져온다. thr_mbr에 연관관계를 매핑하지 않아(ChatSess와
	* 같은 이유) 조인 대신 두 번 조회하지만, 두 번째는 findAllById 한 번이라 건수만큼 늘지 않는다.
	*
	* status로 Thread를 거르지 않는다 — ARCHIVED로 만드는 코드 경로가 아직 없어, 지금 거르면 아무
	* 행도 만들지 않는 조건을 미리 박아두는 셈이 된다.
	*/
	private List<CollabThreadSummary> joinedCollabThreads(UUID userId) {
		List<UUID> joinedIds = thrMbrRepository.findByUserIdAndStatus(userId, ThrMbrStatus.ACTIVE)
				.stream()
				.map(ThrMbr::getThrId)
				.toList();
		if (joinedIds.isEmpty()) {
			return List.of();
		}
		return thrRepository.findAllById(joinedIds).stream()
				.filter(thr -> thr.getKind() == ThrKind.COLLAB)
				.sorted(Comparator.comparing(Thr::getCreatedAt).reversed())
				.map(CollabThreadSummary::from)
				.toList();
	}

}
