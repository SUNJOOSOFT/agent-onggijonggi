package com.onggijonggi.common.chat.domain;

/**
 * Class Name : ThrMbrStatus.java
 * Description : thr_mbr.status 값. 참가자를 뺄 때 행을 지우지 않고 이 값으로 표시해, 누가 언제 왜
 *               나갔는지를 남긴다. 재초대는 과거 행을 되살리지 않고 새 행을 만든다.
 */
public enum ThrMbrStatus {
	ACTIVE,
	LEFT,
	REVOKED
}
