/********************************************************
 파일명 : bun-runtime.d.ts (mocks)
 설 명 : ws-server.ts가 실제로 쓰는 Bun 런타임 API만 좁게 선언한다.

 공식 패키지(@types/bun)를 쓰지 않는 이유 — 그쪽은 전역 `fetch`를 Bun 시그니처로 덮어써서
 앱 코드(lib/api/http.ts)의 타입 검사를 깨뜨린다. 목업 하나 때문에 앱 전체의 타입이 흔들리면
 안 되므로, 전역을 건드리지 않는 최소 선언을 여기 둔다. 이 파일이 루트 tsconfig 안에 있는
 덕에 CI의 `bunx tsc --noEmit`이 목업 서버도 그대로 검사한다.

 선언이 실제 Bun API와 어긋나면 목업 서버가 기동 시점에 바로 죽으므로 조용히 틀어지지 않는다.
 *********************************************************/

interface BunServerWebSocket<T> {
  readonly data: T;
  send(data: string): number;
  close(code?: number, reason?: string): void;
}

interface BunServer<T> {
  readonly port: number;
  /** 성공하면 true. 이때 fetch 핸들러는 Response 대신 undefined를 돌려줘야 한다. */
  upgrade(
    request: Request,
    options: { data: T; headers?: Record<string, string> },
  ): boolean;
}

interface BunWebSocketHandler<T> {
  open?(ws: BunServerWebSocket<T>): void;
  message?(ws: BunServerWebSocket<T>, message: string | Uint8Array): void;
  close?(ws: BunServerWebSocket<T>, code: number, reason: string): void;
}

interface BunServeOptions<T> {
  port?: number;
  fetch(
    request: Request,
    server: BunServer<T>,
  ): Response | undefined | Promise<Response | undefined>;
  websocket: BunWebSocketHandler<T>;
}

declare const Bun: {
  serve<T>(options: BunServeOptions<T>): BunServer<T>;
};
