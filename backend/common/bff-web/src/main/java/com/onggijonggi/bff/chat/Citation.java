package com.onggijonggi.bff.chat;

/**
 * Class Name : Citation.java
 * Description : 근거 인용 문서 한 건. ChatAnswerFrame.citations의 원소 타입이다. 기존 REST
 *               /api/chat/citations 응답(CitationsResponse)의 citation 항목과 필드 구성이 같다.
 */
public record Citation(String docId, String title, String snippet, double score) {
}
