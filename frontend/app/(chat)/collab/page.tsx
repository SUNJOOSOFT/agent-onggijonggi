/********************************************************
 파일명 : page.tsx (app/(chat)/collab)
 설 명 : 협업 채널 목록 페이지(이슈 #19). 목록 조회는 ThreadList가 클라이언트에서 한다
 (lib/api/collab.ts 주석).

 (chat) 라우트 그룹 안에 둔다 — 1:1 채팅과 같은 사이드바에서 오가는 화면이기 때문이다.
 원래는 그룹 밖에 뒀었다. 그 레이아웃이 서버에서 BFF를 부르는데 목업 모드에서는 base URL이
 비어 상대주소가 되고 Node의 fetch가 그것을 파싱하지 못해 레이아웃째 죽었기 때문인데,
 config.ts의 SERVER_BFF_BASE_URL이 목업 모드에서 Next 자기 주소로 되돌아가게 고치면서 그
 제약이 없어졌다.
 *********************************************************/

import { ThreadList } from '@/components/collab/thread-list';

export default function Page() {
  return <ThreadList />;
}
