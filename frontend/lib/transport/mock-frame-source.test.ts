import { describe, expect, it } from 'vitest';
import { parseFrameFromText } from './parse-frame';
import {
  errorMidStreamFrames,
  goldenPathFrames,
  mockFrameSource,
  restrictedCitationsFrames,
  tokensOnlyFrames,
} from './mock-frame-source';

/** 소스가 내보낸 문자열이 실제로 parseFrameFromText를 통과하는지까지 확인한다 — 시나리오
 * 헬퍼가 만드는 객체와 parse-frame.ts의 스키마가 어긋나면 여기서 바로 드러난다. */
async function collect(source: AsyncIterable<string>) {
  const texts: string[] = [];
  for await (const text of source) texts.push(text);
  return texts.map((text) => parseFrameFromText(text));
}

describe('mockFrameSource', () => {
  it('프레임 배열을 순서대로 JSON 문자열로 내보낸다', async () => {
    const frames = goldenPathFrames({ tokens: ['a', 'b'] });
    const parsed = await collect(mockFrameSource(frames));
    expect(parsed).toEqual(frames);
  });

  it('비동기적으로 하나씩 온다(한 번에 배열째로 오지 않는다)', async () => {
    const frames = tokensOnlyFrames({ tokens: ['x', 'y', 'z'] });
    const iterator = mockFrameSource(frames)[Symbol.asyncIterator]();
    const first = await iterator.next();
    expect(first.done).toBe(false);
    expect(parseFrameFromText(first.value)).toEqual(frames[0]);
  });
});

describe('시나리오 헬퍼', () => {
  it('goldenPathFrames: citation이 delta 없이 먼저, 이어서 token들, 마지막에 status:done', () => {
    const frames = goldenPathFrames({ tokens: ['한', '글'] });
    expect(
      frames.map((f) => (f.type === 'chat.answer' ? f.status : f.type)),
    ).toEqual(['streaming', 'streaming', 'streaming', 'done']);
    const first = frames[0];
    expect(first.type === 'chat.answer' && first.citations.length > 0).toBe(
      true,
    );
    expect(first.type === 'chat.answer' && first.delta).toBe('');
    const last = frames.at(-1);
    expect(last?.type === 'chat.answer' && last.status).toBe('done');
  });

  it('tokensOnlyFrames: 모든 패킷의 citations가 빈 배열이다', () => {
    const frames = tokensOnlyFrames({ tokens: ['한', '글'] });
    expect(
      frames.every((f) => f.type === 'chat.answer' && f.citations.length === 0),
    ).toBe(true);
  });

  it('errorMidStreamFrames: 토큰 일부 후 error로 끝나고 status:done 패킷이 없다', () => {
    const frames = errorMidStreamFrames({ tokensBeforeError: ['한'] });
    expect(frames.map((f) => f.type)).toEqual(['chat.answer', 'error']);
    expect(
      frames.some((f) => f.type === 'chat.answer' && f.status === 'done'),
    ).toBe(false);
  });

  it('restrictedCitationsFrames: citations는 빈 배열이지만 restrictedResultsOmitted는 true', () => {
    const frames = restrictedCitationsFrames({ tokens: ['한'] });
    const first = frames[0];
    expect(first.type === 'chat.answer' && first.citations).toEqual([]);
    expect(first.type === 'chat.answer' && first.restrictedResultsOmitted).toBe(
      true,
    );
  });
});
