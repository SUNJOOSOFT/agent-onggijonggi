package com.onggijonggi.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Class Name : CollabThreadControllerTest.java
 * Description : GET /api/collab/threads가 호출자를 기준으로 방을 걸러 내려주는지 검증한다(이슈 #22).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ChatControllerTest.FakeChatModelConfig.class, FakeJwtDecoderConfig.class, CollabRoomFixture.class})
class CollabThreadControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private CollabRoomFixture.CollabRooms rooms;

	private RestTestClient restTestClient;

	@BeforeEach
	void setUp() {
		restTestClient = RestTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void returnsOnlyThreadsTheCallerParticipatesIn() {
		UUID joined = rooms.openRoom("threads-owner", "threads-member");
		rooms.openRoom("threads-stranger");

		String body = listThreadsAs("threads-member");

		assertThat(body).contains(joined.toString());
		assertThat(body).contains("\"title\":\"테스트 협업방\"");
	}

	/** 참가한 방이 하나도 없으면 404가 아니라 빈 목록이다 — 화면이 "아직 방이 없다"를 그리는 근거다. */
	@Test
	void returnsEmptyListWhenCallerJoinedNothing() {
		rooms.openRoom("threads-other-owner");

		assertThat(listThreadsAs("threads-loner")).isEqualTo("[]");
	}

	/** app_user에 이름 컬럼이 없어 participants는 아직 채우지 않는다(이슈 #22 코멘트). */
	@Test
	void returnsEmptyParticipantsUntilDisplayNamesExist() {
		rooms.openRoom("threads-participants-owner");

		assertThat(listThreadsAs("threads-participants-owner")).contains("\"participants\":[]");
	}

	private String listThreadsAs(String subject) {
		return restTestClient.get()
				.uri("/api/collab/threads")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtSupport.signedJwt(subject, List.of("USER")))
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();
	}

}
