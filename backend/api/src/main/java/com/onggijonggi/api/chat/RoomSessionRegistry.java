package com.onggijonggi.api.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Class Name : RoomSessionRegistry.java
 * Description : 방 단위 연결과 프레임 방송을 in-memory로 관리한다. WebSocketSession은
 *               {@link CollabWebSocketHandler}가 소유하며, in-memory sink 기반이라 단일
 *               서버 프로세스만 지원한다. 다중 인스턴스는 pub/sub와 분산 순서 보장이 필요하다.
 *               입퇴장 방송(이슈 #25)도 여기서 낸다 — 누가 방에 있는지 아는 곳이 여기뿐이라,
 *               "이미 있던 사람에게만 알린다"는 판단을 멤버십 변경과 같은 잠금 안에서 해야 한다.
 */
@Component
public class RoomSessionRegistry {

	private static final int WARMUP_BUFFER_SIZE = 1;

	private final ConcurrentMap<UUID, RoomState> rooms = new ConcurrentHashMap<>();

	/**
	 * 연결을 방에 등록하고 그 방의 프레임 스트림을 돌려준다. 이미 있던 참여자가 하나라도 있으면
	 * 그들에게 입장을 알린다 — 첫 연결이면 알릴 상대가 없어 아무것도 내지 않는다. 이 구분은
	 * 취향이 아니라 필요다: sink의 warm-up 버퍼는 한 칸뿐이라, 빈 방에 프레임을 밀어 넣으면
	 * 그 자리가 채워져 뒤이어 오는 첫 메시지가 밀려난다(이슈 #102).
	 */
	public Flux<WsFrame> join(UUID threadId, UUID connectionId, UUID userId) {
		RoomState room = rooms.compute(threadId, (ignored, current) -> {
			RoomState joined = current == null ? new RoomState() : current;
			if (joined.add(connectionId)) {
				joined.emitPresence(new PresenceJoinFrame(threadId, userId));
			}
			return joined;
		});
		return room.frames();
	}

	public void broadcast(UUID threadId, WsFrame frame) {
		RoomState room = rooms.get(threadId);
		if (room == null) {
			throw new IllegalStateException("room is not registered: " + threadId);
		}
		room.emit(frame);
	}

	/**
	 * 연결을 방에서 빼고, 남은 참여자가 있으면 그들에게 퇴장을 알린다. 마지막 퇴장이면 방이
	 * 사라지므로 알리지 않는다 — 받을 사람도 없고, 사라진 방에 broadcast하면 예외가 된다.
	 */
	public void leave(UUID threadId, UUID connectionId, UUID userId) {
		rooms.computeIfPresent(threadId, (ignored, current) -> {
			if (!current.removeAndCompleteIfEmpty(connectionId)) {
				current.emitPresence(new PresenceLeaveFrame(threadId, userId));
				return current;
			}
			return null;
		});
	}

	private static final class RoomState {

		private final Set<UUID> connections = new HashSet<>();

		private final Sinks.Many<WsFrame> frames = Sinks.many()
				.multicast()
				.onBackpressureBuffer(WARMUP_BUFFER_SIZE, false);

		/** @return 이 연결이 들어오기 전에 이미 다른 연결이 있었으면 true */
		synchronized boolean add(UUID connectionId) {
			boolean hadOthers = !connections.isEmpty();
			connections.add(connectionId);
			return hadOthers;
		}

		synchronized boolean removeAndCompleteIfEmpty(UUID connectionId) {
			connections.remove(connectionId);
			if (!connections.isEmpty()) {
				return false;
			}
			frames.tryEmitComplete();
			return true;
		}

		Flux<WsFrame> frames() {
			return frames.asFlux();
		}

		/**
		 * 입퇴장 통보 전용 emit. 지금 듣고 있는 구독자가 없으면 보내지 않는다 — presence는 그 순간
		 * 방에 붙어 있는 사람에게만 의미가 있고, 무엇보다 구독자가 없을 때 밀어 넣으면 warm-up
		 * 버퍼 한 칸을 차지해 뒤따라오는 첫 메시지를 밀어낸다(이슈 #102). 실패해도 던지지 않는다.
		 * 이 통보는 부가 정보이고, 특히 퇴장 경로에서 예외가 나면 세션 종료가 망가진다.
		 */
		synchronized void emitPresence(WsFrame frame) {
			if (frames.currentSubscriberCount() == 0) {
				return;
			}
			frames.tryEmitNext(frame);
		}

		synchronized void emit(WsFrame frame) {
			Sinks.EmitResult result = frames.tryEmitNext(frame);
			if (result.isFailure()) {
				throw new IllegalStateException("room frame emission failed: " + result);
			}
		}
	}

}
