package com.onggijonggi.common.chat.domain;

/**
 * Class Name : ThrKind.java
 * Description : thr.kind 값. DIRECT는 1:1 대화라 소유자(drc_own_user_id)를 갖고, COLLAB은 소유자
 *               컬럼 대신 OWNER 역할 참가자로 소유를 표현한다 — V8__thread.sql의 CHECK가 이
 *               대응을 강제한다.
 */
public enum ThrKind {
	DIRECT,
	COLLAB
}
