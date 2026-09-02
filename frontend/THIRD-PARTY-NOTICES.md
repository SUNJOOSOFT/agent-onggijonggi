# 서드파티 고지

이 프로젝트 자체는 [Apache License 2.0](../LICENSE)을 따른다. 이 문서는 **빌드 산출물(도커 이미지)에 함께 담겨 배포되는** 서드파티 구성요소 중, 별도 고지가 필요한 것을 적는다.

> 이 디렉터리의 `LICENSE.apache-2.0`은 저장소 루트 `LICENSE`와 같은 Apache-2.0 전문이다.
> 도커 빌드 컨텍스트가 `frontend/`라 루트 파일을 이미지에 담을 수 없어 사본을 둔다.
> 두 파일은 항상 같아야 한다.

저장소 자체는 서드파티 바이너리를 담고 있지 않다(`node_modules`는 추적하지 않는다). 아래 내용은 `frontend` 이미지를 빌드해 배포할 때 해당한다.

---

## libvips (LGPL-3.0-or-later)

`frontend` 런타임 이미지에는 `sharp`가 의존하는 **libvips** 네이티브 라이브러리가 포함된다. Next.js가 이미지 최적화를 위해 자동으로 끌어오는 전이 의존성이다.

| 항목 | 내용 |
|---|---|
| 패키지 | `@img/sharp-libvips-linux-x64`, `@img/sharp-libvips-linuxmusl-x64` |
| 라이선스 | **LGPL-3.0-or-later** |
| 원본 소스 | https://github.com/libvips/libvips |
| 라이선스 전문 | https://www.gnu.org/licenses/lgpl-3.0.html |

**교체·재링크**: libvips는 `sharp`가 런타임에 동적으로 불러오는 네이티브 애드온이다. 이미지 안의 `node_modules/@img/` 아래 해당 패키지를 교체하면 다른 빌드의 libvips로 바꿔 쓸 수 있다. 이 프로젝트는 그 교체를 막는 어떤 조치도 하지 않는다.

> 참고: `sharp` 본체(`sharp`, `@img/sharp-linux*-x64`)는 Apache-2.0이다. 위 LGPL 항목은 네이티브 라이브러리(`@img/sharp-libvips-*`)에 해당한다.

---

## caniuse-lite (CC-BY-4.0)

`frontend` 런타임 이미지에는 `caniuse-lite`가 포함된다. Next.js가 브라우저 호환성 판단에 쓰는 전이 의존성이며, Dockerfile이 `node_modules`를 통째로 복사하므로 이미지에 함께 들어간다.

| 항목 | 내용 |
|---|---|
| 패키지 | `caniuse-lite` |
| 라이선스 | **CC-BY-4.0** — 담고 있는 브라우저 지원 데이터가 Can I Use 프로젝트의 저작물이다 |
| 원본 | https://github.com/browserslist/caniuse-lite · https://caniuse.com |
| 라이선스 전문 | https://creativecommons.org/licenses/by/4.0/ |

CC-BY-4.0은 저작자 표시를 요구한다 — **이 항목이 그 표시다.** 데이터는 수정하지 않고 그대로 재배포한다.

---

## 그 밖의 의존성

2026-08-07 기준으로 백엔드 런타임 클래스패스 178개와 프론트엔드 323개의 라이선스를 조사해, permissive가 아니거나 듀얼 라이선스인 것을 아래에 밝힌다. 듀얼 라이선스는 이 프로젝트가 **선택한 쪽**을 함께 적는다.

조사는 패키지 메타데이터(POM·`package.json`)와 아카이브 안의 라이선스 파일에 근거한다. 의존성이 바뀌면 결과도 달라지므로, 배포 전에는 그 시점의 의존성으로 다시 확인하는 것을 권한다.

### `backend/api` (Gradle — 런타임 클래스패스 178개)

대부분 Apache-2.0이며, 그 외는 다음과 같다.

| 구성요소 | 라이선스 | 선택 |
|---|---|---|
| `ch.qos.logback:logback-classic`·`logback-core` | EPL-2.0 **OR** LGPL-2.1 | **EPL-2.0** |
| `jakarta.annotation:jakarta.annotation-api`<br>`jakarta.transaction:jakarta.transaction-api` | EPL-2.0 **OR** GPL-2.0 with Classpath Exception | **EPL-2.0** |
| `jakarta.persistence:jakarta.persistence-api` | EPL-2.0 **OR** EDL-1.0 | **EDL-1.0** |
| `org.aspectj:aspectjweaver` | EPL-2.0 | — |
| `jaxb-runtime`·`jaxb-core`·`txw2`·`jakarta.xml.bind-api`<br>`jakarta.activation-api`·`angus-activation`·`istack-commons-runtime` | EDL-1.0 | — |
| `org.postgresql:postgresql` | BSD-2-Clause | — |
| `org.antlr:ST4`·`antlr4-runtime` | BSD-3-Clause | — |
| `org.slf4j:slf4j-api`·`com.knuddels:jtokkit` | MIT | — |
| `org.reactivestreams:reactive-streams` | MIT-0 | — |
| `org.hdrhistogram:HdrHistogram`·`org.latencyutils:LatencyUtils` | CC0-1.0 (일부 BSD-2-Clause 병기) | — |

### `frontend` (npm — 323개)

MIT 287 · Apache-2.0 14 · ISC 13 · BSD 계열 4 · `MIT OR Apache-2.0` 2. 그 외는 다음과 같다.

| 구성요소 | 라이선스 | 선택 |
|---|---|---|
| `@img/sharp-libvips-*` | LGPL-3.0-or-later | 위 libvips 절 참고 |
| `caniuse-lite` | CC-BY-4.0 | 위 절 참고 |
| `json-schema` | AFL-2.1 **OR** BSD-3-Clause | **BSD-3-Clause** |

> 위 구성요소는 모두 **수정 없이 그대로** 재배포한다. 듀얼 라이선스 항목은 "선택" 열에 적힌 라이선스를 따른다.

---

## 폰트

`frontend/public/fonts/`의 Geist 폰트는 SIL Open Font License 1.1을 따른다 — [`public/fonts/OFL.txt`](public/fonts/OFL.txt) 참고.

## 포크 원본

`frontend/`는 Vercel [`ai-chatbot`](https://github.com/vercel/ai-chatbot)(Apache-2.0) 템플릿에서 시작했다. 원본 저작권 고지는 [`LICENSE`](LICENSE)에 유지하며, 변경 내역은 [README](../README.md#라이선스)에 적었다.
