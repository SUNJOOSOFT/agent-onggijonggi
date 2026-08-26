# 표준단어 목록 (사람이 관리하는 원천)

> 이 파일만 손으로 고친다. `scripts/glossary/data-glossary.md`(축약 사전)는 여기서 **생성**되므로 직접 편집하지 않는다.
> 생성: `node scripts/build-glossary.mjs`

## 축약 규칙

```
1. 소문자화
2. 예외 테이블에 있으면 그 값 사용 → 종료
3. 길이 ≤ 4 → 원형 그대로
4. 길이 ≥ 5 → 모음(a,e,i,o,u) 제거 (첫 글자가 모음이면 첫 글자만 보존),
              중복 압축 없이 앞 3자
5. 충돌·불량이 나오면 생성기가 실패한다 → 아래 예외 테이블에 등재
```

물리명은 PostgreSQL 관례인 **snake_case**. 조합(`mbr_id`)은 등재 대상이 아니다 — 단어만 등재한다.

## 단어

> 실제 스키마에서 쓰는 단어만 등재한다(사전을 미리 채우지 않는다).

| 영문 | 뜻 |
|---|---|
| `access` | 접근 |
| `answer` | 답변 |
| `app` | 애플리케이션 |
| `at` | 시점 |
| `attempt` | 시도 |
| `audit` | 감사 |
| `by` | 주체 |
| `chat` | 대화 |
| `chunk` | 조각 |
| `chunking` | 조각화 |
| `content` | 본문 |
| `count` | 개수 |
| `created` | 생성됨 |
| `dim` | 차원 |
| `document` | 문서 |
| `embedding` | 임베딩 |
| `error` | 오류 |
| `file` | 파일 |
| `indexed` | 색인됨 |
| `indexing` | 색인 실행 |
| `json` | JSON |
| `key` | 키 |
| `last` | 마지막 |
| `location` | 위치 |
| `log` | 로그 |
| `member` | 회원 |
| `message` | 메시지 |
| `model` | 모델 |
| `name` | 이름 |
| `next` | 다음 |
| `original` | 원본 |
| `outcome` | 결과 |
| `path` | 경로 |
| `pending` | 대기 중 |
| `question` | 질문 |
| `requester` | 요청자 |
| `role` | 역할 |
| `run` | 실행 |
| `session` | 세션 |
| `source` | 근거 |
| `subject` | 주체 식별자 |
| `tag` | 태그 |
| `text` | 텍스트 |
| `title` | 제목 |
| `trace` | 추적 |
| `updated` | 수정됨 |
| `uploaded` | 업로드됨 |
| `user` | 사용자 |
| `version` | 버전 |

## 예외

> 알고리즘 결과를 덮어쓴다. **사유를 반드시 적는다.**

| 영문 | 축약 | 사유 |
|---|---|---|
| `identifier` | `id` | 관용어(팀 합의) |
| `password` | `pw` | 관용어(팀 합의) |
| `document` | `doc` | 관용어 — 알고리즘은 `dcm` |
| `version` | `ver` | 관용어 — 알고리즘은 `vrs` |
| `message` | `msg` | 관용어 — 알고리즘은 `mss` |
| `requester` | `req` | 관용어 — 알고리즘은 `rqs` |
| `member` | `mbr` | 관용어 — 알고리즘은 `mmb` |
| `location` | `loc` | 관용어 — 알고리즘은 `lct` |
| `subject` | `subj` | 관용어 — 알고리즘은 `sbj` |
| `indexing` | `idx` | 관용어 — 알고리즘은 `ind`로 `indexed`와 충돌 |
| `session` | `sess` | 알고리즘 결과 `sss`가 불량(동일 문자 반복) |
| `chunking` | `chnk` | `chunk`와 구분(알고리즘은 둘 다 `chn`) |
| `created` | `created` | 관용 — `created_at`은 범용 관례라 원형 유지 |
| `updated` | `updated` | 관용 — `updated_at`은 범용 관례라 원형 유지 |
| `indexed` | `indexed` | 관용 — `indexed_at` 대칭 |
| `uploaded` | `uploaded` | 관용 — `uploaded_by` 대칭 |
| `title` | `title` | 알고리즘 `ttl`은 Time-To-Live로 통용돼 혼동 |
| `content` | `content` | 알고리즘 `cnt`는 count로 통용돼 혼동 |
| `chunk` | `chunk` | 알고리즘 `chn`은 의미가 불명확 |
| `keycloak` | `keycloak` | 고유명사 — 축약하지 않음 |
| `department` | `dept` | 관용어 — 알고리즘 결과 `dpr`이 불명확 |
| `status` | `status` | 관용 유지 — 알고리즘 결과 `stt`가 부자연스러움(`content`·`title`과 같은 패턴) |
| `current` | `cur` | 관용어 — 알고리즘 결과 `crr`이 부자연스러움 |
| `fingerprint` | `fgpt` | 관용어 — 알고리즘 결과 `fng`가 의미 불명확, 4자로 늘려 가독성 확보 |
| `deleted` | `deleted` | 관용 유지 — `deleted_at`/`deleted_by` 대칭, 기존 `created`/`updated`/`indexed`/`uploaded`와 동일 패턴 |
| `sequence` | `seq` | 관용어 — 알고리즘 결과 `sqn`이 불명확, 업계 통용 축약 |

### 복수형 정책

**단수만 등재하고 물리명도 단수로 쓴다.** 배열임은 타입(`text[]`)이 말해주므로 이름까지 복수일 필요는 없다.
따라서 `access_tags` → `acc_tag`, `sources_json` → `src_json`.
붙어 있는 합성어는 분해한다 — `filename` → `file_name`.
