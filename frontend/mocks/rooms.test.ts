import { describe, expect, it, vi } from 'vitest';
import type { PresenceJoinFrame } from '@/lib/transport/frames';
import {
  FORBIDDEN_FRAME_THREAD_ID,
  FORBIDDEN_HANDSHAKE_THREAD_ID,
  MockRoomRegistry,
  type RoomMember,
  mentionsAi,
  roomAccess,
} from './rooms';

/** send를 vi.fn()으로 들고 있는 멤버 — 브로드캐스트가 누구에게 갔는지 이걸로 본다. */
function member(id: string, userId = id) {
  const send = vi.fn<(data: string) => void>();
  return { id, userId, send } satisfies RoomMember;
}

const joinFrame = (userId: string): PresenceJoinFrame => ({
  type: 'presence.join',
  sessionId: 't1',
  userId,
});

describe('roomAccess', () => {
  it('예약되지 않은 threadId는 모두 허용한다', () => {
    expect(roomAccess('t1')).toBe('allow');
  });

  it('거부 시나리오 두 방식을 threadId로 구분한다', () => {
    expect(roomAccess(FORBIDDEN_HANDSHAKE_THREAD_ID)).toBe('deny-handshake');
    expect(roomAccess(FORBIDDEN_FRAME_THREAD_ID)).toBe('deny-frame');
  });
});

describe('mentionsAi', () => {
  it('대소문자를 가리지 않고 잡는다', () => {
    expect(mentionsAi('@AI 이거 알려줘')).toBe(true);
    expect(mentionsAi('@ai 이거 알려줘')).toBe(true);
  });

  it('한글이 바로 붙어도 멘션으로 읽는다', () => {
    expect(mentionsAi('@AI야 이거 알려줘')).toBe(true);
  });

  it('영문이 이어 붙으면 멘션이 아니다', () => {
    expect(mentionsAi('@AIssistant')).toBe(false);
  });

  it('멘션이 없으면 false — 이 경우 AI 파이프라인을 타지 않는다', () => {
    expect(mentionsAi('오늘 회의 몇 시죠?')).toBe(false);
  });
});

describe('MockRoomRegistry', () => {
  it('같은 방의 전원에게 보내고, 보낸 사람도 받는다', () => {
    const registry = new MockRoomRegistry();
    const alice = member('c1', 'alice');
    const bob = member('c2', 'bob');
    registry.join('t1', alice);
    registry.join('t1', bob);

    registry.broadcast('t1', joinFrame('alice'));

    const expected = JSON.stringify(joinFrame('alice'));
    expect(alice.send).toHaveBeenCalledExactlyOnceWith(expected);
    expect(bob.send).toHaveBeenCalledExactlyOnceWith(expected);
  });

  it('다른 방에는 새어 나가지 않는다', () => {
    const registry = new MockRoomRegistry();
    const here = member('c1');
    const elsewhere = member('c2');
    registry.join('t1', here);
    registry.join('t2', elsewhere);

    registry.broadcast('t1', joinFrame('c1'));

    expect(here.send).toHaveBeenCalledOnce();
    expect(elsewhere.send).not.toHaveBeenCalled();
  });

  it('같은 사용자가 탭 두 개를 띄우면 커넥션 둘로 센다', () => {
    const registry = new MockRoomRegistry();
    registry.join('t1', member('c1', 'alice'));
    registry.join('t1', member('c2', 'alice'));

    expect(registry.membersOf('t1')).toHaveLength(2);
  });

  it('나간 커넥션에는 더 이상 보내지 않는다', () => {
    const registry = new MockRoomRegistry();
    const alice = member('c1', 'alice');
    const bob = member('c2', 'bob');
    registry.join('t1', alice);
    registry.join('t1', bob);

    registry.leave('t1', 'c1');
    registry.broadcast('t1', joinFrame('bob'));

    expect(alice.send).not.toHaveBeenCalled();
    expect(bob.send).toHaveBeenCalledOnce();
  });

  it('마지막 참여자가 나가면 빈 방이 남지 않는다', () => {
    const registry = new MockRoomRegistry();
    registry.join('t1', member('c1'));
    registry.leave('t1', 'c1');

    expect(registry.membersOf('t1')).toEqual([]);
  });
});
