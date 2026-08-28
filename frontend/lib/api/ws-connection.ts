/********************************************************
 파일명 : ws-connection.ts (lib/api)
 설 명 : 협업채팅 WS 커넥션 계층(이슈 #4). #3이 만든 서브프로토콜 인증 진입점 위에서
 연결을 유지하고, 끊기면 사유에 따라 다르게 되살린다.

 http.ts의 authFetch가 요청 단위로 하던 "401 → 세션 재조회 → 재시도 → 그래도 안 되면
 재로그인"을 커넥션 단위로 옮긴 것이다. 다만 요청 단위와 결정적으로 다른 점이 하나 있다:
 브라우저 WebSocket API는 핸드셰이크 응답의 status code도 body도 노출하지 않는다. 그래서
 서버가 401 봉투에 담아 보내는 TOKEN_EXPIRED·TOKEN_INVALID 구분은 여기까지 오지 못하고,
 실패는 전부 "열리지 않은 채 close" 하나로 뭉개져 도착한다.

 그 대신 쓰는 신호가 close code다(이슈 #2 확정, 서버 측은 #62):
   - 4000(토큰 만료) — 서버가 exp 타이머로 끊은 것. 세션을 재조회해 곧바로 재연결한다.
   - 1000(정상 종료) — 되살리지 않는다.
   - 그 외(1006 등) — 토큰 문제로 단정할 수 없으니 백오프 재연결만 한다. 다만 연속으로 거부되면
     그때는 세션을 다시 조회한다(아래).

 "재조회 후에도 무효면 재로그인"은 4000 직후의 핸드셰이크 실패로만 판정한다 — 서버 다운과
 인증 거부가 클라이언트에서 똑같이 1006으로 보이기 때문에, 만료 통보를 받은 직후라는 문맥이
 없으면 인증 실패로 단정할 수 없다. 그 문맥 밖의 실패를 재로그인으로 처리하면 서버가 잠깐
 죽었을 뿐인데 사용자를 로그인 화면으로 쫓아내게 된다.

 다만 그 문맥 밖이라고 아무것도 안 하면, 낡은 토큰을 든 채 영원히 같은 실패를 반복하게 된다
 (PR #68 리뷰 지적). 그래서 핸드셰이크가 연속으로 거부되면 세션을 다시 조회한다 — 재로그인
 여부의 판정을 close code가 아니라 next-auth에 넘기는 것이다. 세션이 실제로 죽었으면
 (RefreshAccessTokenError·토큰 없음) freshToken()이 그 자리에서 재로그인시키고, 살아 있으면
 새 토큰을 받아 백오프 재연결을 이어간다.
 *********************************************************/

import { getSession, signIn } from 'next-auth/react';
import { WS_PATH, bffWsUrl } from './config';

/** 서브프로토콜의 첫 번째 값 — 서버 WsSubProtocolBearerTokenConverter.PROTOCOL_NAME과 반드시 같아야 한다. */
const PROTOCOL_NAME = 'access_token';

/** 서버가 토큰 만료로 커넥션을 끊을 때 쓰는 커스텀 close code(이슈 #62). */
export const CLOSE_TOKEN_EXPIRED = 4000;

/** RFC 6455 정상 종료. 이 코드로 끊기면 재연결하지 않는다. */
const CLOSE_NORMAL = 1000;

/** RFC 6455 비정상 종료 — 브라우저가 close 프레임 없이 끊긴 연결(핸드셰이크 거부 포함)에 매기는 코드. */
const CLOSE_ABNORMAL = 1006;

/** 재연결 백오프 상한 — http.ts의 429 백오프와 같은 값으로 맞춘다. */
const RECONNECT_BACKOFF_CAP_MS = 10_000;

/** 핸드셰이크가 이만큼 연속으로 거부되면 세션을 다시 조회한다. 1회로 하면 서버가 잠깐 흔들릴 때마다
 * /api/auth/session을 두드리게 되고, 너무 늘리면 낡은 토큰으로 헛도는 시간이 길어진다. 3회면 백오프
 * 곡선상 약 7초다. */
const HANDSHAKE_FAILURES_BEFORE_REFRESH = 3;

/** 표준 WebSocket에서 이 계층이 실제로 쓰는 부분만 추린 구조적 타입. vitest 환경이 'node'라
 * 전역 WebSocket이 없어, 테스트가 가짜 소켓을 끼울 수 있어야 한다. */
export interface SocketLike {
  addEventListener(
    type: 'open' | 'message' | 'close',
    listener: (event: { data?: unknown; code?: number }) => void,
  ): void;
  close(code?: number, reason?: string): void;
}

interface WsConnectionDeps {
  /** URL 결정까지 소켓 팩토리가 맡는다 — 그래야 테스트가 window.location에 기대지 않는다. */
  createSocket: (protocols: string[]) => SocketLike;
  getSession: typeof getSession;
  signIn: typeof signIn;
  sleep: (ms: number) => Promise<void>;
}

const defaultDeps: WsConnectionDeps = {
  createSocket: (protocols) =>
    new WebSocket(bffWsUrl(WS_PATH), protocols) as unknown as SocketLike,
  getSession,
  signIn,
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
};

export interface WsConnectionOptions {
  /** 서버가 보낸 텍스트 프레임 하나. 파싱은 이 계층의 일이 아니다(lib/transport/parse-frame.ts). */
  onMessage: (data: string) => void;
  /** 재로그인이 강제되는 순간 불린다 — http.ts의 onForcedReauth와 같은 목적이다. */
  onForcedReauth?: () => void;
}

export interface WsConnection {
  /** 재연결 루프를 멈추고 소켓을 정상 종료(1000)한다. 이후 어떤 콜백도 불리지 않는다. */
  close: () => void;
}

/** 재연결 대기 시간. http.ts의 retryAfterMs 폴백과 같은 지수 곡선(1s, 2s, 4s ...)에 상한을 둔다. */
export function reconnectBackoffMs(attempt: number): number {
  return Math.min(2 ** attempt * 500, RECONNECT_BACKOFF_CAP_MS);
}

/** 한 번 연결해서 끊길 때까지 기다린다. 열린 적이 있는지(opened)와 close code를 함께 돌려주는데,
 * 이 둘의 조합이 "인증이 거부됐다"와 "연결은 됐다가 끊겼다"를 가르는 유일한 단서다. */
function connectOnce(
  token: string,
  options: WsConnectionOptions,
  deps: WsConnectionDeps,
  onSocket: (socket: SocketLike) => void,
): Promise<{ opened: boolean; code: number }> {
  return new Promise((resolve) => {
    // 표준 WebSocket API는 핸드셰이크에 Authorization 헤더를 못 실으므로 서브프로토콜 두 값으로
    // 토큰을 넘긴다 — 서버 WsSubProtocolBearerTokenConverter와 짝이다(이슈 #3).
    const socket = deps.createSocket([PROTOCOL_NAME, token]);
    onSocket(socket);

    let opened = false;
    socket.addEventListener('open', () => {
      opened = true;
    });
    socket.addEventListener('message', (event) => {
      // 서버는 텍스트 프레임만 보낸다. Blob·ArrayBuffer가 오면 이 계층이 다룰 것이 아니다.
      if (typeof event.data === 'string') options.onMessage(event.data);
    });
    socket.addEventListener('close', (event) => {
      // code가 없는 close는 표준상 나오지 않지만, 온다면 정상 종료로 읽어 조용히 끊기는 것보다
      // 비정상으로 읽어 재연결을 시도하는 쪽이 안전하다.
      resolve({ opened, code: event.code ?? CLOSE_ABNORMAL });
    });
  });
}

/**
 * WS 커넥션을 열고, 끊기면 사유에 따라 되살린다. 반환된 close()를 부를 때까지 살아 있다.
 *
 * deps는 테스트용 주입 지점이다(http.ts와 같은 패턴).
 */
export function openWsConnection(
  options: WsConnectionOptions,
  deps: WsConnectionDeps = defaultDeps,
): WsConnection {
  let closedByCaller = false;
  let socket: SocketLike | null = null;

  const forceReauth = async () => {
    options.onForcedReauth?.();
    await deps.signIn('keycloak');
  };

  /** 세션을 다시 조회해 액세스 토큰을 얻는다. 리프레시가 이미 실패한 세션은 어떤 재연결로도
   * 살아나지 않으므로 곧장 재로그인으로 보낸다(http.ts와 같은 판단). */
  const freshToken = async (): Promise<string | null> => {
    const session = await deps.getSession();
    if (session?.error === 'RefreshAccessTokenError' || !session?.accessToken) {
      await forceReauth();
      return null;
    }
    return session.accessToken;
  };

  const run = async () => {
    let token = await freshToken();
    // 만료 통보(4000)를 받은 직후인지 — 이 문맥에서만 핸드셰이크 실패를 인증 실패로 읽는다.
    let afterExpiry = false;
    let expiryRetried = false;
    let backoffAttempt = 0;
    // 한 번도 열리지 못한 핸드셰이크가 연속 몇 번인지. 열리면 0으로 되돌린다.
    let failedHandshakes = 0;

    while (token !== null && !closedByCaller) {
      const { opened, code } = await connectOnce(token, options, deps, (s) => {
        socket = s;
      });
      if (closedByCaller || code === CLOSE_NORMAL) return;

      if (opened) {
        backoffAttempt = 0;
        expiryRetried = false;
        failedHandshakes = 0;
        if (code === CLOSE_TOKEN_EXPIRED) {
          // 만료를 통보받았으니 재조회한 토큰으로 곧바로 다시 붙는다 — 여기서 기다릴 이유가 없다.
          afterExpiry = true;
          token = await freshToken();
          continue;
        }
        afterExpiry = false;
      } else if (afterExpiry) {
        // 재조회한 토큰으로도 핸드셰이크가 거부됐다. 한 번 더 재조회해 보고, 그래도면 재로그인.
        if (expiryRetried) {
          await forceReauth();
          return;
        }
        expiryRetried = true;
        token = await freshToken();
        continue;
      } else {
        // 만료 문맥 밖에서 계속 거부되는 경우. 재로그인을 단정할 수는 없지만 토큰을 다시 받아보는
        // 것까지는 안전하다 — 세션이 죽었다면 freshToken()이 재로그인으로 보낸다.
        failedHandshakes += 1;
        if (failedHandshakes >= HANDSHAKE_FAILURES_BEFORE_REFRESH) {
          failedHandshakes = 0;
          token = await freshToken();
        }
      }

      backoffAttempt += 1;
      await deps.sleep(reconnectBackoffMs(backoffAttempt));
    }
  };

  void run();

  return {
    close: () => {
      closedByCaller = true;
      socket?.close(CLOSE_NORMAL);
    },
  };
}
