package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : PresenceJoinFrame.java
 * Description : 참여자가 방에 입장했음을 알리는 협업 이벤트 프레임.
 *               대칭 이벤트(퇴장)는 아직 없다 — 이슈 5(접속 상태/Presence)와 경계 확인 필요.
 */
public record PresenceJoinFrame(UUID sessionId, UUID userId) implements WsFrame {
}
