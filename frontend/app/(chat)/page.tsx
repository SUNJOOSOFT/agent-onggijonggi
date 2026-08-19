/********************************************************
 파일명 : page.tsx (app/(chat))
 설 명 : "새 대화" 진입점. 매 진입마다 새 세션 id를 생성해 Chat을 렌더링한다(기존 세션을 여는
 chat/[id]/page.tsx와 역할이 다르다).
 *********************************************************/

import { cookies } from 'next/headers';

import { Chat } from '@/components/chat';
import { resolveSelectedModelId } from '@/lib/ai/models';
import { fetchModelsForServer } from '@/lib/api/server-history';
import { generateUUID } from '@/lib/utils';

/** 새 세션 id를 생성하고, 게이트웨이의 모델 목록과 쿠키에 저장된 마지막 선택으로 Chat을 렌더링한다. */
export default async function Page() {
  const id = generateUUID();

  const cookieStore = await cookies();
  const modelIdFromCookie = cookieStore.get('model-id')?.value;

  const availableModels = await fetchModelsForServer();
  const selectedModelId = resolveSelectedModelId(
    availableModels,
    modelIdFromCookie,
  );

  return (
    <>
      <Chat
        key={id}
        id={id}
        availableModels={availableModels}
        selectedModelId={selectedModelId}
        serverMessages={[]}
      />
    </>
  );
}
