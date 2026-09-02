package com.onggijonggi.api.chat;

import com.onggijonggi.common.model.ModelCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Class Name : ModelController.java
 * Description : 01·CLIENT 모델 선택 목록 조회 계약 구현체. 화면이 목록을 상수로 갖지 않고 이 응답으로
 *               드롭다운을 채운다. 02·EDGE(SecurityConfig)의 /api/** 규칙이 인증·권한을 이미 강제한다.
 */
@RestController
public class ModelController {

	private final ModelCatalogService modelCatalogService;

	public ModelController(ModelCatalogService modelCatalogService) {
		this.modelCatalogService = modelCatalogService;
	}

	@GetMapping("/api/models")
	public Mono<List<String>> listModels() {
		return modelCatalogService.listModelIds();
	}

}
