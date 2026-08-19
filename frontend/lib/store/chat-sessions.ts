/********************************************************
 파일명 : chat-sessions.ts (lib/store)
 설 명 : 다중 채팅 세션 상태 스토어. 세션 목록·현재 세션·세션별 메시지를 zustand로 관리하고
 localStorage로 지속한다. isLoading(스트리밍 여부)은 useChat이 컴포넌트 로컬로 관리하므로
 여긴 두지 않는다 — 여러 세션 동시 백그라운드 스트리밍은 스코프 밖.
 failedMessageIds는 재로그인 리다이렉트 후에도 "전송 실패 + 재전송" 표시가 남아야 해서 이 스토어에 둔다.
 하이드레이션: 서버 평가 시점엔 localStorage가 없어 skipHydration으로 자동 복원을 끄고,
 클라이언트 마운트 후 useChatSessionsHydrated가 명시적으로 복원한다.
 *********************************************************/

import type { Message } from 'ai';
import { useEffect, useState } from 'react';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

/** setSessions 입력 — 서버(GET /api/chat/sessions) 응답 항목 모양(lib/api/server-history.ts와 동일). */
export interface ServerChatSession {
  id: string;
  title: string;
  createdAt: string;
}

export interface ChatSession {
  id: string;
  title: string;
  modelId: string;
  messages: Message[];
  createdAt: number;
  failedMessageIds: string[];
  /** true면 renameSession으로 사용자가 직접 정한 제목 — 자동 파생·서버 동기화가 덮어쓰지 않는다. */
  titleCustomized: boolean;
}

const DEFAULT_TITLE = '새 대화';
/** 세션 탭 제목 최대 길이. 자동 파생(deriveTitle)과 사용자 수동 변경(renameSession) 둘 다 이 값으로 자른다. */
export const TITLE_MAX_LENGTH = 40;

/** failedMessageIds 폴백용 고정 참조 — 매번 새 배열(`?? []`)을 셀렉터에서 반환하면
 * zustand가 "바뀐 값"으로 보고 매 렌더 재구독을 트리거해 무한 리렌더로 이어진다. */
export const EMPTY_FAILED_MESSAGE_IDS: string[] = [];

/** 첫 user 메시지로 세션 탭 제목을 만든다. 없으면 기본 제목, TITLE_MAX_LENGTH 초과 시 말줄임표로 자른다. */
export function deriveTitle(messages: Message[]): string {
  const firstUserMessage = messages.find((message) => message.role === 'user');
  if (!firstUserMessage?.content) return DEFAULT_TITLE;

  const trimmed = firstUserMessage.content.trim();
  return trimmed.length > TITLE_MAX_LENGTH
    ? `${trimmed.slice(0, TITLE_MAX_LENGTH)}…`
    : trimmed;
}

/** 세션 삭제 후 currentSessionId를 정한다. 현재 세션이 지워진 경우에만 남은 세션 중 마지막으로
 * 전환한다(없으면 null → 새 대화 화면). */
export function pickNextSessionId(
  sessions: ChatSession[],
  deletedId: string,
  currentSessionId: string | null,
): string | null {
  if (currentSessionId !== deletedId) return currentSessionId;
  const remaining = sessions.filter((session) => session.id !== deletedId);
  return remaining.at(-1)?.id ?? null;
}

interface ChatSessionsState {
  sessions: ChatSession[];
  currentSessionId: string | null;
  createSession: (params: { id: string; modelId: string }) => void;
  switchSession: (id: string) => void;
  deleteSession: (id: string) => void;
  setSessionMessages: (id: string, messages: Message[]) => void;
  clearCurrentSession: () => void;
  setSessions: (serverSessions: ServerChatSession[]) => void;
  markMessageFailed: (sessionId: string, messageId: string) => void;
  clearMessageFailed: (sessionId: string, messageId: string) => void;
  renameSession: (id: string, title: string) => void;
}

export const useChatSessionsStore = create<ChatSessionsState>()(
  persist(
    (set) => ({
      sessions: [],
      currentSessionId: null,

      createSession: ({ id, modelId }) =>
        set((state) => ({
          sessions: [
            ...state.sessions,
            {
              id,
              title: DEFAULT_TITLE,
              modelId,
              messages: [],
              createdAt: Date.now(),
              failedMessageIds: [],
              titleCustomized: false,
            },
          ],
          currentSessionId: id,
        })),

      switchSession: (id) =>
        set((state) =>
          state.sessions.some((session) => session.id === id)
            ? { currentSessionId: id }
            : state,
        ),

      // draft 세션("/" 새 진입)으로 이동 시 사이드바 활성 하이라이트만 해제한다.
      clearCurrentSession: () => set({ currentSessionId: null }),

      deleteSession: (id) =>
        set((state) => ({
          sessions: state.sessions.filter((session) => session.id !== id),
          currentSessionId: pickNextSessionId(
            state.sessions,
            id,
            state.currentSessionId,
          ),
        })),

      // 제목은 첫 user 메시지 도착 시 한 번만 확정한다(계속 바뀌면 탭을 못 찾는 UX가 됨).
      // titleCustomized면 사용자가 직접 정한 제목이라 손대지 않는다.
      setSessionMessages: (id, messages) =>
        set((state) => ({
          sessions: state.sessions.map((session) =>
            session.id === id
              ? {
                  ...session,
                  messages,
                  title:
                    !session.titleCustomized && session.title === DEFAULT_TITLE
                      ? deriveTitle(messages)
                      : session.title,
                }
              : session,
          ),
        })),

      // 서버가 진실의 원천이라 서버 목록에 없는 로컬 세션은 제거한다. messages는 서버 응답에
      // 없으므로(메타데이터만) 로컬에 이미 로드돼 있으면 보존한다. title도 titleCustomized면
      // 로컬 값을 지킨다 — 그러지 않으면 PATCH 실패 시 새로고침마다 이름이 되돌아간다.
      setSessions: (serverSessions) =>
        set((state) => {
          const localById = new Map(
            state.sessions.map((session) => [session.id, session]),
          );
          return {
            sessions: serverSessions.map((server) => {
              const local = localById.get(server.id);
              return {
                id: server.id,
                title: local?.titleCustomized ? local.title : server.title,
                // 서버 응답에는 모델이 없다. 로컬 기록이 없으면 비워둔다 — 이 값은 기록용이고,
                // 실제 전송에 쓰이는 모델은 화면이 page.tsx에서 받은 selectedModelId다.
                modelId: local?.modelId ?? '',
                messages: local?.messages ?? [],
                createdAt: new Date(server.createdAt).getTime(),
                failedMessageIds: local?.failedMessageIds ?? [],
                titleCustomized: local?.titleCustomized ?? false,
              };
            }),
          };
        }),

      // 재로그인이 강제된 요청의 마지막 user 메시지를 "전송 실패"로 표시한다(중복 추가 방지).
      markMessageFailed: (sessionId, messageId) =>
        set((state) => ({
          sessions: state.sessions.map((session) => {
            if (session.id !== sessionId) return session;
            // 이 필드 도입 전 저장된 로컬 세션엔 없을 수 있다.
            const failedMessageIds = session.failedMessageIds ?? [];
            return failedMessageIds.includes(messageId)
              ? session
              : {
                  ...session,
                  failedMessageIds: [...failedMessageIds, messageId],
                };
          }),
        })),

      // 재전송 시도 시 낙관적으로 실패 표시를 지운다. 다시 재로그인 강제로 실패하면 onError가
      // 재표시한다 — 스트림절단·일반에러로 실패하는 경우엔 토스트의 "다시 시도"가 재시도를 맡는다.
      clearMessageFailed: (sessionId, messageId) =>
        set((state) => ({
          sessions: state.sessions.map((session) =>
            session.id === sessionId
              ? {
                  ...session,
                  failedMessageIds: (session.failedMessageIds ?? []).filter(
                    (id) => id !== messageId,
                  ),
                }
              : session,
          ),
        })),

      // 빈 문자열(공백만 입력)은 무시하고 기존 제목을 유지한다 — 빈 탭 이름은 클릭 대상을 찾을 수 없게 만든다.
      renameSession: (id, title) =>
        set((state) => {
          const trimmed = title.trim().slice(0, TITLE_MAX_LENGTH);
          if (!trimmed) return state;
          return {
            sessions: state.sessions.map((session) =>
              session.id === id
                ? { ...session, title: trimmed, titleCustomized: true }
                : session,
            ),
          };
        }),
    }),
    {
      name: 'chat-sessions',
      storage: createJSONStorage(() => localStorage),
      skipHydration: true,
    },
  ),
);

/**
 * localStorage 복원 완료 여부. true 전에 세션 생성/전환을 판정하면 빈 스토어를 보고 매번
 * 새 세션을 만들어버리므로, 호출부(Chat)는 이 값으로 채팅 UI 마운트를 게이트해야 한다.
 * SSR엔 localStorage가 없어 `persist`가 아예 안 붙을 수 있다 — 초기값 조회는 옵셔널 체이닝으로 방어한다.
 */
export function useChatSessionsHydrated(): boolean {
  const [hydrated, setHydrated] = useState(
    () => useChatSessionsStore.persist?.hasHydrated() ?? false,
  );

  useEffect(() => {
    if (hydrated) return;
    const unsubscribe = useChatSessionsStore.persist.onFinishHydration(() =>
      setHydrated(true),
    );
    useChatSessionsStore.persist.rehydrate();
    return unsubscribe;
  }, [hydrated]);

  return hydrated;
}
