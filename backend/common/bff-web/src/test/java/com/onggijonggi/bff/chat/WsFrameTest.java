package com.onggijonggi.bff.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Class Name : WsFrameTest.java
 * Description : WsFrame의 type 태그 다형성 직렬화가 초안 스펙과 맞는지 순수 단위 테스트로
 *               검증한다. Spring 컨텍스트 없이, 실제 런타임과 동일한 Jackson 3
 *               ObjectMapper(JsonMapper)로 확인한다.
 */
class WsFrameTest {

	private final ObjectMapper objectMapper = new JsonMapper();

	@Test
	void serializesChatTokenWithTypeTag() throws Exception {
		UUID sessionId = UUID.randomUUID();
		WsFrame frame = new ChatTokenFrame(sessionId, "안녕");

		String json = objectMapper.writeValueAsString(frame);

		assertThat(json).contains("\"type\":\"chat.token\"", "\"sessionId\":\"" + sessionId + "\"",
				"\"delta\":\"안녕\"");
	}

	@Test
	void deserializesByTypeTagIntoCorrectSubtype() throws Exception {
		UUID sessionId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String json = "{\"type\":\"presence.join\",\"sessionId\":\"%s\",\"userId\":\"%s\"}"
				.formatted(sessionId, userId);

		WsFrame frame = objectMapper.readValue(json, WsFrame.class);

		assertThat(frame).isEqualTo(new PresenceJoinFrame(sessionId, userId));
	}

	@Test
	void allFiveFrameTypesRoundTripThroughJson() throws Exception {
		UUID sessionId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		List<WsFrame> frames = List.of(
				new ChatTokenFrame(sessionId, "delta"),
				new ChatDoneFrame(sessionId),
				new PresenceJoinFrame(sessionId, userId),
				new ChatMessageFrame(sessionId, userId, "content"),
				new ErrorFrame(sessionId, "FORBIDDEN", "권한이 없습니다.", "trace-1"));

		for (WsFrame frame : frames) {
			String json = objectMapper.writeValueAsString(frame);
			WsFrame roundTripped = objectMapper.readValue(json, WsFrame.class);
			assertThat(roundTripped).isEqualTo(frame);
		}
	}

	@Test
	void serializesErrorFrameWithNullSessionId() throws Exception {
		WsFrame frame = new ErrorFrame(null, "UNAUTHENTICATED", "인증이 필요합니다.", "trace-2");

		String json = objectMapper.writeValueAsString(frame);
		WsFrame roundTripped = objectMapper.readValue(json, WsFrame.class);

		assertThat(json).contains("\"type\":\"error\"", "\"code\":\"UNAUTHENTICATED\"");
		assertThat(roundTripped).isEqualTo(frame);
	}
}
