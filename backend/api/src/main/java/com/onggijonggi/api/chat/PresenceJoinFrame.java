package com.onggijonggi.api.chat;

import java.util.UUID;

/**
 * Class Name : PresenceJoinFrame.java
 * Description : 참여자가 방에 입장했음을 알리는 협업 이벤트 프레임(이슈 #25). 대칭 이벤트는
 *               {@link PresenceLeaveFrame}이다. 입장 본인에게는 보내지 않는다 — 이미 방에 있던
 *               참여자에게만 나가므로, 방의 첫 입장자는 이 프레임을 발생시키지 않는다.
 */
public record PresenceJoinFrame(UUID sessionId, UUID userId) implements WsFrame {
}
