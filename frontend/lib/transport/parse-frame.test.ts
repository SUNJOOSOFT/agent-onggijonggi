import { describe, expect, it } from 'vitest';
import { parseFrame, parseFrameFromText } from './parse-frame';

describe('parseFrame', () => {
  it('chat.token을 파싱한다', () => {
    const frame = parseFrame({
      type: 'chat.token',
      sessionId: 's1',
      delta: '안녕',
    });
    expect(frame).toEqual({
      type: 'chat.token',
      sessionId: 's1',
      delta: '안녕',
    });
  });

  it('chat.done을 파싱한다', () => {
    const frame = parseFrame({ type: 'chat.done', sessionId: 's1' });
    expect(frame).toEqual({ type: 'chat.done', sessionId: 's1' });
  });

  it('chat.message를 파싱한다', () => {
    const frame = parseFrame({
      type: 'chat.message',
      sessionId: 's1',
      from: 'u1',
      content: '안녕하세요',
    });
    expect(frame).toEqual({
      type: 'chat.message',
      sessionId: 's1',
      from: 'u1',
      content: '안녕하세요',
    });
  });

  it('presence.join을 파싱한다', () => {
    const frame = parseFrame({
      type: 'presence.join',
      sessionId: 's1',
      userId: 'u1',
    });
    expect(frame).toEqual({
      type: 'presence.join',
      sessionId: 's1',
      userId: 'u1',
    });
  });

  it('error를 파싱한다 (sessionId 있음)', () => {
    const frame = parseFrame({
      type: 'error',
      sessionId: 's1',
      code: 'MODEL_UNAVAILABLE',
      message: '모델을 호출할 수 없습니다.',
      traceId: 't1',
    });
    expect(frame).toEqual({
      type: 'error',
      sessionId: 's1',
      code: 'MODEL_UNAVAILABLE',
      message: '모델을 호출할 수 없습니다.',
      traceId: 't1',
    });
  });

  it('error를 파싱한다 (sessionId null — 연결 수립 실패 등 세션에 속하지 않는 오류)', () => {
    const frame = parseFrame({
      type: 'error',
      sessionId: null,
      code: 'UNAUTHENTICATED',
      message: '인증이 필요합니다.',
      traceId: 't1',
    });
    expect(frame?.type).toBe('error');
    if (frame?.type === 'error') {
      expect(frame.sessionId).toBeNull();
    }
  });

  it('chat.citation을 파싱한다', () => {
    const frame = parseFrame({
      type: 'chat.citation',
      sessionId: 's1',
      citations: [{ docId: 'd1', title: '제목', snippet: '발췌', score: 0.9 }],
      restrictedResultsOmitted: false,
    });
    expect(frame).toEqual({
      type: 'chat.citation',
      sessionId: 's1',
      citations: [{ docId: 'd1', title: '제목', snippet: '발췌', score: 0.9 }],
      restrictedResultsOmitted: false,
    });
  });

  it('알 수 없는 type은 null (미래의 presence.leave 등에 대비)', () => {
    expect(
      parseFrame({ type: 'presence.leave', sessionId: 's1', userId: 'u1' }),
    ).toBeNull();
  });

  it('필드가 빠진 known type은 null', () => {
    expect(parseFrame({ type: 'chat.token', sessionId: 's1' })).toBeNull();
  });

  it('필드 타입이 안 맞으면 null', () => {
    expect(
      parseFrame({ type: 'chat.token', sessionId: 's1', delta: 123 }),
    ).toBeNull();
  });

  it('객체가 아니면 null', () => {
    expect(parseFrame('not an object')).toBeNull();
    expect(parseFrame(42)).toBeNull();
    expect(parseFrame(null)).toBeNull();
    expect(parseFrame(undefined)).toBeNull();
    expect(parseFrame(['type', 'chat.token'])).toBeNull();
  });

  it('type 필드 자체가 없으면 null', () => {
    expect(parseFrame({ sessionId: 's1', delta: 'x' })).toBeNull();
  });
});

describe('parseFrameFromText', () => {
  it('유효한 JSON 문자열을 파싱한다', () => {
    const frame = parseFrameFromText('{"type":"chat.done","sessionId":"s1"}');
    expect(frame).toEqual({ type: 'chat.done', sessionId: 's1' });
  });

  it('깨진 JSON은 예외를 던지지 않고 null을 반환한다', () => {
    expect(parseFrameFromText('{"type":"chat.done"')).toBeNull();
    expect(parseFrameFromText('')).toBeNull();
  });

  it('유효한 JSON이지만 알려진 프레임이 아니면 null', () => {
    expect(parseFrameFromText('{"hello":"world"}')).toBeNull();
  });
});
