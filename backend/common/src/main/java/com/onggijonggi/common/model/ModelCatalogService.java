package com.onggijonggi.common.model;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Class Name : ModelCatalogService.java
 * Description : 게이트웨이가 실제로 서빙 중인 모델 목록을 OpenAI 호환 GET /v1/models로 조회한다.
 *               목록의 진실은 infra/config/litellm_config.yaml의 model_list 하나뿐이라 BFF는
 *               자체 목록을 두지 않고 매번 게이트웨이에 물어본다 — 설정을 바꾸면 재배포 없이 따라온다.
 */
@Service
public class ModelCatalogService {

	private final WebClient webClient;

	public ModelCatalogService(WebClient.Builder webClientBuilder,
			@Value("${app.llm.gateway-url}") String gatewayUrl,
			@Value("${spring.ai.openai.api-key}") String apiKey) {
		this.webClient = webClientBuilder
				.baseUrl(gatewayUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
	}

	/**
	 * 응답에서 data[].id만 뽑는다. id는 model_list의 model_name(별칭)이고, 이 값이 화면의 선택 목록에
	 * 그대로 뜨면서 채팅 요청의 modelId로 되돌아온다 — 그래서 별도 라벨을 두지 않는다.
	 */
	public Mono<List<String>> listModelIds() {
		return webClient.get()
				.uri("/v1/models")
				.retrieve()
				.bodyToMono(ModelsResponse.class)
				.map(response -> response.data().stream().map(ModelEntry::id).toList());
	}

	/** /v1/models 응답 중 실제로 쓰는 필드만 담는다(나머지는 Jackson이 무시한다). */
	record ModelsResponse(List<ModelEntry> data) {
	}

	record ModelEntry(String id) {
	}

}
