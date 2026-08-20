package com.onggijonggi.bff.chat;

import java.util.UUID;

/**
 * Class Name : ChatMessageFrame.java
 * Description : 참여자 간 일반 대화 메시지 프레임. AI 호출 여부에 따른 라우팅 정책(단순
 *               브로드캐스트 vs LLM 파이프라인 호출)은 GitHub 이슈 #13(협업 채팅방 코어)에서
 *               결정 중이니 확정 여부는 그쪽을 확인한다.
 */
public record ChatMessageFrame(UUID sessionId, UUID from, String content) implements WsFrame {
}
