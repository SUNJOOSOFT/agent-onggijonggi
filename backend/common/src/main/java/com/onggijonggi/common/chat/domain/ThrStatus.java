package com.onggijonggi.common.chat.domain;

/**
 * Class Name : ThrStatus.java
 * Description : thr.status 값. 상태마다 짝이 되는 시각 컬럼(locked_at·archived_at)이 있어야 한다는
 *               제약이 V8__thread.sql의 CHECK로 걸려 있다.
 */
public enum ThrStatus {
	ACTIVE,
	LOCKED,
	ARCHIVED
}
