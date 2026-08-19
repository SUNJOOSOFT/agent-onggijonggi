package com.onggijonggi.bff.chat;

/**
 * Class Name : WsFrame.java
 * Description : WebSocket 메시지 프레임 봉투. GitHub 이슈 #8([협업채팅] 메시지 프레임 프로토콜)의
 *               03·CORE ↔ 01·CLIENT 계약 — type 태그로 채팅 토큰과 협업 이벤트를 한 커넥션에
 *               멀티플렉싱한다. 필드 구성은 프론트와 계약 확정 전이라 아직 잠정안이다.
 */
public sealed interface WsFrame
		permits ChatTokenFrame, ChatDoneFrame, PresenceJoinFrame, ChatMessageFrame {
}
