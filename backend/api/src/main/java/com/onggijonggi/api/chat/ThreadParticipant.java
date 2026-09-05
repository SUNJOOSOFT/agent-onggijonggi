package com.onggijonggi.api.chat;

import com.onggijonggi.common.chat.domain.ThrMbrRole;

/**
 * Class Name : ThreadParticipant.java
 * Description : GET /api/collab/threads/{threadId}/participants 응답 항목.
 *
 *               내부 app_user.id는 담지 않는다. WS 프레임은 사람을 내부 id로 가리키고 이 API는
 *               subject로 가리켜 서로 맞물리지 않는데, 어느 쪽으로 맞출지는 이슈 #130에서 정한다 —
 *               여기서 내부 id를 함께 내려주면 그 결정을 미리 한쪽으로 굳혀 버린다.
 *
 *               표시 이름도 없다. app_user에 이름 컬럼을 두지 않기로 해서, 화면에 보일 이름은
 *               JWT claim에서 읽어야 하고 그건 이슈 #128 몫이다.
 * @param subject 참가자의 Keycloak subject
 * @param role 이 방에서의 역할
 */
public record ThreadParticipant(String subject, ThrMbrRole role) {
}
