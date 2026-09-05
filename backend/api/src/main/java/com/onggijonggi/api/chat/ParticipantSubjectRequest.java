package com.onggijonggi.api.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * Class Name : ParticipantSubjectRequest.java
 * Description : 참가자 초대(POST participants)와 소유권 위임(PUT owner)의 요청 바디. 두 동작이
 *               같은 값 하나(대상의 Keycloak subject)만 받아 레코드를 하나로 쓴다.
 *
 *               길이 제한을 두지 않는 것은 이 값이 app_user.keycloak_subj 조회 키로만 쓰이고,
 *               맞지 않으면 그대로 404가 되기 때문이다.
 * @param subject 대상의 Keycloak subject
 */
public record ParticipantSubjectRequest(
		@NotBlank String subject
) {
}
