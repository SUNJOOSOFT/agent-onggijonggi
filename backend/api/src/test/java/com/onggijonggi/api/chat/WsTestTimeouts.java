package com.onggijonggi.api.chat;

import java.time.Duration;

/**
 * Class Name : WsTestTimeouts.java
 * Description : WS 통합 테스트가 프레임을 기다리는 상한. 성능 단언이 아니라 테스트가 영원히 멈추지
 *               않게 하는 안전장치다 — block은 기대한 프레임이 도착하면 즉시 반환하므로 값을 넉넉히
 *               잡아도 정상 경로 비용은 없다. 값은 흩어져 있던 것과 같은 5초이고, 조정할 근거가
 *               생기면 여기서만 고치면 모든 호출부에 반영된다.
 */
final class WsTestTimeouts {

	/** WS 연결·프레임 왕복 대기 상한. 초과하면 TimeoutException으로 실패한다. */
	static final Duration BLOCK = Duration.ofSeconds(5);

	private WsTestTimeouts() {
	}

}
