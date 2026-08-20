package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ErrorFrame.java
 * Description : 스트림 중 발생한 오류를 알리는 프레임. HTTP 쪽 공통 에러 봉투(ErrorResponse)와
 *               같은 code/message/traceId를 재사용한다(02·EDGE의 UNAUTHENTICATED/TOKEN_EXPIRED/
 *               TOKEN_INVALID/FORBIDDEN/RATE_LIMITED). 연결 수립 자체가 실패하는 경우처럼
 *               특정 세션에 속하지 않는 오류라면 sessionId는 null일 수 있다. traceId는 연결
 *               단위가 아니라 메시지(턴) 단위로 발급한다 — 커넥션 하나가 여러 턴을 실어 나르는
 *               WS 특성상, HTTP처럼 요청 하나 단위의 추적력을 유지하려면 턴마다 새로 발급해야
 *               한다. 실제 발급 지점(어떤 프레임이 턴의 시작인지)은 WS 메시지 처리 파이프라인이
 *               생기는 시점(GitHub 이슈 #2 WS 연결·#13 협업 채팅방 코어)에 정해진다.
 */
public record ErrorFrame(UUID sessionId, String code, String message, String traceId) implements WsFrame {
}
