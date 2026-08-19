package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ChatMessageFrame.java
 * Description : 참여자 간 일반 대화 메시지 프레임. 명시적 AI 호출 멘션이 없는 한 LLM
 *               파이프라인을 타지 않고 단순 브로드캐스트만 된다(이슈 3 결정과 연동).
 */
public record ChatMessageFrame(UUID sessionId, UUID from, String content) implements WsFrame {
}
