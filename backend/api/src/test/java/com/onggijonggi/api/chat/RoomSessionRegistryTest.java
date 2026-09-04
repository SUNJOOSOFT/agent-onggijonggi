package com.onggijonggi.api.chat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class Name : RoomSessionRegistryTest.java
 * Description : 방 단위 in-memory 방송(`RoomSessionRegistry`, 이슈 #16)을 검증한다. 같은 방
 *               구독자에게만(다른 방은 제외) 방송되는지, 동시 방송이 모든 구독자에게 같은
 *               순서로 도달하는지, 느린 연결의 outbound 버퍼가 넘쳐도 그 연결만 신호를 받고
 *               다른 연결은 영향받지 않는지, 마지막 멤버가 나간 뒤 새 방 상태가 정상 동작하는지
 *               확인한다. 입퇴장 통보(이슈 #25)는 연결이 아니라 사용자 단위라, 한 사람이 탭을
 *               여럿 열었을 때 통보가 새지 않는지도 함께 본다.
 */
class RoomSessionRegistryTest {

	private final RoomSessionRegistry registry = new RoomSessionRegistry();

	@Test
	void broadcastsToSenderAndPeersButNotOtherRooms() {
		UUID roomId = UUID.randomUUID();
		UUID otherRoomId = UUID.randomUUID();
		UUID sender = UUID.randomUUID();
		UUID secondUserId = UUID.randomUUID();
		List<WsFrame> first = new CopyOnWriteArrayList<>();
		List<WsFrame> second = new CopyOnWriteArrayList<>();
		List<WsFrame> other = new CopyOnWriteArrayList<>();

		Disposable firstSubscription = registry.join(roomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(first::add);
		Disposable secondSubscription = registry.join(roomId, UUID.randomUUID(), secondUserId)
				.subscribe(second::add);
		Disposable otherSubscription = registry.join(otherRoomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(other::add);

		ChatMessageFrame expected = new ChatMessageFrame(roomId, sender, "hello");
		registry.broadcast(roomId, expected);
		// 먼저 들어와 있던 first만 두 번째 입장을 통보받는다 — second는 자기 입장을 받지 않는다.
		assertThat(first).containsExactly(new PresenceJoinFrame(roomId, secondUserId), expected);
		assertThat(second).containsExactly(expected);
		assertThat(other).isEmpty();

		firstSubscription.dispose();
		secondSubscription.dispose();
		otherSubscription.dispose();
	}

	@Test
	void concurrentBroadcastsHaveTheSameOrderForEverySubscriber() throws Exception {
		UUID roomId = UUID.randomUUID();
		List<WsFrame> first = new CopyOnWriteArrayList<>();
		List<WsFrame> second = new CopyOnWriteArrayList<>();
		Disposable firstSubscription = registry.join(roomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(first::add);
		Disposable secondSubscription = registry.join(roomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(second::add);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> left = executor.submit(() -> broadcastRange(roomId, "left", start));
			Future<?> right = executor.submit(() -> broadcastRange(roomId, "right", start));
			start.countDown();
			left.get(5, TimeUnit.SECONDS);
			right.get(5, TimeUnit.SECONDS);

			// first는 second의 입장 통보를 하나 더 갖고 있다 — 순서 비교 대상은 메시지뿐이다.
			List<WsFrame> firstMessages = first.stream().filter(ChatMessageFrame.class::isInstance).toList();
			assertThat(firstMessages).hasSize(200);
			assertThat(second).containsExactlyElementsOf(firstMessages);
		} finally {
			executor.shutdownNow();
			firstSubscription.dispose();
			secondSubscription.dispose();
		}
	}

	@Test
	void overflowingConnectionBufferSignalsOnlyThatSubscriber() {
		UUID roomId = UUID.randomUUID();
		UUID slowConnectionId = UUID.randomUUID();
		UUID fastConnectionId = UUID.randomUUID();
		Sinks.One<Void> slowOverflow = Sinks.one();
		Sinks.One<Void> fastOverflow = Sinks.one();
		AtomicBoolean slowOverflowed = new AtomicBoolean();
		AtomicBoolean fastOverflowed = new AtomicBoolean();
		List<WsFrame> fastFrames = new CopyOnWriteArrayList<>();

		slowOverflow.asMono().doOnSuccess(ignored -> slowOverflowed.set(true)).subscribe();
		fastOverflow.asMono().doOnSuccess(ignored -> fastOverflowed.set(true)).subscribe();

		BaseSubscriber<WsFrame> slowSubscriber = new BaseSubscriber<>() {
			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				// 연결별 버퍼를 채우기 위해 의도적으로 demand를 요청하지 않는다.
			}
		};
		CollabWebSocketHandler.bufferForConnection(
				registry.join(roomId, slowConnectionId, UUID.randomUUID()), slowOverflow).subscribe(slowSubscriber);
		Disposable fastSubscription = CollabWebSocketHandler.bufferForConnection(
				registry.join(roomId, fastConnectionId, UUID.randomUUID()), fastOverflow)
				.subscribe(fastFrames::add);

		for (int i = 0; i < 257; i++) {
			registry.broadcast(roomId, new ChatMessageFrame(roomId, UUID.randomUUID(), "message-" + i));
		}

		assertThat(slowOverflowed).isTrue();
		assertThat(fastOverflowed).isFalse();
		assertThat(fastFrames).hasSize(257);

		slowSubscriber.cancel();
		fastSubscription.dispose();
		registry.leave(roomId, slowConnectionId, UUID.randomUUID());
		registry.leave(roomId, fastConnectionId, UUID.randomUUID());
	}

	@Test
	void aNewRoomStateSurvivesAfterThePreviousLastMemberLeaves() {
		UUID roomId = UUID.randomUUID();
		UUID oldConnectionId = UUID.randomUUID();
		Disposable oldSubscription = registry.join(roomId, oldConnectionId, UUID.randomUUID()).subscribe();

		registry.leave(roomId, oldConnectionId, UUID.randomUUID());
		oldSubscription.dispose();

		UUID newConnectionId = UUID.randomUUID();
		List<WsFrame> received = new CopyOnWriteArrayList<>();
		Disposable newSubscription = registry.join(roomId, newConnectionId, UUID.randomUUID())
				.subscribe(received::add);
		registry.broadcast(roomId, new ChatMessageFrame(roomId, UUID.randomUUID(), "new room"));

		assertThat(received).hasSize(1);

		newSubscription.dispose();
		registry.leave(roomId, newConnectionId, UUID.randomUUID());
	}

	@Test
	void announcesJoinToExistingMembersOnlyAndSkipsTheFirstConnection() {
		UUID roomId = UUID.randomUUID();
		UUID firstUserId = UUID.randomUUID();
		UUID secondUserId = UUID.randomUUID();
		List<WsFrame> first = new CopyOnWriteArrayList<>();
		List<WsFrame> second = new CopyOnWriteArrayList<>();

		Disposable firstSubscription = registry.join(roomId, UUID.randomUUID(), firstUserId)
				.subscribe(first::add);
		// 첫 입장은 알릴 상대가 없어 아무것도 내지 않는다 — 빈 방의 warm-up 버퍼를 쓰지 않는다.
		assertThat(first).isEmpty();

		UUID secondConnectionId = UUID.randomUUID();
		Disposable secondSubscription = registry.join(roomId, secondConnectionId, secondUserId)
				.subscribe(second::add);

		assertThat(first).containsExactly(new PresenceJoinFrame(roomId, secondUserId));
		assertThat(second).isEmpty();

		firstSubscription.dispose();
		secondSubscription.dispose();
		registry.leave(roomId, secondConnectionId, secondUserId);
	}

	@Test
	void announcesLeaveToTheRemainingMembers() {
		UUID roomId = UUID.randomUUID();
		UUID stayingConnectionId = UUID.randomUUID();
		UUID leavingConnectionId = UUID.randomUUID();
		UUID leavingUserId = UUID.randomUUID();
		List<WsFrame> staying = new CopyOnWriteArrayList<>();

		Disposable stayingSubscription = registry.join(roomId, stayingConnectionId, UUID.randomUUID())
				.subscribe(staying::add);
		registry.join(roomId, leavingConnectionId, leavingUserId).subscribe();
		staying.clear();

		registry.leave(roomId, leavingConnectionId, leavingUserId);

		assertThat(staying).containsExactly(new PresenceLeaveFrame(roomId, leavingUserId));

		stayingSubscription.dispose();
	}

	@Test
	void doesNotAnnounceLeaveWhenTheLastMemberLeaves() {
		UUID roomId = UUID.randomUUID();
		UUID onlyConnectionId = UUID.randomUUID();
		UUID onlyUserId = UUID.randomUUID();
		List<WsFrame> received = new CopyOnWriteArrayList<>();

		Disposable subscription = registry.join(roomId, onlyConnectionId, onlyUserId).subscribe(received::add);

		// 마지막 퇴장이면 방이 사라진다. 사라진 방에 방송하면 예외이므로 아무것도 내지 않아야 한다.
		registry.leave(roomId, onlyConnectionId, onlyUserId);

		assertThat(received).isEmpty();
		subscription.dispose();
	}

	@Test
	void doesNotAnnounceJoinForTheSameUsersSecondConnection() {
		UUID roomId = UUID.randomUUID();
		UUID twoTabUserId = UUID.randomUUID();
		UUID firstTabId = UUID.randomUUID();
		List<WsFrame> watcher = new CopyOnWriteArrayList<>();

		Disposable watcherSubscription = registry.join(roomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(watcher::add);
		Disposable firstTab = registry.join(roomId, firstTabId, twoTabUserId).subscribe();
		watcher.clear();

		Disposable secondTab = registry.join(roomId, UUID.randomUUID(), twoTabUserId).subscribe();

		// 이미 방에 있는 사람이 탭을 하나 더 연 것은 입장이 아니다.
		assertThat(watcher).isEmpty();

		watcherSubscription.dispose();
		firstTab.dispose();
		secondTab.dispose();
	}

	@Test
	void announcesLeaveOnlyWhenTheSameUsersLastConnectionGoes() {
		UUID roomId = UUID.randomUUID();
		UUID twoTabUserId = UUID.randomUUID();
		UUID firstTabId = UUID.randomUUID();
		UUID secondTabId = UUID.randomUUID();
		List<WsFrame> watcher = new CopyOnWriteArrayList<>();

		Disposable watcherSubscription = registry.join(roomId, UUID.randomUUID(), UUID.randomUUID())
				.subscribe(watcher::add);
		Disposable firstTab = registry.join(roomId, firstTabId, twoTabUserId).subscribe();
		Disposable secondTab = registry.join(roomId, secondTabId, twoTabUserId).subscribe();
		watcher.clear();

		registry.leave(roomId, secondTabId, twoTabUserId);

		// 탭 하나가 닫혀도 다른 탭이 남아 있으면 그 사람은 아직 방에 있다. 재연결도 같은 모양이다 —
		// 새 연결이 먼저 등록되고 죽은 옛 연결의 doFinally가 뒤늦게 도는 순서라, 여기서 통보가
		// 나가면 남은 사람 목록에서 그 사람이 사라진 채로 남는다.
		assertThat(watcher).isEmpty();

		registry.leave(roomId, firstTabId, twoTabUserId);

		assertThat(watcher).containsExactly(new PresenceLeaveFrame(roomId, twoTabUserId));

		watcherSubscription.dispose();
		firstTab.dispose();
		secondTab.dispose();
	}

	private void broadcastRange(UUID roomId, String prefix, CountDownLatch start) {
		try {
			start.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(interrupted);
		}
		for (int i = 0; i < 100; i++) {
			registry.broadcast(roomId, new ChatMessageFrame(roomId, UUID.randomUUID(), prefix + i));
		}
	}

}
