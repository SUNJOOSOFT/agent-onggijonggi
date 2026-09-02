package com.onggijonggi.api.chat;

import com.onggijonggi.common.model.ModelCatalogService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Class Name : ModelControllerTest.java
 * Description : GET /api/models의 계약(02·EDGE 인증·권한, 응답 형태)을 실제 서버 기동 상태에서
 *               검증한다. 게이트웨이 응답 해석은 ModelCatalogServiceTest가 따로 다루므로 여기서는
 *               ModelCatalogService를 고정 목록 가짜로 교체해 게이트웨이 없이 컨트롤러만 태운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(FakeJwtDecoderConfig.class)
class ModelControllerTest {

	private static final List<String> FAKE_MODELS = List.of("gemini-3.6-flash", "gpt-4o-mini");

	/** 실제 게이트웨이 호출 없이 컨트롤러 계약만 검증하기 위해 카탈로그 조회를 고정 목록으로 대체한다. */
	@TestConfiguration
	static class FakeModelCatalogConfig {

		@Bean
		@Primary
		ModelCatalogService fakeModelCatalogService() {
			return new ModelCatalogService(WebClient.builder(), "http://unused", "unused") {

				@Override
				public Mono<List<String>> listModelIds() {
					return Mono.just(FAKE_MODELS);
				}
			};
		}

	}

	@LocalServerPort
	private int port;

	private RestTestClient restTestClient;

	@BeforeEach
	void setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	/** 화면이 드롭다운을 채우는 경로 — 별칭 문자열 배열이 순서 그대로 와야 한다. */
	@Test
	void returnsModelIdsForAuthenticatedUser() {
		restTestClient.get()
				.uri("/api/models")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtSupport.signedJwt("testuser", List.of("USER")))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$").isArray()
				.jsonPath("$[0]").isEqualTo("gemini-3.6-flash")
				.jsonPath("$[1]").isEqualTo("gpt-4o-mini");
	}

	/** /api/**는 02·EDGE가 인증을 강제한다 — 모델 목록도 예외가 아니다. */
	@Test
	void rejectsRequestWithoutToken() {
		restTestClient.get()
				.uri("/api/models")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
				.expectBody()
				.jsonPath("$.error.code").isEqualTo("UNAUTHENTICATED")
				.jsonPath("$.error.traceId").isNotEmpty();
	}

	/** 인증됐더라도 USER role이 없으면 막힌다(ChatController와 동일 규칙). */
	@Test
	void rejectsRequestWithoutRequiredRole() {
		restTestClient.get()
				.uri("/api/models")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtSupport.signedJwt("testuser", List.of()))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
				.expectBody()
				.jsonPath("$.error.code").isEqualTo("FORBIDDEN");
	}

}
