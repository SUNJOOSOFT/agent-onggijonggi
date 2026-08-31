/********************************************************
 파일명 : page.tsx (app/(chat)/collab/[threadId])
 설 명 : 협업방 입장 페이지(이슈 #19). URL의 threadId로 방 화면을 연다.
 방 제목은 CollabRoom이 클라이언트에서 채운다 — 서버 조회는 목업 모드에서 쓸 수 없다.
 *********************************************************/

import { CollabRoom } from '@/components/collab/collab-room';

export default async function Page({
  params,
}: {
  params: Promise<{ threadId: string }>;
}) {
  const { threadId } = await params;

  return <CollabRoom key={threadId} threadId={threadId} />;
}
