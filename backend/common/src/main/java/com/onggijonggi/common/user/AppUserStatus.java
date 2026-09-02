package com.onggijonggi.common.user;

/**
 * Class Name : AppUserStatus.java
 * Description : app_user.status(V9__app_user_status.sql) 값. 인가 판정에 쓰지 않는다 — 계정이
 *               살아 있는지는 Keycloak이 정본이고, 이 값은 참조 무결성과 Tombstone 표시용이다.
 */
public enum AppUserStatus {
	ACTIVE,
	INACTIVE
}
