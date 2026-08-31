import { describe, expect, it } from 'vitest';
import type { WsFrame } from '@/lib/transport/frames';
import {
  type RoomState,
  applyFrame,
  initialRoomState,
  isForbidden,
} from './room-state';

const THREAD = 'thread-1';

function join(userId: string): WsFrame {
  return { type: 'presence.join', sessionId: THREAD, userId };
}

function say(from: string, content: string): WsFrame {
  return { type: 'chat.message', sessionId: THREAD, from, content };
}

function answer(delta: string, status: 'streaming' | 'done'): WsFrame {
  return {
    type: 'chat.answer',
    sessionId: THREAD,
    delta,
    citations: [],
    restrictedResultsOmitted: false,
    status,
  };
}

/** 프레임을 순서대로 접는다 — 테스트가 화면 없이 대화 한 판을 재현하는 방법이다. */
function fold(frames: WsFrame[], from: RoomState = initialRoomState): RoomState {
  return frames.reduce(applyFrame, from);
}

describe('applyFrame - presence.join', () => {
  it('입장 순서대로 참여자를 쌓는다', () => {
    const state = fold([join('sujin'), join('minho')]);
    expect(state.participants).toEqual(['sujin', 'minho']);
  });

  it('스냅샷 재생으로 같은 사람이 다시 와도 한 번만 센다', () => {
    // 서버는 전용 스냅샷 프레임이 없어 기존 참여자의 presence.join을 되풀이해 보낸다.
    const state = fold([join('sujin'), join('minho'), join('sujin')]);
    expect(state.participants).toEqual(['sujin', 'minho']);
  });
});

describe('applyFrame - chat.message', () => {
  it('보낸 사람을 함께 남긴다', () => {
    const state = fold([say('sujin', '이 계약서 확인 부탁해요')]);
    expect(state.messages).toEqual([
      {
        id: 'm1',
        from: 'sujin',
        content: '이 계약서 확인 부탁해요',
        streaming: false,
      },
    ]);
  });
});

describe('applyFrame - chat.answer', () => {
  it('여러 패킷을 말풍선 하나로 잇고 done에서 멈춘다', () => {
    const state = fold([
      answer('제12조에 ', 'streaming'),
      answer('따르면 ', 'streaming'),
      answer('상한은 10%입니다.', 'done'),
    ]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({
      from: null,
      content: '제12조에 따르면 상한은 10%입니다.',
      streaming: false,
    });
  });

  it('답변이 끝난 뒤 오는 패킷은 새 말풍선이 된다', () => {
    const state = fold([
      answer('첫 답변', 'done'),
      answer('두 번째 답변', 'done'),
    ]);
    expect(state.messages.map((m) => m.content)).toEqual([
      '첫 답변',
      '두 번째 답변',
    ]);
  });

  it('내용이 없는 패킷으로 빈 말풍선을 만들지 않는다', () => {
    // delta 없이 status만 알리는 패킷도 계약상 유효하다(frames.ts).
    expect(fold([answer('', 'streaming')]).messages).toEqual([]);
  });

  it('답변이 흐르는 중에 다른 사람이 말해도 한 말풍선으로 잇는다', () => {
    // 협업방에서는 흔한 순서다. 맨 끝만 보고 이어붙이면 답변이 둘로 갈린다(PR #80 리뷰).
    const state = fold([
      answer('제12조에 ', 'streaming'),
      say('minho', '저도 그거 궁금했어요'),
      answer('따르면 10%입니다.', 'done'),
    ]);

    expect(state.messages.map((m) => m.from)).toEqual([null, 'minho']);
    expect(state.messages[0]).toMatchObject({
      content: '제12조에 따르면 10%입니다.',
      streaming: false,
    });
  });

  it('사람 메시지 뒤에 오면 그 메시지에 섞이지 않는다', () => {
    const state = fold([say('sujin', '@AI 요약해줘'), answer('요약', 'done')]);
    expect(state.messages.map((m) => m.from)).toEqual(['sujin', null]);
  });
});

describe('applyFrame - error', () => {
  it('FORBIDDEN은 방 접근 거부로 읽는다', () => {
    const state = fold([
      {
        type: 'error',
        sessionId: THREAD,
        code: 'FORBIDDEN',
        message: '이 방에 접근할 수 없습니다.',
        traceId: 'trace-1',
      },
    ]);
    expect(isForbidden(state.error)).toBe(true);
    expect(state.error?.message).toBe('이 방에 접근할 수 없습니다.');
  });

  it('그 밖의 오류는 접근 거부가 아니다', () => {
    const state = fold([
      {
        type: 'error',
        sessionId: null,
        code: 'RATE_LIMITED',
        message: '요청이 많습니다.',
        traceId: 'trace-2',
      },
    ]);
    expect(isForbidden(state.error)).toBe(false);
    expect(state.error?.code).toBe('RATE_LIMITED');
  });
});

describe('applyFrame', () => {
  it('받은 상태를 바꾸지 않는다', () => {
    const before = fold([join('sujin')]);
    const snapshot = structuredClone(before);
    applyFrame(before, say('sujin', '안녕하세요'));
    expect(before).toEqual(snapshot);
  });
});
