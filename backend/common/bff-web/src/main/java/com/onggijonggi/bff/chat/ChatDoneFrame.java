package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ChatDoneFrame.java
 * Description : 답변 스트리밍 종료를 알리는 프레임.
 */
public record ChatDoneFrame(UUID sessionId) implements WsFrame {
}
