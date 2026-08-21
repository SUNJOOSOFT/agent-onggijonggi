# 설치 — 내 PC에서 띄워보기

`docker compose` 한 번으로 6개 컨테이너(프론트·BFF·게이트웨이·인증·DB·캐시)가 뜬다. **모델은 포함돼 있지 않다** — OpenAI 호환 엔드포인트 하나를 각자 연결한다(2단계).

작업 시간은 10분 남짓, 여기에 첫 이미지 빌드 5~15분이 더해진다.

> **성격**: 개발·평가용 구성이다. HTTPS가 아니고 자격증명이 공개값이다. 공개망에 그대로 두지 않는다(맨 아래 [알아둘 제약](#알아둘-제약)).

---

## 0. 준비물

| | 필요한 것 |
|---|---|
| 메모리 | 여유 8GB 이상 |
| 디스크 | 10GB 이상 |
| 모델 | OpenAI 호환 엔드포인트 하나 — 상용 API 키 또는 로컬 Ollama (2단계에서 고른다) |
| 포트 | `3010` `8090` `8081` `5442` `4000` `6379` 가 비어 있어야 한다 |

아래에서 자기 OS를 펼쳐 도커를 설치한다.

<details>
<summary><b>Windows</b> — Docker Desktop</summary>

[Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치한다. WSL2 백엔드를 쓰도록 설정한다(설치 시 기본값).

</details>

<details>
<summary><b>macOS</b> — Docker Desktop</summary>

[Docker Desktop](https://www.docker.com/products/docker-desktop/)을 설치한다. Apple Silicon·Intel용 설치본이 다르니 맞는 쪽을 받는다.

> ⚠️ **메모리 할당을 확인한다.** Docker Desktop → Settings → Resources에서 Memory가 8GB 이상인지 본다. 기본값이 낮으면 프론트 빌드가 조용히 실패한다.

</details>

<details>
<summary><b>Linux</b> — Docker Engine</summary>

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

`usermod` 뒤에는 **로그아웃했다 다시 로그인해야** 적용된다. 그 전까지는 모든 `docker` 명령에 `sudo`를 붙인다.

</details>

설치 확인:

```bash
docker compose version
```

답하지 않으면 compose 플러그인이 빠진 것이다 — 리눅스라면 `sudo apt install docker-compose-plugin`(데비안 계열) 등으로 채운다.

---

## 1. 클론 · 설정 복사

**macOS · Linux**

```bash
git clone https://github.com/SUNJOOSOFT/agent-onggijonggi.git
cd agent-onggijonggi/infra
cp .env.example .env
```

**Windows (PowerShell)**

```powershell
git clone https://github.com/SUNJOOSOFT/agent-onggijonggi.git
cd agent-onggijonggi\infra
Copy-Item .env.example .env
```

> ⚠️ **`.env`의 자격증명은 저장소에 공개된 고정값이다.** 로그인 `appuser`/`appuser`, Keycloak 관리자 `admin`/`admin`. 혼자 시험하는 범위를 넘어선다면 반드시 바꾼다.

> 💡 `.env.example`은 **Gemini에 붙는 기본 템플릿**이다. Ollama나 사내 서버를 쓸 거라면 2단계에서 다른 템플릿으로 갈아탄다 — 지금은 그냥 복사하고 넘어가도 된다.

<details>
<summary>접속 주소가 어떻게 잡혀 있는지 (안 읽어도 된다)</summary>

`.env` 맨 아래 네 줄이 브라우저가 쓰는 주소다.

```
NEXTAUTH_URL=http://localhost:3010
PUBLIC_FRONTEND_URL=http://localhost:3010
PUBLIC_BFF_URL=http://localhost:8090
PUBLIC_KEYCLOAK_URL=http://localhost:8081
```

네 줄 모두 **브라우저가 보는 주소**라 `localhost`면 된다.

서버끼리 주고받는 구간은 주소가 따로다. 컨테이너 안에서 `localhost`는 그 컨테이너 자신을 가리키므로, nextjs가 Keycloak에 토큰을 받으러 갈 때는 도커 내부 주소(`http://keycloak:8080`)를 쓴다 — compose의 `KEYCLOAK_INTERNAL_ISSUER`가 그 값이다.

**같은 네트워크의 다른 기기(폰 등)에서도 열고 싶다면** 네 줄을 모두 이 PC의 IP로 바꾼다(`http://192.168.0.15:3010` 식). 단 **3단계로 넘어가기 전에** 바꿔야 한다 — 로그인 설정은 최초 기동 때 이 값으로 한 번만 만들어진다.

</details>

---

## 2. 모델 연결

### A. 외부 API — 가장 빠른 길 (기본값)

**Google Gemini는 무료 등급이 있어 결제 없이 바로 써볼 수 있다.** 이 문서는 그 기준으로 안내한다.

[aistudio.google.com/apikey](https://aistudio.google.com/apikey)에서 키를 발급받아(구글 계정만 있으면 된다) `.env`의 **한 줄만** 채운다.

```
LLM_API_KEY=
```

이걸로 끝이다. `config/litellm_config.yaml`은 손대지 않아도 된다 — 이 파일이 **기본 프리셋**이고, Gemini 하나를 연결해 둔 상태다.

> 💡 **다른 구성이 필요하다면 `.env` 템플릿을 갈아탄다.** 네 템플릿은 **여는 모델만 다르고 나머지는 같다** — 채울 곳이 맨 위에 몰려 있다.
>
> | 템플릿 | 무엇을 여는가 | 고칠 곳 |
> |---|---|---|
> | `.env.example` | **기본** — Gemini 하나 | `LLM_API_KEY` |
> | `.env.multi.example` | Gemini·Claude·OpenAI 셋 | 공급자별 키 |
> | `.env.ollama.example` | 내 PC의 Ollama (아래 B) | `LLM_MODEL` |
> | `.env.vllm.example` | 온프렘 OpenAI 호환 서버 (아래 C) | `VLLM_API_BASE`·`LLM_MODEL` |
>
> 각 템플릿은 `LITELLM_CONFIG_FILE`로 짝이 되는 `config/litellm_config*.yaml`을 가리킨다. **저장소의 설정 파일을 고칠 일이 없으므로** 다음 `git pull`이 충돌하지 않는다.
>
> ⚠️ 이미 `.env`를 만들어 고친 뒤라면 통째로 덮어쓰지 말고 **LiteLLM 절만 옮겨 적는다** — 자격증명을 직접 정했다면 그 값이 날아간다.

<details>
<summary><b>Claude · OpenAI도 함께 쓰려면</b></summary>

셋을 한꺼번에 여는 템플릿으로 갈아탄다.

**macOS · Linux**

```bash
cp .env.multi.example .env
```

**Windows (PowerShell)**

```powershell
Copy-Item .env.multi.example .env
```

그다음 쓸 공급자의 키를 채운다. 셋 다 채울 필요는 없다.

```
GEMINI_API_KEY=         # 키 발급 https://aistudio.google.com/apikey
ANTHROPIC_API_KEY=      # 키 발급 https://console.anthropic.com/settings/keys
OPENAI_API_KEY=         # 키 발급 https://platform.openai.com/api-keys
```

> ⚠️ 기본 템플릿의 `LLM_API_KEY`는 여기서 쓰이지 않는다. Gemini 키를 이미 채웠다면 **`GEMINI_API_KEY`로 옮겨 적는다.**

> **OpenAI는 무료 등급이 없다.** 크레딧을 선결제해야 키가 동작한다.

세 모델이 채팅 화면 드롭다운에 뜨고, 거기서 골라 쓴다.

> **키를 안 채운 모델도 드롭다운에는 뜬다.** 어떤 키가 채워졌는지 아는 곳은 게이트웨이뿐이라 화면이 미리 걸러내지 못한다. 그런 모델을 고르고 메시지를 보내면 *"이 모델을 사용할 수 없어요"* 안내가 뜬다. 목록에서 아예 빼려면 프리셋에서 그 항목을 지운다.

</details>

<details>
<summary><b>다른 모델을 목록에 추가하려면</b></summary>

쓰고 있는 프리셋의 `model_list`에 항목을 더한다. `model_name`이 화면 드롭다운에 그대로 뜨는 이름이고, `model:`이 LiteLLM에게 알려주는 실제 공급자·모델이다.

```yaml
  - model_name: 화면에-보일-이름
    litellm_params:
      model: 공급자/실제-모델명
      api_key: os.environ/키를_담을_환경변수
```

새 환경변수를 쓴다면 `docker-compose.yml`의 litellm 서비스에도 그 변수를 전달해야 한다. LiteLLM이 지원하는 공급자 목록은 [docs.litellm.ai/docs/providers](https://docs.litellm.ai/docs/providers) 참조.

> 저장소의 프리셋을 직접 고치면 다음 `git pull`이 충돌할 수 있다. 자기 구성을 오래 쓸 거라면 프리셋을 복사해 두고 `.env`의 `LITELLM_CONFIG_FILE`로 그 사본을 가리킨다 — **저장소 밖의 절대경로도 된다.**

</details>

### B. 완전 로컬 — Ollama

먼저 Ollama를 설치하고 모델을 받는다. Ollama는 기본적으로 `127.0.0.1`에만 귀를 열어 컨테이너에서 닿지 못하므로, 설치 뒤 `OLLAMA_HOST=0.0.0.0`을 주고 다시 실행해야 한다.

<details>
<summary><b>Windows</b></summary>

```powershell
winget install Ollama.Ollama
ollama pull gemma3:4b
setx OLLAMA_HOST 0.0.0.0
```

그다음 **트레이의 Ollama를 종료했다 다시 실행한다.**

</details>

<details>
<summary><b>macOS</b></summary>

```bash
brew install ollama
ollama pull gemma3:4b
launchctl setenv OLLAMA_HOST 0.0.0.0
```

그다음 **Ollama를 종료했다 다시 실행한다.**

</details>

<details>
<summary><b>Linux</b></summary>

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull gemma3:4b
```

설치 스크립트가 systemd 서비스로 등록하므로, 환경변수는 서비스 설정에 넣는다.

```bash
sudo systemctl edit ollama
```

열린 편집기에 아래를 넣고 저장한다.

```
[Service]
Environment="OLLAMA_HOST=0.0.0.0"
```

```bash
sudo systemctl restart ollama
```

> ⚠️ 방화벽(ufw·firewalld)을 쓴다면 도커 브리지에서 오는 `11434` 접근을 허용해야 한다.

</details>

`0.0.0.0`으로 열렸는지 확인한다.

**Linux**

```bash
ss -lnt | grep 11434
```

**macOS**

```bash
lsof -nP -iTCP:11434 -sTCP:LISTEN
```

**Windows (PowerShell)**

```powershell
Get-NetTCPConnection -LocalPort 11434 -State Listen | Select-Object LocalAddress,LocalPort,State
```

**✅ 성공**: 주소가 `0.0.0.0` · `*` · `::` 중 하나로 나온다. `127.0.0.1`이면 환경변수가 적용되지 않은 것이다 — Ollama를 다시 실행하고 확인한다.

Ollama 템플릿으로 갈아탄다. 주소는 기본값 그대로 두면 된다.

**macOS · Linux**

```bash
cp .env.ollama.example .env
```

**Windows (PowerShell)**

```powershell
Copy-Item .env.ollama.example .env
```

컨테이너가 호스트를 찾는 `host.docker.internal`이라는 이름은 compose가 `extra_hosts`로 매핑해 두어 세 OS에서 모두 동작한다.

받은 모델 이름이 `gemma3:4b`가 아니라면 `.env`의 `LLM_MODEL`과 `config/litellm_config_ollama.yaml`의 `model_name`·`model:` 두 줄을 그 이름으로 바꾼다.

### C. 사내·온프렘 서버 — vLLM 등 OpenAI 호환

이미 띄워둔 OpenAI 호환 서버(vLLM·TGI·LM Studio 등)에 붙일 때는 vLLM 템플릿을 쓴다.

**macOS · Linux**

```bash
cp .env.vllm.example .env
```

**Windows (PowerShell)**

```powershell
Copy-Item .env.vllm.example .env
```

`.env`에서 서버 주소를 자기 것으로 바꾼다.

```
VLLM_API_BASE=http://192.168.0.20:8000/v1
```

서빙 중인 모델 이름은 서버마다 다르다. 확인한 뒤 `.env`의 `LLM_MODEL`과 `config/litellm_config_vllm.yaml`의 `model_name`·`model:` 두 줄을 그 이름으로 맞춘다.

```bash
curl http://192.168.0.20:8000/v1/models
```

> 서버가 API 키를 요구한다면(vLLM의 `--api-key` 등) 프리셋의 `api_key: none`을 `api_key: os.environ/LLM_API_KEY`로 바꾸고 `.env`의 `LLM_API_KEY` 주석을 푼다.

---

## 3. 기동

```bash
docker compose up -d --build
```

첫 실행은 이미지 내려받기와 프론트·BFF 빌드까지 하느라 **5~15분** 걸린다. 진행 상황은 다른 창에서:

```bash
docker compose ps
```

**✅ 성공**: 6개 서비스가 모두 `healthy`가 된다. `nextjs`는 `bff` → `keycloak` → `postgres` 순으로 기다렸다 뜨므로 가장 늦다.

로그인 설정이 만들어졌는지 확인한다.

**macOS · Linux**

```bash
docker compose logs keycloak | grep imported
```

**Windows (PowerShell)**

```powershell
docker compose logs keycloak | Select-String "imported"
```

**✅ 성공**: `Realm 'app-realm' imported` — 로그인 설정(realm·client·계정)이 `.env` 값으로 자동 생성됐다는 뜻이다. **최초 기동 때 한 번만** 만들어진다.

> 메모리가 모자라 빌드가 죽는다면(`Fail extracting tarball` 등) 나눠서 돌린다:
> `docker compose build bff` → `docker compose build nextjs` → `docker compose up -d`

---

## 4. 접속

브라우저에서 **<http://localhost:3010>** 을 연다. 로그인 화면으로 넘어간다.

| | |
|---|---|
| 아이디 | `appuser` |
| 비밀번호 | `appuser` |

로그인하면 채팅 화면이 나온다. 한 줄 보내서 응답이 **한 글자씩 흘러나오면** 프론트 → BFF → 게이트웨이 → 모델까지 전 구간이 붙은 것이다.

---

## 문제 해결

| 증상 | 원인과 조치 |
|---|---|
| `port is already allocated` | 그 포트를 쓰는 프로그램이 있다. 범인을 찾거나 `docker-compose.yml`의 `ports` 왼쪽 숫자를 바꾼다(바꿨다면 `.env`의 주소도 함께) → 아래 [포트 확인](#포트-확인) |
| 로그인 버튼을 누르면 **"There is a problem with the server configuration"** | nextjs가 Keycloak에 못 닿는 경우다. `docker compose logs --tail=30 nextjs`에 `Unable to connect`이 보이는지 확인한다. `.env`의 주소를 기동 후에 바꿨다면 [설정 다시 만들기](#로그인-설정-다시-만들기) |
| 로그인은 되는데 **채팅만 답이 없다** | 모델 연결 문제다. `docker compose logs --tail=50 litellm`을 본다. Ollama라면 십중팔구 `OLLAMA_HOST=0.0.0.0` 누락(2-B) |
| 채팅 요청이 **401/403** | BFF가 토큰을 거부한 것이다. `.env`의 `PUBLIC_KEYCLOAK_URL`을 기동 후에 바꾸지 않았는지 확인한다 — 바꿨다면 [설정 다시 만들기](#로그인-설정-다시-만들기) |
| `nextjs`가 계속 `starting` | 빌드 인자가 굳은 경우가 있다. `docker compose up -d --build nextjs` |
| postgres가 `initdb`에서 죽는다 | 오래된 libseccomp 호스트 이슈다. compose에 이미 `seccomp=unconfined` 우회가 들어 있으니, 그래도 죽는다면 도커를 갱신한다 |
| **(Linux)** `permission denied ... docker.sock` | `docker` 그룹 적용 전이다. 로그아웃 후 재로그인하거나 `sudo`를 붙인다 |

### 포트 확인

**macOS · Linux**

```bash
lsof -i :3010
```

**Windows (PowerShell)**

```powershell
netstat -ano | Select-String ":3010"
```

### 로그인 설정 다시 만들기

realm은 **최초 기동 때 한 번만** 만들어진다. 주소나 계정을 바꿨다면 지우고 다시 만든다.

```bash
docker compose down
docker volume rm agent-ogjg_postgres-data
docker compose up -d --build
```

> ⚠️ **로그인 계정과 대화 내용이 함께 지워진다.** `.env`의 `APP_USER`로 만들어지는 기본 계정은 자동으로 다시 생긴다.

---

## 내리기

```bash
docker compose down       # 컨테이너만 정리 (데이터 유지)
docker compose down -v    # 볼륨까지 삭제 (계정·대화 전부 삭제)
```

---

## 알아둘 제약

- **HTTPS가 아니다.** 브라우저가 "안전하지 않음"으로 표시하는 게 정상이다. 자물쇠가 필요하면 [INSTALL_r-proxy.md](INSTALL_r-proxy.md)로 caddy를 얹는다.
- **이 PC에서만 접속된다.** 기본 주소가 `localhost` 기준이라 그렇다 — 다른 기기에서 열려면 1단계의 접힌 절을 본다.
- **자격증명이 공개값이다.** `.env.example`에 그대로 적혀 있다. 직접 정한 값으로 바꾸려면 `infra/utils/init-env.ps1`(Windows) 또는 `infra/utils/init-env.sh`(macOS·Linux)로 `.env`를 만든다 — 비밀번호 2개만 입력받고 나머지 5개는 무작위로 채운다. **최초 기동 전에** 해야 한다(이미 띄운 뒤에 바꾸면 Keycloak·DB에 저장된 값과 어긋나 로그인이 막힌다).
- **Keycloak이 개발 모드(`start-dev`)로 뜬다.** 평문 HTTP를 허용하는 구성이다.
- **DB·게이트웨이 포트가 호스트의 모든 인터페이스에 열린다**(`5442` `4000` `8090` `8081`). 신뢰할 수 없는 네트워크에 물린 PC라면 방화벽으로 막는다.

---

## 다음

- 코드를 고치려면 → [CONTRIBUTING.md](CONTRIBUTING.md) (프론트·BFF를 호스트에서 직접 띄우는 개발 구성)
- HTTPS로 쓰려면 → [INSTALL_r-proxy.md](INSTALL_r-proxy.md)
- 구조가 궁금하면 → [README.md](README.md)
