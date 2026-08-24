/********************************************************
 파일명 : frames.ts (lib/transport)
 설 명 : 서버 WsFrame(backend WsFrame.java, 이슈 #9) 계약을 그대로 미러링한 판별 유니온.
 type 태그는 Jackson @JsonTypeInfo(property = "type")가 쓰는 이름과 정확히 같아야 한다.
 필드명도 Java record 컴포넌트명을 그대로 따른다(커스텀 네이밍 전략 없음 → camelCase 그대로 직렬화).
 *********************************************************/

import type { Citation } from '@/lib/api/chat';

/** 스트리밍 중인 답변의 토큰 조각. 기존 raw text 청크 스트림을 프레임으로 옮긴 형태. */
export interface ChatTokenFrame {
  type: 'chat.token';
  sessionId: string;
  delta: string;
}

/** 답변 스트리밍 종료 신호. 페이로드를 얹지 않는다 — "끝났다" 신호 하나의 책임만 진다
 * (인용정보는 별도 ChatCitationFrame로 분리, 이슈 #10 코멘트 논의 참고). */
export interface ChatDoneFrame {
  type: 'chat.done';
  sessionId: string;
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

/**
 * 근거 인용 프레임 — 제안 스펙, 백엔드(03·CORE) 확정 대기 중(이슈 #10 코멘트 참고).
 * 지금 서버(WsFrame.java)에는 아직 이 타입이 없다. 스트림 종료(chat.done)를 기다리지 않고
 * 독립적으로 보낼 수 있어야, 답변이 생성되는 도중에도 근거 패널이 먼저 채워지는 지금의 UX
 * (CitationsPanel의 loading 상태)를 유지할 수 있다 — 그래서 chat.done에 얹지 않고 별도
 * 타입으로 분리했다.
 */
export interface ChatCitationFrame {
  type: 'chat.citation';
  sessionId: string;
  citations: Citation[];
  restrictedResultsOmitted: boolean;
}

/** 서버 WsFrame과 대응하는 전체 유니온. 새 타입이 추가되면 여기 한 곳만 넓히면 되고,
 * frame-router.ts의 exhaustive switch가 미처리 케이스를 컴파일 타임에 잡아준다. */
export type WsFrame =
  | ChatTokenFrame
  | ChatDoneFrame
  | ChatMessageFrame
  | PresenceJoinFrame
  | WsErrorFrame
  | ChatCitationFrame;

/** WsFrame 서브타입의 type 태그 리터럴만 뽑은 유니온. parse-frame.ts의 태그 검증에 쓴다. */
export type WsFrameType = WsFrame['type'];
