/********************************************************
 파일명 : handshake.ts (mocks)
 설 명 : 목업 WS 서버의 핸드셰이크 해석 — 서브프로토콜에서 토큰 꺼내기, 토큰에서 사용자 이름 뽑기.

 ws-server.ts에 두지 않은 이유는 그 파일이 import되는 순간 Bun.serve가 포트를 잡아서
 vitest(node 환경)에서 불러올 수 없기 때문이다. 방 로직을 rooms.ts로 뺀 것과 같은 이유다.
 *********************************************************/

/** 서브프로토콜의 첫 번째 값 — 서버 WsSubProtocolBearerTokenConverter.PROTOCOL_NAME과 짝이다. */
export const PROTOCOL_NAME = 'access_token';

/**
 * 서브프로토콜 헤더에서 토큰을 꺼낸다. 클라이언트는 `access_token, <jwt>` 두 값을 보낸다
 * (ws-connection.ts). 모양이 안 맞으면 null — 호출부가 401로 돌려준다.
 */
export function bearerFromSubProtocol(header: string | null): string | null {
  if (!header) return null;
  const [name, token] = header.split(',').map((part) => part.trim());
  return name === PROTOCOL_NAME && token ? token : null;
}

/**
 * 서명은 검증하지 않고 payload만 열어 사람이 읽을 이름을 뽑는다 — 목업이라 이 이상은 필요 없다.
 * 실서버라면 여기가 JWT 검증 자리다(BFF의 WsSecurityConfig).
 */
export function userIdFromToken(token: string): string | null {
  const payload = token.split('.')[1];
  if (!payload) return null;
  try {
    const text = Buffer.from(payload, 'base64url').toString('utf8');
    const claims = JSON.parse(text) as Record<string, unknown>;
    const name = claims.preferred_username ?? claims.sub;
    return typeof name === 'string' ? name : null;
  } catch {
    return null;
  }
}
