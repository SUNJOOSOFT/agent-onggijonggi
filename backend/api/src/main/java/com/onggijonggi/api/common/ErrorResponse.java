package com.onggijonggi.api.common;

/**
 * Class Name : ErrorResponse.java
 * Description : 공통 에러 봉투. 4xx/5xx 응답 바디로 사용된다.
 *               - traceId는 항상 호출부가 넘긴다. of(code, message) 2-인자 오버로드는 두지 않는다
 *                 — 그 안에서 traceId를 새로 발급하면 X-Trace-Id 헤더의 값과 어긋난다.
 */
public record ErrorResponse(ErrorBody error) {

	/** message에는 서버 내부 상세를 담지 않는다 — 클라이언트에 그대로 노출된다. */
	public record ErrorBody(String code, String message, String traceId) {
	}

	public static ErrorResponse of(String code, String message, String traceId) {
		return new ErrorResponse(new ErrorBody(code, message, traceId));
	}

}
