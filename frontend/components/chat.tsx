'use client';

/********************************************************
 파일명 : chat.tsx
 설 명 : 채팅 화면의 핵심 배선(useChat 훅 설정)과 세션 스토어 연동. Chat은 localStorage 하이드레이션이
 끝날 때까지 로딩만 보여주는 게이트고, 실제 로직(BFF URL·스트림 프로토콜·인증 fetch·요청 바디·
 에러 처리)은 ChatSession에 있다.
 *********************************************************/

import type { Message } from 'ai';
import { useChat } from 'ai/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';

import { ChatHeader } from '@/components/chat-header';
import { LoaderIcon } from '@/components/icons';
import {
  CHAT_STREAM_URL,
  buildChatRequestBody,
  fetchCitations,
} from '@/lib/api/chat';
import {
  STREAM_TRUNCATED_MESSAGE,
  isStreamTruncated,
  resolveChatError,
} from '@/lib/api/errors';
import { createAuthFetchWithReauthSignal } from '@/lib/api/http';
import type { ChatMsgItem } from '@/lib/api/server-history';
import {
  EMPTY_FAILED_MESSAGE_IDS,
  useChatSessionsHydrated,
  useChatSessionsStore,
} from '@/lib/store/chat-sessions';
import type { CitationsState } from './citations-panel';
import { Messages } from './messages';
import { MultimodalInput } from './multimodal-input';

/** localStorage 복원 전엔 빈 스토어를 보고 매번 새 세션을 만들어버리므로, 복원이 끝날 때까지
 * 로딩 스피너만 보여주는 게이트. serverMessages는 로컬에 없을 때만 폴백으로 쓰인다. */
export function Chat({
  id,
  availableModels,
  selectedModelId,
  serverMessages,
}: {
  id: string;
  availableModels: string[];
  selectedModelId: string;
  serverMessages: ChatMsgItem[];
}) {
  const hydrated = useChatSessionsHydrated();

  if (!hydrated) {
    return (
      <div className="flex flex-col min-w-0 h-dvh bg-background items-center justify-center">
        <div className="animate-spin text-muted-foreground">
          <LoaderIcon />
        </div>
      </div>
    );
  }

  return (
    <ChatSession
      id={id}
      availableModels={availableModels}
      selectedModelId={selectedModelId}
      serverMessages={serverMessages}
    />
  );
}

/** useChat 훅을 계약에 맞게 배선하고, 세션 스토어 등록·근거 인용 병렬 조회·스트림 중단 감지·
 * 로그인 만료 시 전송 실패 표시를 담당한다. Chat이 id별로 리마운트하므로(page.tsx의 key={id})
 * 세션 전환 시 상태가 자연히 초기화된다. */
function ChatSession({
  id,
  availableModels,
  selectedModelId,
  serverMessages,
}: {
  id: string;
  availableModels: string[];
  selectedModelId: string;
  serverMessages: ChatMsgItem[];
}) {
  // reload·messages를 onError 클로저에서 바로 참조하면 선언 전 사용(TDZ)이 되므로 ref로 가리킨다.
  const reloadRef = useRef<(() => void) | null>(null);
  const messagesRef = useRef<Message[]>([]);

  // authFetch가 재로그인을 강제한 요청인지 onError가 구분하기 위한 플래그. 이 useChat
  // 인스턴스 전용 fetch에만 물려서 인용 조회(별도 authFetch 사용)와 신호가 섞이지 않는다.
  const forcedReauthRef = useRef(false);
  const chatFetch = useMemo(
    () =>
      createAuthFetchWithReauthSignal(() => {
        forcedReauthRef.current = true;
      }),
    [],
  );

  // useChat은 initialMessages를 최초 마운트 시점에만 반영하므로 지연 초기화로 한 번만 읽는다.
  // 로컬에 메시지가 없으면(다른 기기·새 브라우저 등) serverMessages로 폴백한다.
  const [initialMessages] = useState<Message[] | undefined>(() => {
    const local = useChatSessionsStore
      .getState()
      .sessions.find((session) => session.id === id)?.messages;
    if (local && local.length > 0) return local;
    if (serverMessages.length === 0) return local;
    return serverMessages.map((item) => ({
      id: item.id,
      role: item.role as Message['role'],
      content: item.content,
    }));
  });

  // 스토어에 없는 id(draft, "/" 새 진입)면 세션을 만들지 않고 활성 하이라이트만 해제한다 —
  // 여기서 무조건 생성하면 새로고침마다 빈 대화가 쌓인다(실제 생성은 handleChatSubmit).
  useEffect(() => {
    const { sessions, switchSession, clearCurrentSession } =
      useChatSessionsStore.getState();
    if (sessions.some((session) => session.id === id)) {
      switchSession(id);
    } else {
      clearCurrentSession();
    }
  }, [id, selectedModelId]);

  const {
    messages,
    setMessages,
    handleSubmit,
    input,
    setInput,
    append,
    isLoading,
    stop,
    reload,
  } = useChat({
    id,
    initialMessages,
    // 목업 모드에선 동일 오리진 라우트로 우회된다.
    api: CHAT_STREAM_URL,
    // 프레이밍 없는 raw 텍스트 스트림 소비.
    streamProtocol: 'text',
    fetch: chatFetch,
    experimental_prepareRequestBody: ({ messages }) =>
      buildChatRequestBody({
        sessionId: id,
        modelId: selectedModelId,
        messages,
      }),
    experimental_throttle: 100,
    // 401은 authFetch에서 이미 리프레시·재로그인 처리되므로, 여기 도달하는 건 주로
    // 400/403/429/5xx와 스트림 중단이다. 중단·오류엔 재생성(reload)을 제안한다.
    onError: (error) => {
      // 재로그인이 강제된 요청은 응답을 받을 수 없다 — keepLastMessageOnError(기본값)가
      // 이미 질문을 messages에 남겨두므로 여기선 "전송 실패" 표시만 얹는다. 로그인 화면으로
      // 넘어가는 중이라 토스트는 띄우지 않는다.
      if (forcedReauthRef.current) {
        forcedReauthRef.current = false;
        const lastMessage = messagesRef.current.at(-1);
        if (lastMessage?.role === 'user') {
          useChatSessionsStore.getState().markMessageFailed(id, lastMessage.id);
        }
        return;
      }

      // duration: Infinity — 기본 지속시간 후 토스트가 사라지면 "다시 시도" 버튼도 함께
      // 사라져 재시도할 방법이 없어진다. cancel(닫기)로 재시도 없이도 넘어갈 수 있게 한다.
      if (isStreamTruncated(messagesRef.current.at(-1))) {
        toast.error(STREAM_TRUNCATED_MESSAGE, {
          duration: Number.POSITIVE_INFINITY,
          action: { label: '다시 시도', onClick: () => reloadRef.current?.() },
          cancel: { label: '닫기', onClick: () => {} },
        });
        return;
      }

      const { message, traceId } = resolveChatError(error);
      if (traceId) {
        console.error(`[chat] BFF error traceId=${traceId}`);
      }
      toast.error(message, {
        duration: Number.POSITIVE_INFINITY,
        action: { label: '다시 시도', onClick: () => reloadRef.current?.() },
        cancel: { label: '닫기', onClick: () => {} },
      });
    },
  });

  reloadRef.current = reload;
  messagesRef.current = messages;

  useEffect(() => {
    useChatSessionsStore.getState().setSessionMessages(id, messages);
  }, [id, messages]);

  // EMPTY_FAILED_MESSAGE_IDS는 고정 참조 — 매번 새 배열을 반환하면 zustand가 값이 바뀐
  // 것으로 보고 무한 리렌더로 이어진다.
  const failedMessageIds = useChatSessionsStore(
    (state) =>
      state.sessions.find((session) => session.id === id)?.failedMessageIds ??
      EMPTY_FAILED_MESSAGE_IDS,
  );

  // reload()는 마지막 메시지가 user면 그대로 재전송한다 — 실패 메시지가 항상 마지막이므로 충분.
  const handleResendFailedMessage = useCallback(
    (messageId: string) => {
      useChatSessionsStore.getState().clearMessageFailed(id, messageId);
      reload();
    },
    [id, reload],
  );

  // 새 user 메시지가 오면 채팅 스트림과 별도로 근거 인용을 조회한다. ref로 이미 요청한
  // 메시지 id를 추적해(state면 effect 의존성에 넣어야 해 순환·중복요청 우려) 한 번만 쏜다.
  const [citationsByMessageId, setCitationsByMessageId] = useState<
    Record<string, CitationsState>
  >({});
  const requestedCitationsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    const lastMessage = messages.at(-1);
    if (!lastMessage || lastMessage.role !== 'user') return;
    if (requestedCitationsRef.current.has(lastMessage.id)) return;
    requestedCitationsRef.current.add(lastMessage.id);

    setCitationsByMessageId((prev) => ({
      ...prev,
      [lastMessage.id]: { status: 'loading' },
    }));

    fetchCitations({
      sessionId: id,
      query: lastMessage.content,
      modelId: selectedModelId,
    })
      .then(({ citations, restrictedResultsOmitted }) => {
        setCitationsByMessageId((prev) => ({
          ...prev,
          [lastMessage.id]: {
            status: 'success',
            citations,
            restrictedResultsOmitted,
          },
        }));
      })
      .catch((error: Error) => {
        const { message } = resolveChatError(error);
        setCitationsByMessageId((prev) => ({
          ...prev,
          [lastMessage.id]: { status: 'error', errorMessage: message },
        }));
      });
  }, [messages, id, selectedModelId]);

  // 세션은 첫 메시지를 실제로 보낼 때만 스토어에 만든다(draft 화면 새로고침으로 빈 세션이 쌓이지 않도록).
  const handleChatSubmit = useCallback<typeof handleSubmit>(
    (event, options) => {
      const { sessions, createSession } = useChatSessionsStore.getState();
      if (!sessions.some((session) => session.id === id)) {
        createSession({ id, modelId: selectedModelId });
      }
      handleSubmit(event, options);
    },
    [handleSubmit, id, selectedModelId],
  );

  return (
    <div className="flex flex-col min-w-0 h-dvh bg-background">
      <ChatHeader
        availableModels={availableModels}
        selectedModelId={selectedModelId}
      />

      <Messages
        chatId={id}
        isLoading={isLoading}
        messages={messages}
        citationsByMessageId={citationsByMessageId}
        failedMessageIds={failedMessageIds}
        onResendFailedMessage={handleResendFailedMessage}
        setMessages={setMessages}
        reload={reload}
      />

      <form className="flex mx-auto px-4 bg-background pb-4 md:pb-6 gap-2 w-full md:max-w-3xl">
        <MultimodalInput
          chatId={id}
          input={input}
          setInput={setInput}
          handleSubmit={handleChatSubmit}
          isLoading={isLoading}
          stop={stop}
          messages={messages}
          setMessages={setMessages}
          append={append}
        />
      </form>
    </div>
  );
}
