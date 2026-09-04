package com.onggijonggi.api.auth;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Class Name : RateLimitClockConfig.java
 * Description : HTTP·WebSocket 레이트리밋 필터가 공유하는 운영 시간원을 제공한다.
 */
@Configuration
public class RateLimitClockConfig {

	@Bean
	Clock rateLimitClock() {
		return Clock.systemUTC();
	}
}
