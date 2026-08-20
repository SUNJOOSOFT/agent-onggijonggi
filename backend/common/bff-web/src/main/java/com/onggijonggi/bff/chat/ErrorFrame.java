package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ErrorFrame.java
 * Description : 스트림 중 발생한 오류를 알리는 프레임. HTTP 쪽 공통 에러 봉투(ErrorResponse)와
 *               같은 code/message/traceId를 재사용한다(02·EDGE의 UNAUTHENTICATED/TOKEN_EXPIRED/
 *               TOKEN_INVALID/FORBIDDEN/RATE_LIMITED). 연결 수립 자체가 실패하는 경우처럼
 *               특정 세션에 속하지 않는 오류라면 sessionId는 null일 수 있다.
 */
public record ErrorFrame(UUID sessionId, String code, String message, String traceId) implements WsFrame {
}
