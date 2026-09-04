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
 *               확인한다.
 */
class RoomSessionRegistryTest {

	private final RoomSessionRegistry registry = new RoomSessionRegistry();

	@Test
	void broadcastsToSenderAndPeersButNotOtherRooms() {
		UUID roomId = UUID.randomUUID();
		UUID otherRoomId = UUID.randomUUID();
		UUID sender = UUID.randomUUID();
		List<WsFrame> first = new CopyOnWriteArrayList<>();
		List<WsFrame> second = new CopyOnWriteArrayList<>();
		List<WsFrame> other = new CopyOnWriteArrayList<>();

		Disposable firstSubscription = registry.join(roomId, UUID.randomUUID()).subscribe(first::add);
		Disposable secondSubscription = registry.join(roomId, UUID.randomUUID()).subscribe(second::add);
		Disposable otherSubscription = registry.join(otherRoomId, UUID.randomUUID()).subscribe(other::add);

		ChatMessageFrame expected = new ChatMessageFrame(roomId, sender, "hello");
		registry.broadcast(roomId, expected);
		assertThat(first).containsExactly(expected);
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
		Disposable firstSubscription = registry.join(roomId, UUID.randomUUID()).subscribe(first::add);
		Disposable secondSubscription = registry.join(roomId, UUID.randomUUID()).subscribe(second::add);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> left = executor.submit(() -> broadcastRange(roomId, "left", start));
			Future<?> right = executor.submit(() -> broadcastRange(roomId, "right", start));
			start.countDown();
			left.get(5, TimeUnit.SECONDS);
			right.get(5, TimeUnit.SECONDS);

			assertThat(first).hasSize(200);
			assertThat(second).containsExactlyElementsOf(first);
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
				registry.join(roomId, slowConnectionId), slowOverflow).subscribe(slowSubscriber);
		Disposable fastSubscription = CollabWebSocketHandler.bufferForConnection(
				registry.join(roomId, fastConnectionId), fastOverflow).subscribe(fastFrames::add);

		for (int i = 0; i < 257; i++) {
			registry.broadcast(roomId, new ChatMessageFrame(roomId, UUID.randomUUID(), "message-" + i));
		}

		assertThat(slowOverflowed).isTrue();
		assertThat(fastOverflowed).isFalse();
		assertThat(fastFrames).hasSize(257);

		slowSubscriber.cancel();
		fastSubscription.dispose();
		registry.leave(roomId, slowConnectionId);
		registry.leave(roomId, fastConnectionId);
	}

	@Test
	void aNewRoomStateSurvivesAfterThePreviousLastMemberLeaves() {
		UUID roomId = UUID.randomUUID();
		UUID oldConnectionId = UUID.randomUUID();
		Disposable oldSubscription = registry.join(roomId, oldConnectionId).subscribe();

		registry.leave(roomId, oldConnectionId);
		oldSubscription.dispose();

		UUID newConnectionId = UUID.randomUUID();
		List<WsFrame> received = new CopyOnWriteArrayList<>();
		Disposable newSubscription = registry.join(roomId, newConnectionId).subscribe(received::add);
		registry.broadcast(roomId, new ChatMessageFrame(roomId, UUID.randomUUID(), "new room"));

		assertThat(received).hasSize(1);

		newSubscription.dispose();
		registry.leave(roomId, newConnectionId);
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
