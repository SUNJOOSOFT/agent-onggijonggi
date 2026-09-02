package com.onggijonggi.api.chat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Class Name : RateLimitWebFilterTest.java
 * Description : sub(JWT subject)별 고정 윈도우 RateLimit의 429 응답·Retry-After
 *               헤더·윈도우 리셋을 검증한다. 기본값(분당 20회)으로는 테스트가 느려지므로
 *               app.ratelimit.per-minute/window-seconds를 낮게 오버라이드한 별도 컨텍스트를 쓴다
 *               (ChatControllerTest와 다른 프로퍼티라 Spring이 컨텍스트를 별도로 캐싱한다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"app.ratelimit.per-minute=2", "app.ratelimit.window-seconds=" + RateLimitWebFilterTest.WINDOW_SECONDS})
@ActiveProfiles("test")
@Import({ChatControllerTest.FakeChatModelConfig.class, FakeJwtDecoderConfig.class})
class RateLimitWebFilterTest {

	static final int WINDOW_SECONDS = 2;

	@LocalServerPort
	private int port;

	private RestTestClient restTestClient;

	@BeforeEach
	void setUp() {
		restTestClient = RestTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	/**
	* awaitWindowStart: 고정 윈도우(RateLimitWebFilter, 벽시계 기준 WINDOW_SECONDS초 버킷)의 경계에 요청이
	* 걸쳐 카운트가 새 윈도우에서 재시작되는 걸 피하기 위해, 방금 시작된 새 윈도우의 맨 앞까지 대기한다.
	*/
	private static void awaitWindowStart() throws InterruptedException {
		long windowMillis = WINDOW_SECONDS * 1000L;
		long msIntoWindow = System.currentTimeMillis() % windowMillis;
		Thread.sleep(windowMillis - msIntoWindow + 50);
	}

	private static String requestBody() {
		return """
				{
				  "sessionId": "11111111-1111-1111-1111-111111111111",
				  "modelId": "test-model",
				  "messages": [ { "role": "user", "content": "안녕" } ]
				}
				""";
	}

	/**
	* returnsRateLimitedAfterExceedingPerMinuteLimit: 한도(2회)까지는 200이 오고, 그다음 요청은
	* 429 + Retry-After 헤더 + RATE_LIMITED 봉투가 오는지, 윈도우가 지나면 다시 200으로 리셋되는지 검증한다.
	*/
	@Test
	void returnsRateLimitedAfterExceedingPerMinuteLimit() throws InterruptedException {
		String token = "Bearer " + TestJwtSupport.signedJwt("ratelimit-user", List.of("USER"));

		awaitWindowStart();

		for (int i = 0; i < 2; i++) {
			restTestClient.post()
					.uri("/api/chat/stream")
					.contentType(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.AUTHORIZATION, token)
					.body(requestBody())
					.exchange()
					.expectStatus().isOk();
		}

		restTestClient.post()
				.uri("/api/chat/stream")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, token)
				.body(requestBody())
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
				.expectHeader().exists(HttpHeaders.RETRY_AFTER)
				.expectBody()
				.jsonPath("$.error.code").isEqualTo("RATE_LIMITED");

		Thread.sleep(2_100);

		restTestClient.post()
				.uri("/api/chat/stream")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, token)
				.body(requestBody())
				.exchange()
				.expectStatus().isOk();
	}

}
