import { describe, expect, it, vi } from 'vitest';
import { routeFrame } from './frame-router';
import type {
  ChatCitationFrame,
  ChatDoneFrame,
  ChatMessageFrame,
  ChatTokenFrame,
  PresenceJoinFrame,
  WsErrorFrame,
} from './frames';

describe('routeFrame', () => {
  it('chat.token은 onChatToken에게만 원본 payload로 전달된다', () => {
    const frame: ChatTokenFrame = {
      type: 'chat.token',
      sessionId: 's1',
      delta: '안녕',
    };
    const onChatToken = vi.fn();
    const onChatDone = vi.fn();
    routeFrame(frame, { onChatToken, onChatDone });
    expect(onChatToken).toHaveBeenCalledExactlyOnceWith(frame);
    expect(onChatDone).not.toHaveBeenCalled();
  });

  it('chat.done을 라우팅한다', () => {
    const frame: ChatDoneFrame = { type: 'chat.done', sessionId: 's1' };
    const onChatDone = vi.fn();
    routeFrame(frame, { onChatDone });
    expect(onChatDone).toHaveBeenCalledExactlyOnceWith(frame);
  });

  it('chat.message를 라우팅한다', () => {
    const frame: ChatMessageFrame = {
      type: 'chat.message',
      sessionId: 's1',
      from: 'u1',
      content: '안녕하세요',
    };
    const onChatMessage = vi.fn();
    routeFrame(frame, { onChatMessage });
    expect(onChatMessage).toHaveBeenCalledExactlyOnceWith(frame);
  });

  it('presence.join을 라우팅한다', () => {
    const frame: PresenceJoinFrame = {
      type: 'presence.join',
      sessionId: 's1',
      userId: 'u1',
    };
    const onPresenceJoin = vi.fn();
    routeFrame(frame, { onPresenceJoin });
    expect(onPresenceJoin).toHaveBeenCalledExactlyOnceWith(frame);
  });

  it('error를 라우팅한다', () => {
    const frame: WsErrorFrame = {
      type: 'error',
      sessionId: null,
      code: 'UNAUTHENTICATED',
      message: '인증이 필요합니다.',
      traceId: 't1',
    };
    const onError = vi.fn();
    routeFrame(frame, { onError });
    expect(onError).toHaveBeenCalledExactlyOnceWith(frame);
  });

  it('chat.citation을 라우팅한다', () => {
    const frame: ChatCitationFrame = {
      type: 'chat.citation',
      sessionId: 's1',
      citations: [{ docId: 'd1', title: '제목', snippet: '발췌', score: 0.9 }],
      restrictedResultsOmitted: false,
    };
    const onChatCitation = vi.fn();
    routeFrame(frame, { onChatCitation });
    expect(onChatCitation).toHaveBeenCalledExactlyOnceWith(frame);
  });

  it('해당 타입 핸들러를 안 넘겨도 예외 없이 조용히 무시한다', () => {
    const frame: ChatTokenFrame = {
      type: 'chat.token',
      sessionId: 's1',
      delta: 'x',
    };
    expect(() => routeFrame(frame, {})).not.toThrow();
  });

  it('handlers가 여러 타입을 갖고 있어도 해당 프레임의 핸들러만 호출된다', () => {
    const frame: ChatDoneFrame = { type: 'chat.done', sessionId: 's1' };
    const onChatToken = vi.fn();
    const onChatDone = vi.fn();
    const onError = vi.fn();
    routeFrame(frame, { onChatToken, onChatDone, onError });
    expect(onChatDone).toHaveBeenCalledOnce();
    expect(onChatToken).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
  });
});
