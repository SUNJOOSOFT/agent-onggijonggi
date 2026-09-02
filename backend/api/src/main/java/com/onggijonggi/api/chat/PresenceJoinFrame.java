package com.onggijonggi.api.chat;

import java.util.UUID;

/**
 * Class Name : PresenceJoinFrame.java
 * Description : 참여자가 방에 입장했음을 알리는 협업 이벤트 프레임.
 *               대칭 이벤트(퇴장)는 아직 없다 — 관련 논의는 GitHub 이슈 #25(Presence 방송
 *               join/leave)에서 진행 중이니 확정 여부는 그쪽을 확인한다.
 */
public record PresenceJoinFrame(UUID sessionId, UUID userId) implements WsFrame {
}
