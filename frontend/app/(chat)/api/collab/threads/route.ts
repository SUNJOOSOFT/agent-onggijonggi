/********************************************************
 파일명 : route.ts (app/(chat)/api/collab/threads)
 설 명 : [MOCK] 협업 채널 목록 목업(이슈 #19). 실 BFF(NEXT_PUBLIC_BFF_BASE_URL) 설정 시 우회된다.

 라우트 그룹은 URL에 나타나지 않으므로 (chat) 안에 있어도 실제 경로는 /api/collab/threads
 그대로다 — 1:1 채팅의 목업 라우트와 같은 자리에 뒀다.

 목록에 접근 거부용 방을 섞어 둔 것은 의도다. 인가 실패를 핸드셰이크 거부로 줄지 error 프레임으로
 줄지가 #22에 미결이라 화면은 두 경우를 다 처리해야 하고, 목업 WS 서버(#33)가 이 두 threadId를
 예약어로 읽어 각각을 재현하기로 돼 있다.
 *********************************************************/

import { isMockMode } from '@/lib/api/config';

export const runtime = 'nodejs';

/** 목업 방 세 개 — 정상 방 하나와, 거부를 두 방식으로 재현하는 방 둘. */
const THREADS = [
  {
    id: 'mock-thread',
    title: '계약 검토 협업방',
    participants: ['sujin', 'minho'],
  },
  {
    id: 'forbidden-close',
    title: '임원 회의록 검토(핸드셰이크 거부)',
    participants: ['ceo'],
  },
  {
    id: 'forbidden-frame',
    title: '인사 평가 논의(error 프레임 거부)',
    participants: ['hr-lead'],
  },
];

export async function GET() {
  if (!isMockMode()) {
    return new Response('Mock disabled: real BFF is configured', {
      status: 404,
    });
  }

  return Response.json(THREADS);
}
