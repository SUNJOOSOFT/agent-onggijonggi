# 리버스 프록시로 HTTPS 붙이기 — caddy

기본 설치([INSTALL.md](INSTALL.md))는 `http://localhost:3010`으로 포트를 직접 연다. 이 문서는 **caddy**를 하나 더 띄워 포트 없는 HTTPS 주소로 바꾸는 방법이다.

```
http://localhost:3010  →  https://app.localhost
http://localhost:8090  →  https://api.localhost
http://localhost:8081  →  https://auth.localhost
```

**INSTALL.md를 대체하지 않는다** — 달라지는 곳만 적었다.

---

## 무엇이 달라지나

| | 기본 | caddy |
|---|---|---|
| 접속 주소 | `http://localhost:3010` | `https://app.localhost` |
| HTTPS | 없음 | 있음 (내 PC에서 만든 인증서) |
| 추가 준비 | 없음 | 공용 네트워크 · 인증서 신뢰 등록(**관리자 권한 1회**) |
| 필요한 포트 | 3010 등 | **80 · 443** 추가 |
| 컨테이너 수 | 6 | 7 |

`.localhost`로 끝나는 이름은 브라우저가 표준(RFC 6761)에 따라 `127.0.0.1`로 해석한다 — **hosts 파일을 건드릴 필요가 없고**, Wi-Fi나 IP가 바뀌어도 영향이 없다.

> ⚠️ **다른 기기에서는 접속할 수 없다.** `app.localhost`는 "접속하는 기기 자기 자신"을 뜻하므로, 휴대폰에서 이 주소를 치면 휴대폰 자신을 찾는다. 여러 기기에서 쓰려면 기본 설치를 쓰고 `.env`의 주소를 이 PC의 IP로 바꾼다.
>
> ⚠️ **`ping app.localhost`나 `curl`은 실패할 수 있다.** 이 해석은 대체로 브라우저가 자체적으로 하는 일이라 OS의 이름 해석과는 별개다. Chrome·Edge·Firefox에서 동작하며, 그 밖의 브라우저(macOS Safari 등)나 CLI 도구에서 "주소를 찾을 수 없음"이 나오면 hosts 파일에 세 줄을 넣으면 된다.
>
> ```
> 127.0.0.1 app.localhost
> 127.0.0.1 api.localhost
> 127.0.0.1 auth.localhost
> ```
>
> 파일 위치는 Windows가 `C:\Windows\System32\drivers\etc\hosts`, macOS·Linux가 `/etc/hosts`다(둘 다 관리자 권한 필요).

---

## 1. `.env` 고치기

INSTALL.md 1단계로 `.env`를 만든 뒤, 주소 네 줄을 바꾸고 한 줄을 더한다.

```
NEXTAUTH_URL=https://app.localhost
PUBLIC_FRONTEND_URL=https://app.localhost
PUBLIC_BFF_URL=https://api.localhost
PUBLIC_KEYCLOAK_URL=https://auth.localhost
COMPOSE_PROFILES=caddy
```

> ⚠️ **`COMPOSE_PROFILES=caddy`가 없으면 caddy는 아예 켜지지 않는다.** compose에서 `profiles: [caddy]`로 묶여 있어 기본 기동에서는 빠진다. 이 줄을 넣으면 이후 모든 `docker compose` 명령이 caddy를 함께 다룬다. (`docker compose --profile caddy ...`를 매번 붙여도 되지만 빠뜨리기 쉽다.)
>
> ⚠️ **이미 기본 설치로 한 번 띄운 뒤라면** 주소만 바꿔선 안 된다 — 아래 [기본 설치에서 전환하기](#기본-설치에서-전환하기)를 본다.

---

## 2. caddy 준비

**공용 네트워크 만들기** — 한 번만 하면 된다.

```bash
docker network create proxy-net
```

compose에서 `external: true`로 선언돼 있어 자동으로 만들어지지 않는다. 없으면 `network proxy-net declared as external, but could not be found`로 기동이 실패한다. 이미 있으면 `already exists`가 나오는데 정상이다.

> 💡 이 저장소 밖의 다른 compose 스택도 같은 caddy 뒤에 붙일 수 있도록 분리해 둔 네트워크다. 앱 7개끼리는 compose가 만드는 `app-net`으로 통신한다.

`infra/Caddyfile`은 저장소에 들어 있어 따로 만들 것이 없다. 주소 3개를 각각 nextjs·bff·keycloak으로 넘기고 `local_certs`로 인증서를 자체 발급한다.

---

## 3. 기동

INSTALL.md 2단계(모델 연결)까지 마친 뒤 띄운다.

```bash
docker compose up -d --build
docker compose ps
```

**✅ 성공**: `agent-ogjg-caddy-1`을 포함해 **7줄**이 뜬다. caddy는 헬스체크가 없어 `Up`으로만 표시되고, 나머지 6개는 `Up (healthy)`가 된다.

> ⚠️ **caddy가 목록에 없으면** 1단계의 `COMPOSE_PROFILES=caddy`가 빠졌거나 오타다.

---

## 4. 인증서 신뢰 등록

caddy가 자체 발급한 인증서라, 등록하지 않으면 브라우저가 경고를 띄운다. 먼저 인증서를 꺼낸다 — 이 명령은 세 OS에서 같다.

```bash
docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-local-root.crt
```

**✅ 성공**: `infra` 폴더에 `caddy-local-root.crt`가 생긴다. 안 생기면 `docker compose logs caddy`로 caddy가 떴는지 먼저 본다.

그다음 OS에 맞게 등록한다.

<details>
<summary><b>Windows</b></summary>

**관리자 권한 PowerShell**에서 `infra` 폴더로 이동해:

```powershell
Import-Certificate -FilePath .\caddy-local-root.crt -CertStoreLocation Cert:\LocalMachine\Root
```

</details>

<details>
<summary><b>macOS</b></summary>

```bash
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain caddy-local-root.crt
```

키체인 접근 앱에서 직접 넣어도 된다 — 시스템 키체인에 추가한 뒤 "이 인증서 사용 시" 항목을 **항상 신뢰**로 바꾼다.

</details>

<details>
<summary><b>Linux</b></summary>

시스템 신뢰 저장소에 넣는다(데비안·우분투 계열):

```bash
sudo cp caddy-local-root.crt /usr/local/share/ca-certificates/caddy-local-root.crt
sudo update-ca-certificates
```

RHEL·페도라 계열이면 `/etc/pki/ca-trust/source/anchors/`에 넣고 `sudo update-ca-trust`를 쓴다.

> ⚠️ **Chrome·Firefox는 시스템 저장소를 보지 않는다.** 리눅스에서는 브라우저가 자체 NSS 저장소를 쓰므로 한 번 더 넣어야 한다(`libnss3-tools` 필요).
> ```bash
> certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n caddy-local -i caddy-local-root.crt
> ```
> Firefox는 프로필별로 저장하므로 위 명령 대신 설정 → 인증서 보기 → 인증 기관 → 가져오기에서 직접 넣는 편이 확실하다.

</details>

> 💡 등록 후 **브라우저를 완전히 껐다 켜야** 적용된다.
> 💡 등록하지 않고 "고급 → 계속 진행"으로 넘어가도 동작은 한다. 다만 접속할 때마다 눌러야 한다.

---

## 5. 접속

브라우저에서 **<https://app.localhost>** 를 연다. 계정은 기본 설치와 같다(`appuser` / `appuser`).

> ⚠️ **`localhost:3010`이 아니다.** 앞의 `app.`까지 붙이고 `https://`로 연다. 포트로 직접 들어가면 로그인 주소가 어긋나 실패한다.

Keycloak 관리자 화면은 <https://auth.localhost/admin> 이다.

---

## 문제 해결

INSTALL.md의 [문제 해결](INSTALL.md#문제-해결)이 그대로 유효하다. 아래는 caddy에서만 나오는 증상이다.

| 증상 | 원인과 조치 |
|---|---|
| `docker compose ps`에 caddy가 없다 | `COMPOSE_PROFILES=caddy` 누락 → `.env` 고치고 `docker compose up -d` |
| `network proxy-net declared as external, but could not be found` | 2단계의 `docker network create proxy-net` 누락 |
| **"사이트에 연결할 수 없음"** | 주소 오타이거나 caddy가 안 떠 있다. Chrome·Edge·Firefox가 아니면 `*.localhost`를 해석 못 할 수 있다 |
| **인증서 경고** | 4단계를 안 했거나 브라우저를 안 껐다 켰다 |
| **`502 Bad Gateway`** | caddy는 떴는데 뒤쪽이 아직이다. `docker compose ps`로 6개가 `healthy`인지 보고 1~2분 기다린다 |
| **로그인 후 오류** | 브라우저 주소가 `localhost:3010`이었을 가능성이 크다. `https://app.localhost`로 다시 접속 |
| **`port is already allocated` — 80/443** | 아래 참고 |

### 포트 충돌 — 80 · 443

웹서버가 이미 잡고 있는 경우가 흔하다(Windows는 IIS, 리눅스는 apache·nginx).

**macOS · Linux**

```bash
sudo lsof -i :80 -i :443
```

**Windows (PowerShell)**

```powershell
Get-NetTCPConnection -LocalPort 80,443 -ErrorAction SilentlyContinue | Select-Object LocalPort,OwningProcess
```

그 프로그램을 끄거나, caddy를 포기하고 기본 설치를 쓴다. **caddy의 포트만 바꾸는 건 답이 안 된다** — 주소에 포트가 붙어(`https://app.localhost:8443`) Keycloak에 등록된 주소까지 전부 어긋난다.

> 💡 **(Linux)** 루트리스 도커를 쓴다면 1024 미만 포트를 그냥은 못 연다. `sysctl net.ipv4.ip_unprivileged_port_start=80`을 주거나 일반 도커 엔진을 쓴다.

---

## 기본 설치에서 전환하기

이미 한 번 띄운 뒤라면 **로그인 설정을 다시 만들어야 한다.** realm의 리다이렉트 주소가 최초 기동 때 `.env` 값으로 굳어 있어, `.env`만 고치면 로그인이 거부된다.

```bash
# 이 문서 1단계·2단계를 마친 뒤
docker compose down
docker volume rm agent-ogjg_postgres-data
docker compose up -d --build
```

> ⚠️ **로그인 계정과 대화 내용이 지워진다.** `.env`의 `APP_USER` 기본 계정은 자동으로 다시 생긴다.
> 💡 지우기 싫다면 Keycloak 관리자 화면에서 `app-realm` → Clients → `ogjg-client`의 **Valid redirect URIs · Valid post logout redirect URIs · Web origins** 세 칸을 `https://app.localhost` 계열로 바꿔도 된다.

### 기본 설치로 되돌리려면

1. `.env`에서 `COMPOSE_PROFILES=caddy`를 지우고 주소 네 줄을 `.env.example` 값으로 되돌린다
2. `docker compose --profile caddy down` — 이미 `COMPOSE_PROFILES`를 지웠다면 `--profile caddy`를 붙여야 caddy까지 내려간다
3. 위와 같이 로그인 설정을 다시 만든다

`proxy-net`과 `caddy-local-root.crt`는 남겨둬도 무해하다.

---

## 주소 이름 바꾸기

`app.localhost` 대신 다른 이름을 쓰려면 **두 곳을 함께** 고친다. `Caddyfile`은 건드리지 않는다 —
주소를 `.env`에서 받도록 돼 있다.

| 어디 | 무엇 |
|---|---|
| `infra/.env` | `CADDY_*_DOMAIN` 3줄 + `PUBLIC_*` 3줄 + `NEXTAUTH_URL` |
| Keycloak | 이미 만들어진 realm → 위의 "다시 만들기" |

고친 뒤 `docker compose up -d --build` 로 반영한다. 환경변수는 컨테이너를 다시 만들어야 들어가서
`docker restart`로는 주소가 바뀌지 않는다.

> ⚠️ `.localhost`로 끝나지 않는 이름을 쓰면 hosts 파일 등록이 따로 필요하다.

---

## 알아둘 제약

INSTALL.md의 [알아둘 제약](INSTALL.md#알아둘-제약)이 그대로 적용된다. 달라지는 부분만:

- **HTTPS는 브라우저와 caddy 사이에만 적용된다.** 그것도 정식 인증서가 아니라 내 PC에서 만든 것이라, 4단계에서 신뢰 등록을 했기 때문에 경고가 안 뜨는 것뿐이다. 인터넷에 공개하는 용도로는 쓸 수 없다.
- **caddy를 건너뛰는 직접 포트는 여전히 평문이다.** `3010` `8090` `8081` `4000` `5442`가 호스트의 모든 인터페이스에 열려 있다. caddy 방식은 어차피 이 PC에서만 쓰므로, `docker-compose.yml`의 해당 `ports:`를 `"127.0.0.1:3010:3000"` 처럼 잠그는 편이 안전하다 — 그렇게 잠가도 `https://app.localhost`는 그대로 된다.
