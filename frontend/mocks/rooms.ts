/********************************************************
 파일명 : rooms.ts (mocks)
 설 명 : 목업 WS 서버(ws-server.ts)의 순수 로직 — 방 레지스트리·`@AI` 판정·방 접근 판정.
 Bun.serve 핸들러에서 떼어 둔 이유는 vitest 환경이 'node'라 Bun 런타임이 없어서다. 소켓을
 모르는 형태(send 콜백 하나)로 두면 이 파일만 단위 테스트할 수 있다.

 서버 측 대응물은 이슈 #16(채팅방 세션 레지스트리)의 `Sinks.Many` 기반 방송이다. 목업은 그
 계약(방 단위 브로드캐스트)만 흉내 내고, 인가·영속화는 다루지 않는다.
 *********************************************************/

import type { WsFrame } from '@/lib/transport/frames';

/** threadId를 안 주고 붙었을 때 들어가는 방. 개발 중 URL을 매번 안 적어도 되게 한다. */
export const DEFAULT_THREAD_ID = 'mock-thread';

/**
 * 방 접근 거부를 재현하는 threadId. 실서버가 인가 실패를 **핸드셰이크 거부**로 줄지
 * **error 프레임**으로 줄지는 이슈 #22 완료 기준에 미정 항목으로 남아 있다 — 화면(#19) 처리가
 * 완전히 갈리는 분기라, 목업은 두 방식을 모두 재현해 양쪽 UI를 다 시험해볼 수 있게 한다.
 */
export const FORBIDDEN_HANDSHAKE_THREAD_ID = 'forbidden-close';
export const FORBIDDEN_FRAME_THREAD_ID = 'forbidden-frame';

export type RoomAccess = 'allow' | 'deny-handshake' | 'deny-frame';

/** 방 입장 가부와 거부 방식. 위 두 예약 threadId 외에는 전부 허용한다(목업엔 참여자 테이블이 없다). */
export function roomAccess(threadId: string): RoomAccess {
  if (threadId === FORBIDDEN_HANDSHAKE_THREAD_ID) return 'deny-handshake';
  if (threadId === FORBIDDEN_FRAME_THREAD_ID) return 'deny-frame';
  return 'allow';
}

/**
 * `@AI` 멘션 여부 — 이 판정이 참일 때만 LLM 응답을 흘린다(이슈 #13·#17의 핵심 정책:
 * 멘션 없는 참여자 간 대화는 AI 파이프라인을 타지 않는다).
 *
 * 실서버의 판정 규칙은 #17에서 확정되므로, 목업은 그때까지 대소문자를 무시하는 관대한 쪽으로
 * 둔다. 뒤 경계를 두는 건 `@AIsomething`을 멘션으로 읽지 않기 위해서다 — 한글은 단어 문자가
 * 아니라 `@AI야`는 그대로 멘션으로 잡힌다.
 */
export function mentionsAi(content: string): boolean {
  return /@ai\b/i.test(content);
}

/** 방에 붙어 있는 커넥션 하나. id는 커넥션 단위라 같은 사용자가 탭 두 개를 띄우면 둘로 센다. */
export interface RoomMember {
  id: string;
  userId: string;
  send: (data: string) => void;
}

/**
 * threadId → 접속 중인 커넥션. 서버 인스턴스가 1대라는 전제는 실서버도 같다(부모 이슈 #13의
 * 명시된 리스크) — 목업은 프로세스 하나뿐이라 그 전제가 그대로 성립한다.
 */
export class MockRoomRegistry {
  private readonly rooms = new Map<string, Map<string, RoomMember>>();

  join(threadId: string, member: RoomMember): void {
    const room = this.rooms.get(threadId) ?? new Map<string, RoomMember>();
    room.set(member.id, member);
    this.rooms.set(threadId, room);
  }

  /** 방이 비면 Map 엔트리째 지운다 — 안 지우면 오래 띄워 둔 목업에 빈 방이 쌓인다. */
  leave(threadId: string, memberId: string): void {
    const room = this.rooms.get(threadId);
    if (!room) return;
    room.delete(memberId);
    if (room.size === 0) this.rooms.delete(threadId);
  }

  membersOf(threadId: string): RoomMember[] {
    return [...(this.rooms.get(threadId)?.values() ?? [])];
  }

  /** 방 전원에게 같은 프레임을 보낸다. 보낸 사람도 포함된다 — 실서버 `Sinks.Many` 방송과 같다. */
  broadcast(threadId: string, frame: WsFrame): void {
    const text = JSON.stringify(frame);
    for (const member of this.membersOf(threadId)) member.send(text);
  }
}
