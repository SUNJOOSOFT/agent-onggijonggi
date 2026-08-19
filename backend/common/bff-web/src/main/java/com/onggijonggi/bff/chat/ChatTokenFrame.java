package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ChatTokenFrame.java
 * Description : 스트리밍 중인 답변의 토큰 조각 프레임. 기존 raw text 청크 스트림을
 *               WS 프레임으로 옮긴 형태다.
 */
public record ChatTokenFrame(UUID sessionId, String delta) implements WsFrame {
}
