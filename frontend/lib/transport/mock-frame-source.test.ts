import { describe, expect, it } from 'vitest';
import { parseFrameFromText } from './parse-frame';
import {
  errorMidStreamFrames,
  goldenPathFrames,
  mockFrameSource,
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
    // 나머지가 아직 안 왔는지까지는 마이크로태스크 큐 특성상 엄격히 보장하기 어려우므로,
    // "첫 값이 낱개로 온다"는 계약만 확인한다.
  });
});

describe('시나리오 헬퍼', () => {
  it('goldenPathFrames: citation이 먼저, 이어서 token들, 마지막에 done', () => {
    const frames = goldenPathFrames({ tokens: ['한', '글'] });
    expect(frames.map((f) => f.type)).toEqual([
      'chat.citation',
      'chat.token',
      'chat.token',
      'chat.done',
    ]);
  });

  it('tokensOnlyFrames: citation 프레임이 아예 없다', () => {
    const frames = tokensOnlyFrames({ tokens: ['한', '글'] });
    expect(frames.map((f) => f.type)).toEqual([
      'chat.token',
      'chat.token',
      'chat.done',
    ]);
    expect(frames.some((f) => f.type === 'chat.citation')).toBe(false);
  });

  it('errorMidStreamFrames: 토큰 일부 후 error로 끝나고 chat.done이 없다', () => {
    const frames = errorMidStreamFrames({ tokensBeforeError: ['한'] });
    expect(frames.map((f) => f.type)).toEqual(['chat.token', 'error']);
    expect(frames.some((f) => f.type === 'chat.done')).toBe(false);
  });
});
