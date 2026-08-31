/********************************************************
 파일명 : ws-server.ts (mocks)
 설 명 : 협업채팅 목업 WS 서버(이슈 #33). `bun run mock:ws`로 띄운다.

 왜 Next Route Handler가 아닌 별도 프로세스인가 — App Router의 Route Handler는
 `Request → Response` 모델이라 raw socket에 닿지 못해 WebSocket upgrade를 할 수 없다. 그래서
 기존 HTTP 목업(`app/(chat)/api/chat/*`)을 WS로 "교체"하는 대신, WS만 이 프로세스가 맡고
 HTTP 목업은 그대로 둔다.

 실서버 대신 이 프로세스를 보게 하려면 `NEXT_PUBLIC_MOCK_WS_URL`을 채운다(config.ts 참고).
 인증은 검증하지 않는다 — 서브프로토콜로 토큰이 오는 모양(#3)만 맞추고, 값은 사용자 이름을
 뽑는 데만 쓴다.

 재현하는 프레임은 #8 계약 그대로다(`lib/transport/frames.ts`가 그 미러):
   presence.join  — 입장. 퇴장 대칭 프레임은 #8에 아직 없어(#25 논의 중) 보내지 않는다.
   chat.message   — 참여자 메시지. 방 전원에게 방송만 한다.
   chat.answer    — `@AI` 멘션이 있을 때만 흐르는 답변 스트림(#13·#17 정책).
   error          — 깨진 프레임, 그리고 방 접근 거부(rooms.ts의 두 방식 중 하나).
 *********************************************************/

import type { WsErrorFrame, WsFrame } from '@/lib/transport/frames';
import { goldenPathFrames } from '@/lib/transport/mock-frame-source';
import { parseFrameFromText } from '@/lib/transport/parse-frame';
import {
  PROTOCOL_NAME,
  bearerFromSubProtocol,
  userIdFromToken,
} from './handshake';
import {
  DEFAULT_THREAD_ID,
  MockRoomRegistry,
  type RoomMember,
  mentionsAi,
  roomAccess,
} from './rooms';

/** 서버 WsHandlerMappingConfig의 매핑·config.ts의 WS_PATH와 같아야 한다(이슈 #3). */
const WS_PATH = '/api/ws';

/** 3000(next dev)·8090(BFF)과 겹치지 않는 자리. */
const PORT = Number(process.env.MOCK_WS_PORT ?? 4001);

/** 토큰 사이 간격. HTTP 목업(app/(chat)/api/chat/stream/route.ts)과 같은 값으로 맞춘다. */
const TOKEN_INTERVAL_MS = 40;

const registry = new MockRoomRegistry();
let connectionSeq = 0;

interface SocketData {
  connectionId: string;
  threadId: string;
  userId: string;
  /** true면 open 직후 error 프레임을 보내고 닫는다(방 접근 거부의 "프레임" 방식). */
  denyOnOpen: boolean;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function errorFrame(
  sessionId: string | null,
  code: string,
  message: string,
): WsErrorFrame {
  return { type: 'error', sessionId, code, message, traceId: `mock-${code}` };
}

function presenceJoin(threadId: string, userId: string): WsFrame {
  return { type: 'presence.join', sessionId: threadId, userId };
}

/**
 * `@AI` 멘션에 대한 답변을 방 전원에게 흘린다. goldenPathFrames를 그대로 쓰므로 근거가
 * 먼저(delta 없이) 도착하고 토큰이 이어지는 순서까지 실제 계약과 같다.
 */
async function streamAiAnswer(
  threadId: string,
  question: string,
): Promise<void> {
  const reply = `「목업 응답」 @AI 호출을 받았습니다: "${question}". 이 답변은 목업 WS 서버가 #8 프레임 계약대로 흘려보낸 chat.answer 스트림입니다.`;
  const tokens = reply.split(/(\s+)/).filter((chunk) => chunk.length > 0);

  for (const frame of goldenPathFrames({ sessionId: threadId, tokens })) {
    registry.broadcast(threadId, frame);
    await sleep(TOKEN_INTERVAL_MS);
  }
}

const server = Bun.serve<SocketData>({
  port: PORT,
  fetch(request, server) {
    const url = new URL(request.url);
    // 실서버(#16 PR #77)가 `/api/ws/{threadId}`로 매핑하므로 같은 모양으로 읽는다. 방 없이
    // `/api/ws`로 붙는 것도 받아 주는데, 개발 중 URL을 매번 적지 않아도 되게 한 편의다.
    if (url.pathname !== WS_PATH && !url.pathname.startsWith(`${WS_PATH}/`)) {
      return new Response('Not Found', { status: 404 });
    }

    const token = bearerFromSubProtocol(
      request.headers.get('Sec-WebSocket-Protocol'),
    );
    if (token === null) {
      return new Response('Unauthorized', { status: 401 });
    }

    const threadId =
      decodeURIComponent(url.pathname.slice(WS_PATH.length + 1)) ||
      DEFAULT_THREAD_ID;
    const access = roomAccess(threadId);
    // 핸드셰이크 자체를 거부하면 브라우저에는 status도 body도 닿지 않고 1006으로만 온다
    // (PR #68). 화면이 그 상황을 어떻게 다루는지 보려고 남겨 둔 시나리오다.
    if (access === 'deny-handshake') {
      return new Response('Forbidden', { status: 403 });
    }

    connectionSeq += 1;
    const data: SocketData = {
      connectionId: `c${connectionSeq}`,
      threadId,
      userId:
        url.searchParams.get('user') ??
        userIdFromToken(token) ??
        `user-${connectionSeq}`,
      denyOnOpen: access === 'deny-frame',
    };

    // 서버가 서브프로토콜을 그대로 돌려주지 않으면 브라우저가 연결을 끊는다 — 실서버
    // CollabWebSocketHandler.getSubProtocols()가 하는 일과 같다.
    const upgraded = server.upgrade(request, {
      data,
      headers: { 'Sec-WebSocket-Protocol': PROTOCOL_NAME },
    });
    return upgraded
      ? undefined
      : new Response('Upgrade failed', { status: 400 });
  },

  websocket: {
    open(ws) {
      const { connectionId, threadId, userId, denyOnOpen } = ws.data;

      if (denyOnOpen) {
        const frame = errorFrame(
          threadId,
          'FORBIDDEN',
          '이 방에 들어갈 권한이 없습니다.',
        );
        ws.send(JSON.stringify(frame));
        // 1000으로 닫는다 — 재연결해도 같은 거부라 ws-connection.ts가 루프를 멈추게 한다.
        ws.close(1000, 'room forbidden');
        return;
      }

      const member: RoomMember = {
        id: connectionId,
        userId,
        send: (text) => ws.send(text),
      };

      // 이미 들어와 있는 사람들을 새로 온 사람에게 먼저 재생한다. 참여자 스냅샷 전용 프레임이
      // #8에 없어 presence.join을 되풀이하는 것으로 대신한다(#26 접속자 목록 UI에 필요).
      for (const existing of registry.membersOf(threadId)) {
        member.send(JSON.stringify(presenceJoin(threadId, existing.userId)));
      }

      registry.join(threadId, member);
      registry.broadcast(threadId, presenceJoin(threadId, userId));
      console.log(`[mock-ws] join  ${userId} → ${threadId}`);
    },

    message(ws, raw) {
      const { threadId, userId } = ws.data;
      const frame = parseFrameFromText(String(raw));

      if (frame === null) {
        const error = errorFrame(
          threadId,
          'MALFORMED_REQUEST',
          '프레임을 해석할 수 없습니다.',
        );
        ws.send(JSON.stringify(error));
        return;
      }
      // 클라이언트가 올려보내는 건 메시지뿐이다. 나머지 타입은 서버가 내려보내는 것이라 무시한다.
      if (frame.type !== 'chat.message') return;

      // from은 클라이언트 말이 아니라 커넥션에 붙은 사용자로 덮어쓴다.
      registry.broadcast(threadId, {
        type: 'chat.message',
        sessionId: threadId,
        from: userId,
        content: frame.content,
      });

      if (mentionsAi(frame.content)) {
        void streamAiAnswer(threadId, frame.content);
      }
    },

    close(ws) {
      const { connectionId, threadId, userId, denyOnOpen } = ws.data;
      if (denyOnOpen) return;
      registry.leave(threadId, connectionId);
      console.log(`[mock-ws] leave ${userId} ← ${threadId}`);
    },
  },
});

console.log(`[mock-ws] ws://localhost:${server.port}${WS_PATH} 에서 대기 중`);
