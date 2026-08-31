import { describe, expect, it } from 'vitest';
import { mentionsAi } from './mention';

describe('mentionsAi', () => {
  it('대소문자를 가리지 않고 잡는다', () => {
    expect(mentionsAi('@AI 이 조항 알려줘')).toBe(true);
    expect(mentionsAi('@ai 이 조항 알려줘')).toBe(true);
    expect(mentionsAi('문장 중간에 @Ai 넣어도 된다')).toBe(true);
  });

  it('멘션이 없는 참여자 간 대화는 거짓이다', () => {
    expect(mentionsAi('오늘 회의 몇 시죠')).toBe(false);
    expect(mentionsAi('')).toBe(false);
    expect(mentionsAi('메일 주소는 ai@example.com 입니다')).toBe(false);
  });

  it('단어가 이어지면 멘션이 아니지만, 한글이 붙는 건 멘션이다', () => {
    expect(mentionsAi('@AIssistant 에게 물어봐')).toBe(false);
    expect(mentionsAi('@AI야 이거 뭐야')).toBe(true);
  });
});
