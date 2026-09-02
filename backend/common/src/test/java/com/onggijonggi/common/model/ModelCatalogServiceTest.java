package com.onggijonggi.common.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class Name : ModelCatalogServiceTest.java
 * Description : 게이트웨이 /v1/models 응답을 어떻게 해석하는지만 검증한다. WebClient의
 *               exchangeFunction을 가짜로 물려 HTTP를 통째로 대체하므로 네트워크도 실행 중인
 *               게이트웨이도 필요 없다. 요청을 붙잡아 두어 "어디로, 어떤 헤더로 부르는가"까지 함께 본다.
 */
class ModelCatalogServiceTest {

	private static final String GATEWAY_URL = "http://gateway:4000";

	private static final String MASTER_KEY = "sk-test-master-key";

	/** 마지막으로 나간 요청 — 응답 해석뿐 아니라 호출 방식도 검증하기 위해 붙잡아 둔다. */
	private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();

	private ModelCatalogService serviceReturning(String responseBody) {
		WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
			lastRequest.set(request);
			return Mono.just(ClientResponse.create(HttpStatus.OK)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body(responseBody)
					.build());
		});
		return new ModelCatalogService(builder, GATEWAY_URL, MASTER_KEY);
	}

	/**
	* 응답에는 컨텍스트 길이·소유자 등 화면이 쓰지 않는 필드가 함께 온다. 별칭(id)만 뽑아야
	* 드롭다운에 그대로 쓸 수 있고, model_list에 적힌 순서도 그대로 보존돼야 한다
	* (맨 위 항목이 화면의 기본 선택이기 때문).
	*/
	@Test
	void extractsModelIdsInGatewayOrder() {
		ModelCatalogService service = serviceReturning("""
				{
				  "data": [
				    { "id": "gemini-3.6-flash", "object": "model", "owned_by": "openai" },
				    { "id": "claude-sonnet-5", "object": "model" },
				    { "id": "gpt-4o-mini", "object": "model", "max_input_tokens": 128000 }
				  ],
				  "object": "list"
				}
				""");

		StepVerifier.create(service.listModelIds())
				.expectNext(List.of("gemini-3.6-flash", "claude-sonnet-5", "gpt-4o-mini"))
				.verifyComplete();
	}

	/**
	* 모르는 필드가 섞여 있어도 깨지지 않아야 한다 — 게이트웨이가 LiteLLM 버전에 따라 응답 필드를
	* 늘려도 목록 조회가 멈추면 안 되기 때문이다(ModelCatalogService가 record 두 개로만 받는 근거).
	*/
	@Test
	void ignoresUnknownFields() {
		ModelCatalogService service = serviceReturning("""
				{
				  "data": [ { "id": "gemini-3.6-flash", "새로운_필드": { "중첩": true } } ],
				  "object": "list",
				  "또다른_최상위_필드": 1
				}
				""");

		StepVerifier.create(service.listModelIds())
				.expectNext(List.of("gemini-3.6-flash"))
				.verifyComplete();
	}

	/** 목록이 비어 있어도 오류가 아니다 — 게이트웨이에 등록된 모델이 없는 정상 상태다. */
	@Test
	void returnsEmptyListWhenGatewayServesNoModel() {
		ModelCatalogService service = serviceReturning("""
				{ "data": [], "object": "list" }
				""");

		StepVerifier.create(service.listModelIds())
				.expectNext(List.of())
				.verifyComplete();
	}

	/**
	* app.llm.gateway-url은 Spring AI의 base-url(/v1이 붙어 있다)과 다른 값이라 헷갈리기 쉽다.
	* 경로가 /v1/v1/models로 겹치거나 마스터 키가 빠지면 게이트웨이가 거절하므로 함께 못 박아 둔다.
	*/
	@Test
	void callsGatewayModelsEndpointWithMasterKey() {
		ModelCatalogService service = serviceReturning("""
				{ "data": [], "object": "list" }
				""");

		service.listModelIds().block();

		ClientRequest request = lastRequest.get();
		assertThat(request.method()).isEqualTo(HttpMethod.GET);
		assertThat(request.url()).hasToString(GATEWAY_URL + "/v1/models");
		assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + MASTER_KEY);
	}

}
