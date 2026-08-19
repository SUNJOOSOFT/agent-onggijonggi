/********************************************************
 파일명 : page.tsx (app/(chat)/chat/[id])
 설 명 : URL의 세션 id로 특정 세션 화면을 여는 페이지. 세션 탭(app-sidebar.tsx) 클릭 시 이 라우트로 이동한다.
 *********************************************************/

import { cookies } from 'next/headers';

import { Chat } from '@/components/chat';
import { resolveSelectedModelId } from '@/lib/ai/models';
import {
  fetchModelsForServer,
  fetchSessionMessagesForServer,
} from '@/lib/api/server-history';

/** 게이트웨이의 모델 목록과 쿠키에 저장된 마지막 선택을 복원하고, URL의 세션 id로 Chat을 렌더링한다. */
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  const cookieStore = await cookies();
  const modelIdFromCookie = cookieStore.get('model-id')?.value;
  const availableModels = await fetchModelsForServer();
  const selectedModelId = resolveSelectedModelId(
    availableModels,
    modelIdFromCookie,
  );
  const serverMessages = await fetchSessionMessagesForServer(id);

  return (
    <>
      <Chat
        key={id}
        id={id}
        availableModels={availableModels}
        selectedModelId={selectedModelId}
        serverMessages={serverMessages ?? []}
      />
    </>
  );
}
