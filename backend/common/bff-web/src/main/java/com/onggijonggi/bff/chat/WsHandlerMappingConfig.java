package com.onggijonggi.bff.chat;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * Class Name : WsHandlerMappingConfig.java
 * Description : /api/ws를 CollabWebSocketHandler로 라우팅한다. 경로는 WsSecurityConfig의
 *               securityMatcher("/api/ws/**")와 반드시 맞춰야 한다.
 */
@Configuration
public class WsHandlerMappingConfig {

	@Bean
	public HandlerMapping collabWebSocketMapping(CollabWebSocketHandler collabWebSocketHandler) {
		return new SimpleUrlHandlerMapping(Map.of("/api/ws", collabWebSocketHandler), Ordered.HIGHEST_PRECEDENCE);
	}

}
