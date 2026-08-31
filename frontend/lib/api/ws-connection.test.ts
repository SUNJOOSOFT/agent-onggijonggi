import { describe, expect, it, vi } from 'vitest';
import {
  CLOSE_TOKEN_EXPIRED,
  type SocketLike,
  type WsConnection,
  type WsConnectionOptions,
  openWsConnection,
  reconnectBackoffMs,
} from './ws-connection';

/** 테스트가 open·message·close 시점을 직접 잡을 수 있는 가짜 소켓. vitest 환경이 'node'라
 * 전역 WebSocket이 없고, 있더라도 close code를 마음대로 만들어낼 수 없다. */
class FakeSocket implements SocketLike {
  readonly protocols: string[];
  readonly path: string;
  readonly sent: string[] = [];
  closedWith: number | null = null;
  private readonly listeners = new Map<
    string,
    ((event: { data?: unknown; code?: number }) => void)[]
  >();

  constructor(protocols: string[], path: string) {
    this.protocols = protocols;
    this.path = path;
  }

  send(data: string): void {
    this.sent.push(data);
  }

  addEventListener(
    type: 'open' | 'message' | 'close',
    listener: (event: { data?: unknown; code?: number }) => void,
  ): void {
    const existing = this.listeners.get(type) ?? [];
    existing.push(listener);
    this.listeners.set(type, existing);
  }

  close(code?: number): void {
    this.closedWith = code ?? 1000;
    this.emit('close', { code: this.closedWith });
  }

  /** 서버가 끊은 상황 — close()와 달리 closedWith를 남기지 않는다(클라이언트가 닫은 게 아니다). */
  serverClose(code: number): void {
    this.emit('close', { code });
  }

  open(): void {
    this.emit('open', {});
  }

  message(data: unknown): void {
    this.emit('message', { data });
  }

  private emit(type: string, event: { data?: unknown; code?: number }): void {
    for (const listener of this.listeners.get(type) ?? []) listener(event);
  }
}

interface Harness {
  sockets: FakeSocket[];
  getSession: ReturnType<typeof vi.fn>;
  signIn: ReturnType<typeof vi.fn>;
  sleep: ReturnType<typeof vi.fn>;
  onMessage: ReturnType<typeof vi.fn>;
  connection: WsConnection;
  /** n번째 소켓이 만들어질 때까지 기다린다(1-based). 재연결 루프가 마이크로태스크로 돌기 때문. */
  waitForSocket: (n: number) => Promise<FakeSocket>;
}

function harness(
  sessions: ({
    accessToken?: string;
    error?: 'RefreshAccessTokenError';
  } | null)[],
  extraOptions: Omit<WsConnectionOptions, 'onMessage'> = {},
): Harness {
  const sockets: FakeSocket[] = [];
  // 준비한 세션을 순서대로 하나씩 내주고, 다 떨어지면 마지막 것을 계속 돌려준다.
  let call = 0;
  const getSession = vi.fn(async () => {
    const session = sessions[Math.min(call, sessions.length - 1)];
    call += 1;
    return session;
  });
  const signIn = vi.fn(async () => undefined);
  const sleep = vi.fn(async () => undefined);
  const onMessage = vi.fn();

  const connection = openWsConnection(
    { onMessage, ...extraOptions },
    {
      createSocket: (protocols, path) => {
        const socket = new FakeSocket(protocols, path);
        sockets.push(socket);
        return socket;
      },
      getSession: getSession as never,
      signIn: signIn as never,
      sleep,
    },
  );

  return {
    sockets,
    getSession,
    signIn,
    sleep,
    onMessage,
    connection,
    waitForSocket: (n) =>
      vi.waitFor(() => {
        const socket = sockets[n - 1];
        if (!socket) throw new Error(`${n}번째 소켓이 아직 없다`);
        return socket;
      }),
  };
}

describe('reconnectBackoffMs', () => {
  it('지수로 늘다가 상한에서 멈춘다', () => {
    expect(reconnectBackoffMs(1)).toBe(1000);
    expect(reconnectBackoffMs(2)).toBe(2000);
    expect(reconnectBackoffMs(3)).toBe(4000);
    expect(reconnectBackoffMs(10)).toBe(10_000);
  });
});

describe('openWsConnection', () => {
  it('서브프로토콜 두 값으로 토큰을 실어 연결한다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);
    expect(socket.protocols).toEqual(['access_token', 't1']);
    h.connection.close();
  });

  it('텍스트 프레임만 onMessage로 흘리고 그 외 payload는 무시한다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);
    socket.open();
    socket.message('{"type":"chat.message"}');
    socket.message(new ArrayBuffer(4));
    expect(h.onMessage).toHaveBeenCalledExactlyOnceWith(
      '{"type":"chat.message"}',
    );
    h.connection.close();
  });

  it('정상 종료(1000)면 재연결하지 않는다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);
    socket.open();
    socket.serverClose(1000);
    await vi.waitFor(() => expect(h.sleep).not.toHaveBeenCalled());
    expect(h.sockets).toHaveLength(1);
  });

  it('토큰 만료(4000)면 세션을 재조회해 백오프 없이 곧바로 재연결한다', async () => {
    const h = harness([{ accessToken: 't1' }, { accessToken: 't2' }]);
    const first = await h.waitForSocket(1);
    first.open();
    first.serverClose(CLOSE_TOKEN_EXPIRED);

    const second = await h.waitForSocket(2);
    expect(second.protocols).toEqual(['access_token', 't2']);
    expect(h.getSession).toHaveBeenCalledTimes(2);
    expect(h.sleep).not.toHaveBeenCalled();
    h.connection.close();
  });

  it('만료가 아닌 비정상 종료(1006)는 토큰 재조회 없이 백오프 재연결만 한다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const first = await h.waitForSocket(1);
    first.open();
    first.serverClose(1006);

    const second = await h.waitForSocket(2);
    expect(second.protocols).toEqual(['access_token', 't1']);
    expect(h.sleep).toHaveBeenCalledExactlyOnceWith(1000);
    expect(h.getSession).toHaveBeenCalledTimes(1);
    h.connection.close();
  });

  it('만료 문맥 밖에서도 핸드셰이크가 3번 연속 거부되면 세션을 다시 조회한다', async () => {
    const h = harness([{ accessToken: 't1' }, { accessToken: 't2' }]);

    // 한 번도 open()하지 않는다 — 핸드셰이크가 거부되는 경우다. 4000을 받은 적이 없으므로
    // 만료 문맥(afterExpiry) 밖이고, 예전에는 이 경로가 같은 토큰으로 무한 재시도만 했다.
    for (const n of [1, 2, 3]) (await h.waitForSocket(n)).serverClose(1006);

    const fourth = await h.waitForSocket(4);
    expect(fourth.protocols).toEqual(['access_token', 't2']);
    expect(h.getSession).toHaveBeenCalledTimes(2);
    h.connection.close();
  });

  it('3번 연속 거부 시점에 세션이 죽어 있으면 재로그인시킨다', async () => {
    const h = harness([
      { accessToken: 't1' },
      { accessToken: 't1', error: 'RefreshAccessTokenError' },
    ]);

    for (const n of [1, 2, 3]) (await h.waitForSocket(n)).serverClose(1006);

    // 재조회로 판정을 next-auth에 넘긴 결과다 — close code만 보고 단정한 게 아니다.
    await vi.waitFor(() => expect(h.signIn).toHaveBeenCalledWith('keycloak'));
    expect(h.sockets).toHaveLength(3);
  });

  it('만료 통보 후 재조회한 토큰으로도 핸드셰이크가 거부되면 1회 더 재조회하고 재로그인시킨다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const first = await h.waitForSocket(1);
    first.open();
    first.serverClose(CLOSE_TOKEN_EXPIRED);

    // 2·3번째는 open 없이 끊긴다 — 브라우저가 핸드셰이크 401을 보는 방식(1006)이다.
    (await h.waitForSocket(2)).serverClose(1006);
    (await h.waitForSocket(3)).serverClose(1006);

    await vi.waitFor(() => expect(h.signIn).toHaveBeenCalledWith('keycloak'));
    expect(h.sockets).toHaveLength(3);
    expect(h.sleep).not.toHaveBeenCalled();
  });

  it('리프레시가 실패한 세션이면 연결을 시도하지 않고 곧장 재로그인시킨다', async () => {
    const onForcedReauth = vi.fn();
    const sockets: FakeSocket[] = [];
    const signIn = vi.fn(async () => undefined);

    openWsConnection(
      { onMessage: vi.fn(), onForcedReauth },
      {
        createSocket: (protocols, path) => {
          const socket = new FakeSocket(protocols, path);
          sockets.push(socket);
          return socket;
        },
        getSession: (async () => ({
          accessToken: 't1',
          error: 'RefreshAccessTokenError',
        })) as never,
        signIn: signIn as never,
        sleep: vi.fn(async () => undefined),
      },
    );

    await vi.waitFor(() => expect(signIn).toHaveBeenCalledWith('keycloak'));
    expect(onForcedReauth).toHaveBeenCalledOnce();
    expect(sockets).toHaveLength(0);
  });

  it('close()는 소켓을 1000으로 닫고 재연결 루프를 멈춘다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);
    socket.open();
    h.connection.close();

    expect(socket.closedWith).toBe(1000);
    await vi.waitFor(() => expect(h.sleep).not.toHaveBeenCalled());
    expect(h.sockets).toHaveLength(1);
  });
});

describe('openWsConnection - 협업방(이슈 #19)', () => {
  it('경로를 넘기지 않으면 방 없는 기본 경로로 붙는다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);
    expect(socket.path).toBe('/api/ws');
    h.connection.close();
  });

  it('넘긴 경로를 그대로 소켓에 쓴다 — 방은 쿼리로만 구분된다', async () => {
    const h = harness([{ accessToken: 't1' }], {
      path: '/api/ws?threadId=room-7',
    });
    const socket = await h.waitForSocket(1);
    expect(socket.path).toBe('/api/ws?threadId=room-7');
    h.connection.close();
  });

  it('열려 있을 때만 보내고, 끊겨 있으면 보내지 않고 false를 준다', async () => {
    const h = harness([{ accessToken: 't1' }]);
    const socket = await h.waitForSocket(1);

    // 아직 open 이벤트가 오지 않았다 — 핸드셰이크 중에 누른 전송이다.
    expect(h.connection.send('early')).toBe(false);
    expect(socket.sent).toEqual([]);

    socket.open();
    expect(h.connection.send('hello')).toBe(true);
    expect(socket.sent).toEqual(['hello']);

    // 끊긴 뒤의 메시지는 큐에 쌓지 않는다 — 되살아난 커넥션으로 뒤늦게 나가면 순서가 어긋난다.
    socket.serverClose(1006);
    expect(h.connection.send('after close')).toBe(false);
    expect(socket.sent).toEqual(['hello']);

    h.connection.close();
  });

  it('열리고 끊길 때마다 화면에 알린다', async () => {
    const onOpenChange = vi.fn();
    const h = harness([{ accessToken: 't1' }], { onOpenChange });
    const socket = await h.waitForSocket(1);

    socket.open();
    expect(onOpenChange).toHaveBeenLastCalledWith(true);

    socket.serverClose(1006);
    expect(onOpenChange).toHaveBeenLastCalledWith(false);
    expect(onOpenChange).toHaveBeenCalledTimes(2);

    h.connection.close();
  });

  it('열린 적 없이 거부된 핸드셰이크는 닫혔다고 알리지 않는다', async () => {
    const onOpenChange = vi.fn();
    const h = harness([{ accessToken: 't1' }], { onOpenChange });
    const socket = await h.waitForSocket(1);

    socket.serverClose(1006);
    expect(onOpenChange).not.toHaveBeenCalled();

    h.connection.close();
  });
});
