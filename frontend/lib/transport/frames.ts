/********************************************************
 파일명 : frames.ts (lib/transport)
 설 명 : 서버 WsFrame(backend WsFrame.java, 이슈 #9) 계약을 그대로 미러링한 판별 유니온.
 type 태그는 Jackson @JsonTypeInfo(property = "type")가 쓰는 이름과 정확히 같아야 한다.
 필드명도 Java record 컴포넌트명을 그대로 따른다(커스텀 네이밍 전략 없음 → camelCase 그대로 직렬화).
 *********************************************************/

import type { Citation } from '@/lib/api/chat';

/**
 * 답변 스트리밍 패킷 — chat.token/chat.done/(제안했던)chat.citation 세 개를 흡수한 통합 타입
 * (이슈 #10 코멘트, bsjSunjoo 확정 스펙). 백엔드가 같은 이름·필드로 별도 이슈에서 구현 예정.
 *
 * delta·citations는 패킷마다 필요한 것만 채워서 온다 — 둘 다 비어 있는 패킷도 유효하다(예:
 * status만 알리는 하트비트성 패킷). citations를 delta보다 먼저(빈 delta + status:'streaming'
 * 조합으로) 보낼 수 있어, 답변이 다 끝나기 전에 근거 패널이 먼저 채워지는 지금의 UX
 * (CitationsPanel의 loading 상태)를 유지할 수 있다 — 이게 chat.done에 얹지 않고 별도 필드로
 * 분리해서 얻은 것이다.
 *
 * status:'done'인 패킷도 delta·citations를 함께 실어 보낼 수 있는지는 스펙에 명시돼 있지 않다
 * — 그래서 이 어댑터는 status와 무관하게 매 패킷마다 delta·citations를 항상 처리하고,
 * status:'done'만 별도로 "스트림 종료" 신호로 취급한다(frame-stream-fetch.ts 참고).
 *
 * restrictedResultsOmitted: 기존 REST CitationsResponse(lib/api/chat.ts)에 있던 필드를 그대로
 * 옮겼다 — citations-panel.tsx가 true일 때 "일부 문서는 접근 권한이 없어 결과에서
 * 제외되었습니다" RBAC 안내를 렌더링하는 데 쓴다(PR #50 리뷰, bsjSunjoo). citations가 빈
 * 배열이어도 이 값이 true일 수 있다(전부 걸러진 경우) — 그래서 두 필드는 서로 독립이다.
 */
export interface ChatAnswerFrame {
  type: 'chat.answer';
  sessionId: string;
  delta: string;
  citations: Citation[];
  restrictedResultsOmitted: boolean;
  status: 'streaming' | 'done';
}

/** 참여자 간 일반 대화 메시지. AI 호출 라우팅 정책은 이슈 #13에서 결정 중. */
export interface ChatMessageFrame {
  type: 'chat.message';
  sessionId: string;
  from: string;
  content: string;
}

/** 참여자 입장 이벤트. 퇴장 대칭 이벤트는 아직 없다(이슈 #25 논의 중). */
export interface PresenceJoinFrame {
  type: 'presence.join';
  sessionId: string;
  userId: string;
}

/** 스트림 중 발생한 오류. HTTP 쪽 BffErrorEnvelope(lib/api/errors.ts)와 code/message/traceId를
 * 같은 모양으로 재사용한다. 연결 수립 자체가 실패하는 등 특정 세션에 속하지 않는 오류는
 * sessionId가 null일 수 있다. */
export interface WsErrorFrame {
  type: 'error';
  sessionId: string | null;
  code: string;
  message: string;
  traceId: string;
}

/** 서버 WsFrame과 대응하는 전체 유니온. 새 타입이 추가되면 여기 한 곳만 넓히면 되고,
 * frame-router.ts의 exhaustive switch가 미처리 케이스를 컴파일 타임에 잡아준다. */
export type WsFrame =
  | ChatAnswerFrame
  | ChatMessageFrame
  | PresenceJoinFrame
  | WsErrorFrame;

/** WsFrame 서브타입의 type 태그 리터럴만 뽑은 유니온. parse-frame.ts의 태그 검증에 쓴다. */
export type WsFrameType = WsFrame['type'];
