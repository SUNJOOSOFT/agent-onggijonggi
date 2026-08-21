# 모델 연결 — 기본 말고 다른 구성

[INSTALL.md](INSTALL.md) 2단계는 가장 빠른 길 하나(Gemini)만 다룬다. 그 밖의 구성은 여기에 있다.

- [Claude와 OpenAI 함께 쓰기](#claude와-openai-함께-쓰기)
- [내 PC의 Ollama](#내-pc의-ollama)
- [사내 서버 (vLLM 등 OpenAI 호환)](#사내-서버-vllm-등-openai-호환)
- [모델을 목록에 더하기](#모델을-목록에-더하기)

---

## 고르는 법

`.env` 템플릿과 LiteLLM 프리셋이 **짝**으로 돼 있다. 템플릿을 복사하면 짝이 되는 프리셋이 따라온다 — 설정 파일을 직접 고칠 일이 없다.

| 템플릿 | 무엇을 여는가 | 채울 곳 |
|---|---|---|
| `.env.example` | **기본** — Gemini 하나 | `LLM_API_KEY` |
| `.env.multi.example` | Gemini·Claude·OpenAI 셋 | 공급자별 키 |
| `.env.ollama.example` | 내 PC의 Ollama | `LLM_MODEL` |
| `.env.vllm.example` | 사내·온프렘 OpenAI 호환 서버 | `VLLM_API_BASE`·`LLM_MODEL` |

네 템플릿은 **여는 모델만 다르고 나머지는 같다.** 각각 `LITELLM_CONFIG_FILE`로 짝이 되는 `config/litellm_config*.yaml`을 가리키므로, 저장소의 설정 파일이 그대로라 다음 `git pull`이 충돌하지 않는다.

> ⚠️ **이미 `.env`를 만들어 고친 뒤라면 통째로 덮어쓰지 않는다.** LiteLLM 절만 옮겨 적는다 — 자격증명을 직접 정했다면 그 값이 날아간다.

---

## Claude와 OpenAI 함께 쓰기

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

---

## 내 PC의 Ollama

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

---

## 사내 서버 (vLLM 등 OpenAI 호환)

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

## 모델을 목록에 더하기

쓰고 있는 프리셋의 `model_list`에 항목을 더한다. `model_name`이 화면 드롭다운에 그대로 뜨는 이름이고, `model:`이 LiteLLM에게 알려주는 실제 공급자·모델이다.

```yaml
  - model_name: 화면에-보일-이름
    litellm_params:
      model: 공급자/실제-모델명
      api_key: os.environ/키를_담을_환경변수
```

새 환경변수를 쓴다면 `docker-compose.yml`의 litellm 서비스에도 그 변수를 전달해야 한다. LiteLLM이 지원하는 공급자 목록은 [docs.litellm.ai/docs/providers](https://docs.litellm.ai/docs/providers) 참조.

> 저장소의 프리셋을 직접 고치면 다음 `git pull`이 충돌할 수 있다. 자기 구성을 오래 쓸 거라면 프리셋을 복사해 두고 `.env`의 `LITELLM_CONFIG_FILE`로 그 사본을 가리킨다 — **저장소 밖의 절대경로도 된다.**

---

## 다음

- 설치 본문으로 → [INSTALL.md](INSTALL.md)
- HTTPS로 쓰려면 → [INSTALL_r-proxy.md](INSTALL_r-proxy.md)
