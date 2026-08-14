---
status: In Progress
verification_date: 2026-08-13
owners:
  - 양성훈
  - 이우람
  - 김인안
  - 박진영
related_documents:
  - third-expansion-test-matrix.md
  - third-expansion-task-breakdown.md
  - third-expansion-implementation-plan.md
  - second-expansion-browser-verification.md
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/platform/web-004-supported-browser-matrix.md
  - ../07-adr/quality/test-001-automation-strategy.md
---

# 3차 확장 브라우저 검증 기록

## 1. 문서 목적과 판정 기준

`TST-E3-E2E-001`의 화면 폭·키보드·정상·빈·오류·복구 여정 증거를 남긴다. 대상은 공개 자연어 탐색과 맛집 코스, 관리자 AI 영상 추출 작업 조회와 예외 보정이다. 후속 추적은 [#165](https://github.com/team-youngkk/masit-on/issues/165)에서 한다.

판정 대상 화면 폭은 360px, 390px, 768px, 1280px, 1440px이고 브라우저 매트릭스는 [ADR-WEB-004](../07-adr/platform/web-004-supported-browser-matrix.md) 6.1절의 PC Chrome·Edge, Android Chrome이다. iPhone Safari는 같은 ADR에 따라 판정 대상이 아니다.

**이 문서는 부분 검증 상태다.** 확인한 항목과 확인하지 못한 항목을 6절에서 구분한다. 확인하지 못한 항목을 통과로 적지 않는다. 6절이 비워질 때 상태를 `Verified`로 바꾼다.

## 2. 확인 환경

| 항목 | 값 |
|---|---|
| 브라우저(측정·여정) | Chromium(Claude Code 내장) Chrome 148.0.7778.280 / Electron 42.7.0 |
| 브라우저(캡처) | PC Chrome 151.0.7922.76, PC Edge 151.0.4129.78 실빌드 headless |
| OS | Windows 11 Home 26200 |
| 프런트엔드 | `npm --prefix frontend run dev`, Node.js 24.18.0, `http://localhost:3000` |
| 백엔드 | `./gradlew bootRun --args='--spring.profiles.active=local'`, `http://localhost:8080` |
| 의존 서비스 | `docker compose up -d postgres redis wiremock` |
| 스키마 | 검증 당시 Flyway `V1`~`V4` 적용 (`#192` 통합 후; 현재 PR의 `V5` Preview 차단 migration 적용 전) |
| 기준 커밋 당시 스키마 | Flyway `V1`~`V7` 적용 (`35d0f94` 검증 시점) |
| 기준 커밋(3절 측정·4절 여정·3.1절 초기 화면 캡처) | `develop` `35d0f94`, 2026-08-12 |
| 기준 커밋(3.2절 여정 캡처) | `#182`·`#184` 병합 이후 이 브랜치 HEAD, 2026-08-13 |
| 화면 폭 | 브라우저 viewport를 360·390·768·1280·1440px으로 지정 |

화면 폭 측정값은 세로 스크롤바를 뺀 `documentElement.clientWidth`이므로 768px은 753px, 1280px은 1265px, 1440px은 1425px으로 관측된다.

애플리케이션까지 컨테이너로 올리는 `docker compose up -d --build app` 경로는 이 검증에서 사용하지 못했다. 이유는 5.1절에 있다.

### 2.1 검증용 로컬 Fixture

검증에만 쓰는 로컬 데이터를 넣었다. 운영 데이터가 아니며 `docker compose down -v`로 지워진다.

- 기존 맛집 3건에 확정 태그(`MENU_SUSHI`, `TASTE_SPICY`, `TASTE_LIGHT`)를 `ADMIN_OVERRIDE`로 연결했다.
- 코스 경로용 맛집 4건을 넣었다. 좌표는 `docker/wiremock/mappings`의 Kakao Mobility stub이 매칭하는 값(정상 `127.100100,37.100100`, 429 `127.300300,37.300300`)에 맞췄다.
- AI 추출 작업 4건(`QUEUED`, `FAILED`, `SUCCEEDED`+`AUTO_CONFIRMED`, `SUCCEEDED`+`AUTO_BLOCKED`)과 후보 Snapshot 2건, 실행 시도 5건을 넣었다.
- `AUTO_CONFIRMED` Snapshot이 가리키는 정식 Entity(맛집·유튜버·영상·방문)는 롤백 대상으로만 쓰는 전용 데이터이며 기존 데이터와 분리했다.
- 관리자 화면 확인용 로컬 `ADMIN` 계정을 `scripts/New-LocalAdmin.ps1`로 만들었다. 자격 증명은 이 문서에 적지 않는다.

실제 Kakao·YouTube·Gemini API는 호출하지 않았다. `AI_WORKER_ENABLED`와 `GEMINI_ENABLED`는 기본값 `false`이므로 Worker와 Provider 호출은 일어나지 않았다.

## 3. 화면 폭 5종 측정

각 폭에서 문서 가로 스크롤 발생 여부, viewport를 넘는 요소, 24px 미만 조작 대상, 접근 이름 없는 링크·버튼, 제목 계층, `alt` 없는 이미지, label 없는 폼 컨트롤을 측정했다.

| 화면 | 360px | 390px | 768px | 1280px | 1440px |
|---|---|---|---|---|---|
| 자연어 탐색 `/restaurants` (결과 표시 상태) | 통과 | 통과 | 통과 | 통과 | 통과 |
| 맛집 코스 `/course` (경로 결과 표시 상태) | 통과 | 통과 | 통과 | 통과 | 통과 |
| AI 작업 목록 `/admin/ai` | 통과 | 통과 | 통과 | 통과 | 통과 |
| AI 작업 상세 `/admin/ai/{jobId}` | 통과 | 통과 | 통과 | 통과 | 통과 |

전 조합에서 가로 스크롤 0건, viewport 초과 요소 0건, 접근 이름 없는 링크·버튼 0건, `alt` 속성 없는 이미지 0건이었다. 제목 계층은 네 화면 모두 `h1` 하나로 시작하고 단계를 건너뛰지 않는다.

AI 작업 상세의 `태그 코드 보정` 입력은 첫 측정에서 전 폭 177×21px로 24px 최소 조작 크기를 넘지 못했다. 5.3절에서 고친 뒤 재측정해 207×44px이 됐고 전 폭에서 24px 미만 조작 대상이 0건이다.

자연어 탐색에서 검출된 21px 높이 요소는 오류·빈 결과 안내 문장 안의 `기존 필터 검색으로 이동` 링크다. 문장 안에 있는 인라인 링크는 최소 크기 예외 대상이라 통과로 판정했다.

### 3.1 화면 캡처

공개 화면 초기 상태를 [`assets/e3-t12`](assets/e3-t12)에 보존한다. 3절 측정과 같은 실행(기준 커밋 `35d0f94`, 검증 시점 Flyway `V1`~`V7`, `#192` 통합 후 적용 결과 스키마 동일, 2.1절 Fixture)에서 찍었다.

| 화면 | 360px | 390px | 768px | 1280px | 1440px |
|---|---|---|---|---|---|
| 자연어 탐색 `/restaurants` | [PNG](assets/e3-t12/chrome-restaurants-360.png) | [PNG](assets/e3-t12/chrome-restaurants-390.png) | [PNG](assets/e3-t12/chrome-restaurants-768.png) | [PNG](assets/e3-t12/chrome-restaurants-1280.png) | [PNG](assets/e3-t12/chrome-restaurants-1440.png) |
| 맛집 코스 `/course` | [PNG](assets/e3-t12/chrome-course-360.png) | [PNG](assets/e3-t12/chrome-course-390.png) | [PNG](assets/e3-t12/chrome-course-768.png) | [PNG](assets/e3-t12/chrome-course-1280.png) | [PNG](assets/e3-t12/chrome-course-1440.png) |

교차 브라우저 참고로 PC Edge 실빌드의 1280px 캡처를 함께 둔다. [자연어 탐색](assets/e3-t12/edge-restaurants-1280.png) · [맛집 코스](assets/e3-t12/edge-course-1280.png).

캡처는 PC Chrome 151.0.7922.76과 PC Edge 151.0.4129.78 실빌드의 headless 모드로 찍었다. **Windows의 Chromium은 창 너비를 500px 미만으로 줄이지 못한다.** `--window-size=360`을 주면 레이아웃은 500px로 잡히고 이미지만 360px로 잘려 실제 화면과 다른 결과가 나온다. 360px과 390px은 500px 창 안에서 해당 폭의 iframe으로 문서를 그린 뒤 그 폭만큼 잘라 저장했다. 문서를 그린 폭은 이미지 폭과 같다.

### 3.2 여정 상태 캡처

4절 여정을 실제로 재현하며 상태별로 캡처했다. 4절 표의 모든 여정에 캡처가 있다. 여정 하나에 캡처가 둘인 경우(관리자 재시도는 입력·결과)와, 4절 표에 없는 화면 캡처(신규 영상 추가 입력·접수 결과)가 있어 일대일 대응은 아니다. 신규 영상 추가는 4절 여정 실행 이후 병합된 화면이라 3.2절에만 있고 4절 판정 대상이 아니다.

화면 폭은 1280px이고, 대표 화면 하나는 390px도 함께 남겼다.

| 구분 | 상태 | 캡처 |
|---|---|---|
| 자연어 | 조건 적용 완료 | [1280px](assets/e3-t12/journey-nl-normal-1280.png) · [390px](assets/e3-t12/journey-nl-normal-390.png) |
| 자연어 | 총 0건 | [1280px](assets/e3-t12/journey-nl-empty-1280.png) |
| 자연어 | 해석 실패 | [1280px](assets/e3-t12/journey-nl-failed-1280.png) |
| 자연어 | 백엔드 장애 | [1280px](assets/e3-t12/journey-nl-error-1280.png) |
| 자연어 | 다시 시도 복구 | [1280px](assets/e3-t12/journey-nl-recovered-1280.png) |
| 코스 | 빈 결과 | [1280px](assets/e3-t12/journey-course-empty-1280.png) |
| 코스 | 입력 부족(1개 선택) | [1280px](assets/e3-t12/journey-course-below-minimum-1280.png) |
| 코스 | 맛집 3개 선택 | [1280px](assets/e3-t12/journey-course-selected-1280.png) |
| 코스 | 추천 이동 순서 | [1280px](assets/e3-t12/journey-course-normal-1280.png) |
| 코스 | 외부 장애(429) | [1280px](assets/e3-t12/journey-course-provider-error-1280.png) |
| 코스 | 부분 실패 | [1280px](assets/e3-t12/journey-course-partial-1280.png) |
| 코스 | 선택 변경 복구 | [1280px](assets/e3-t12/journey-course-recovered-1280.png) |
| 코스 | 5분 만료 | [1280px](assets/e3-t12/journey-course-expired-1280.png) |
| 관리자 AI | 작업 목록 | [1280px](assets/e3-t12/journey-admin-list-1280.png) |
| 관리자 AI | 빈 목록 | [1280px](assets/e3-t12/journey-admin-list-empty-1280.png) |
| 관리자 AI | 신규 영상 추가 입력 | [1280px](assets/e3-t12/journey-admin-submit-form-1280.png) |
| 관리자 AI | 신규 영상 접수 결과 | [1280px](assets/e3-t12/journey-admin-submit-done-1280.png) |
| 관리자 AI | 자동 확정 상세 | [1280px](assets/e3-t12/journey-admin-detail-confirmed-1280.png) |
| 관리자 AI | 검수 사유 누락 거부 | [1280px](assets/e3-t12/journey-admin-reason-missing-1280.png) |
| 관리자 AI | 롤백 완료 | [1280px](assets/e3-t12/journey-admin-rollback-done-1280.png) |
| 관리자 AI | 자동 차단 상세 | [1280px](assets/e3-t12/journey-admin-detail-blocked-1280.png) |
| 관리자 AI | 자동 차단 후보 확정 거부 | [1280px](assets/e3-t12/journey-admin-confirm-rejected-1280.png) |
| 관리자 AI | 사후 폐기 완료 | [1280px](assets/e3-t12/journey-admin-discard-done-1280.png) |
| 관리자 AI | 재시도 입력 | [1280px](assets/e3-t12/journey-admin-retry-form-1280.png) |
| 관리자 AI | 재시도 새 작업 생성 | [1280px](assets/e3-t12/journey-admin-retry-done-1280.png) |

여정 캡처는 설치된 PC Chrome을 `--remote-debugging-port`로 띄우고 DevTools Protocol로 화면 폭 지정·입력·클릭·캡처를 보내 만들었다. 새 라이브러리는 추가하지 않았고 harness는 저장소에 두지 않았다. 재현 절차는 8절에 있다.

자연어 장애·복구는 백엔드를 내렸다 올려 만들었고, 코스 만료는 경로 생성 후 5분이 지난 뒤 찍었다. 관리자 신규 영상 추가 화면은 [#182](https://github.com/team-youngkk/masit-on/pull/182) 병합본 기준이다.

## 4. 사용자 여정

### 4.1 공개 자연어 탐색

| 여정 | 입력 | 결과 |
|---|---|---|
| 정상 | `성동구에서 매운 중식` | `조건 적용 완료 · 자치구: 성동구 / 음식 종류: 중식 / 태그: 매운맛`, 총 1건. 자치구·음식 종류·확정 태그 AND 조합이 그대로 적용된다 |
| 빈 결과 | `강남구에서 매운 일식` | 조건은 적용되고 총 0건. `적용한 조건과 일치하는 맛집이 없습니다`와 기존 필터 검색 이동 링크가 나온다 |
| 해석 실패 | `오늘 기분 좋은 곳 아무데나 추천해줘` | `해석 실패`, `적용하지 않은 조건: 지원되지 않는 조건`, 기존 필터 검색 이동 안내. 임의 해석 결과를 만들지 않는다 |
| 오류 | 백엔드 중단 상태에서 정상 문장 검색 | `자연어 검색을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.`와 `다시 시도` 버튼 |
| 복구 | 백엔드 재기동 후 `다시 시도` | 같은 문장이 정상 결과로 복구된다 |

### 4.2 공개 맛집 코스

| 여정 | 입력 | 결과 |
|---|---|---|
| 정상 | 좌표가 stub 정상 응답에 대응하는 맛집 3개 | `추천 이동 순서` 1·2·3, 구간별 `자동차 4.2km · 약 13분`·`3.1km · 약 10분`, 전체 `7.3km · 약 23분`, 생성·만료 시각과 도착 미보장 문구 |
| 빈 결과 | 검색 조건에 맞는 맛집 없음 | `조건에 맞는 맛집이 없습니다` |
| 입력 부족 | 1개 선택 | `코스 계산` 비활성, `코스를 계산하려면 맛집을 최소 2개 선택해야 합니다` |
| 외부 장애 | 429를 반환하는 출발 맛집 | `[외부 장애] 경로 계산 서비스를 일시적으로 사용할 수 없습니다`, `traceId`, 확인이 필요한 맛집 목록, `다시 시도` |
| 부분 실패 | 구간 수가 응답과 맞지 않는 조합 | `[부분 실패] 일부 구간의 경로 계산에 실패했습니다`, 추정값을 만들어 내지 않는다 |
| 만료 | 정상 결과를 5분 이상 방치 | `이 결과는 만료되어 거리·시간을 더 이상 최신으로 보여줄 수 없습니다`. 구간·전체 거리·시간이 화면에서 사라진다 |
| 복구 | `다시 시도` 후 `선택 수정`으로 조합 변경 → 재계산 | 정상 결과로 복구된다. `다시 시도`는 매번 새 `traceId`로 재요청한다 |

현재 위치를 묻는 UI는 없고 화면 안내도 현재 위치·영업시간·실시간 교통을 쓰지 않는다고 밝힌다.

### 4.3 관리자 AI 작업 조회와 예외 보정

| 여정 | 입력 | 결과 |
|---|---|---|
| 정상 목록 | 필터 없음 | 4건이 실행 상태·유입 경로·검수 상태·버전·시도 횟수와 함께 나온다 |
| 빈 목록 | 실행 상태 `실행 중` | `조건에 맞는 AI 작업이 없습니다`, 총 0건 |
| 상세 | `AUTO_CONFIRMED` 작업 | 후보 필드·태그·신뢰도·근거(TIMESTAMP 구간)를 보여주고 `입력 원문, 보완 텍스트, Provider 응답 전문과 비밀정보는 표시하지 않습니다`를 명시한다. 사전 승인 없이 롤백만 가능하다 |
| 오류 | 검수 사유 없이 `ROLLBACK` | `요청 값을 확인해 주세요`와 `문의 ID`. 검수 상태는 `AUTO_CONFIRMED` 그대로다 |
| 롤백 | 사유 입력 후 `ROLLBACK` | 검수 상태가 `MANUAL_OVERRIDE`로 바뀌고 조치 버튼이 사라진다. 등록됐던 맛집·방문이 `PRIVATE`로 내려가 공개 목록에서 빠지고 `ai_extraction_manual_review`에 `ROLLBACK` 이력이 남는다 |
| 자동 차단 확정 거부 | `AUTO_BLOCKED` 작업에 `CONFIRM` | `후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다`. 맛집 수 변화 0건, 검수 상태 불변. 누락 필드가 있는 후보는 수동 확정으로도 통과하지 못한다 |
| 사후 폐기 | `AUTO_BLOCKED` 작업에 `DISCARD` | 검수 상태가 `MANUAL_OVERRIDE`로 바뀐다 |
| 재시도 | `FAILED` 작업에 보완 텍스트·사유 입력 | 새 `ADMIN` 작업이 `QUEUED`로 생성된다. 보완 텍스트는 암호문과 키 ID로만 저장되고 24시간 만료가 붙는다 |

재시도는 기본 로컬 설정에서 실패한다. 원인과 조치는 5.2절에 있다.

## 5. 확인 과정에서 나온 결함

### 5.1 `docker compose up -d --build app`이 기동하지 못한다

`masiton-app` 컨테이너가 다음 오류로 즉시 종료된다.

```
java.lang.IllegalStateException: Kakao Mobility base URL is not an allowed provider endpoint
	at com.masiton.restaurant.infrastructure.external.config.KakaoMobilityProperties.validateBaseUrl
```

`docker-compose.yml`은 `KAKAO_MOBILITY_BASE_URL: http://wiremock:8080`을 넘기는데, `KakaoMobilityProperties.validateBaseUrl`은 운영 호스트이거나 `localhost`·`127.0.0.1`·`::1`인 경우만 허용한다. 컨테이너 네트워크의 서비스 이름 `wiremock`은 어느 쪽에도 들어가지 않는다.

[CLAUDE.md 5절](../../CLAUDE.md)이 통합 실행 명령으로 안내하는 경로가 현재 `develop`에서 동작하지 않는다. 이 검증은 `./gradlew bootRun`으로 우회했다. 조치 대상은 `docker-compose.yml`(이우람 소유) 또는 허용 목록(WS-16)이며 이 문서에서 고치지 않는다.

### 5.2 AI 재시도의 보완 텍스트 암호화 키가 로컬 설정에 없었다 (수정함)

`FAILED` 작업 재시도는 보완 텍스트가 필수(`required`)라 항상 `ADMIN_TEXT` 입력 모드로 동작하고, 저장 전에 `AI_TEMPORARY_INPUT_KEY_ID`·`AI_TEMPORARY_INPUT_KEY`로 암호화한다. 두 값은 `.env.example`, `docker-compose.yml`, `scripts/Initialize-LocalJwt.ps1`, 문서 어디에도 없다.

기본 로컬 설정에서 재시도를 누르면 `503 AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE`이 나고 새 작업이 만들어지지 않는다.

같은 변경 단위에서 회원 Action 메일 키와 같은 방식으로 맞췄다. `.env.example`에 두 항목을 추가하고, `scripts/Initialize-LocalJwt.ps1`이 32-byte AES 키를 만들어 `.env`와 현재 세션에 넣고, `docker-compose.yml`이 앱 컨테이너로 넘기고, [README](../../README.md)가 두 값을 안내한다. 로컬·운영 모두 저장소에 값을 두지 않는 원칙은 그대로다.

### 5.3 AI 작업 상세의 `태그 코드 보정` 입력이 24px보다 작았다 (수정함)

첫 측정에서 전 폭 177×21px이었다. 문장 안의 인라인 링크가 아니라 독립 폼 컨트롤이므로 최소 크기 예외에 해당하지 않는다.

`AiVideoExtractionScreen.module.css`의 `min-height: var(--control-min-height)`가 `.filterRow select`, `.retryForm input`, `.retryForm textarea`에만 걸려 있고 후보 카드 안의 입력은 빠져 있었다. 셀렉터에 `.candidate input`을 더해 207×44px이 됐고 3절 재측정에서 전 폭이 통과한다.

### 5.4 AI 작업 접수 경로가 계약대로 열려 있지 않았다 (후속 구현으로 해소)

검증 중 관리자가 새 추출 작업을 요청할 방법을 찾다가 확인했다. 검증 시점(기준 커밋 `35d0f94`)에는 두 항목 모두 계약에는 있고 구현이 없었다.

| 항목 | 계약 | 검증 시점 백엔드 | 검증 시점 프런트엔드 |
|---|---|---|---|
| 신규 영상 추가·추출 요청 | [API 3.1](../05-specs/api/admin/ai-video-extraction-api.md) `POST /api/admin/ai/video-extractions`, `PR-AIEXTRACT-001` | 있음 | **없음** |
| 채널 감시 활성화·중지 | [API 3.6](../05-specs/api/admin/ai-video-extraction-api.md) `PUT /api/admin/ai/youtube-channel-watches/{creatorId}`, `PR-AIEXTRACT-008` | **없음** | **없음** |

[AI 영상 정보 추출 PRD](../04-product/prd/admin/ai-video-information-extraction.md) 5절은 관리자가 신규 영상 추가 화면에서 URL을 제출해 초기 데이터 적립과 Webhook 누락 보완을 직접 접수한다고 정한다. 관리자 화면에는 목록·상세·재시도·검수만 있고 접수 진입점이 없어, 지금은 API를 직접 호출해야 작업을 만들 수 있다.

`YoutubeChannelWatchStore`에는 `find`만 있고 쓰기 메서드가 없다. `youtube_channel_watch` 테이블은 `V4`에 있지만 행을 넣는 코드가 애플리케이션에 없어 감시 채널을 등록할 수단이 API에도 화면에도 없다. Webhook 접수 자체는 구현돼 있으나 구독을 열 수 없어 실제 알림 경로가 끝까지 이어지지 않는다.

두 항목은 `E3-T12` 범위 밖이며 [#180](https://github.com/team-youngkk/masit-on/issues/180) 채널 감시 설정 API와 [#181](https://github.com/team-youngkk/masit-on/issues/181) 신규 영상 추가 화면으로 분리했다.

두 이슈는 각각 [#184](https://github.com/team-youngkk/masit-on/pull/184)와 [#182](https://github.com/team-youngkk/masit-on/pull/182)로 구현돼 `develop`에 병합됐고, 이 브랜치도 병합본을 포함한다. `AdminYoutubeChannelWatchController`와 관리자 접수 폼이 생겨 위 공백은 해소됐다. **다만 이 문서의 3절 측정과 4절 여정은 병합 전 기준 커밋에서 수행했으므로 두 신규 화면·API는 검증 대상에 없다.** 6절에 미검증으로 남긴다.

### 5.5 관측만 한 항목

- `AiExtractionJobService`의 `YouTube videoUrl is invalid.` 메시지가 관리자 화면에 영어 그대로 나온다. 다른 오류 안내는 모두 한국어다.
- 관리자 화면에도 공개 헤더(`맛집 탐색`·`로그인` 등)가 함께 노출된다. MVP부터 이어진 구조이며 3차 확장에서 생긴 변화가 아니다.
- 개발 모드에서 `NEXTJS-PORTAL` 요소가 Tab 순회에 한 번 들어온다. Next.js 개발 오버레이이며 프로덕션 빌드에는 없다.
- `scripts/New-LocalAdmin.ps1`은 PATH의 `java`가 JDK 21이 아니면 `비밀번호 해시 생성에 실패했습니다`만 남기고 원인을 알려주지 않는다. 이 환경은 PATH가 JDK 8이라 JDK 21 경로를 앞에 붙여 실행했다.

## 6. 확인하지 못한 항목

| 항목 | 상태 | 이유 | 다음 단계 |
|---|---|---|---|
| Android Chrome 등 모바일 실단말의 화면과 여정 | 미검증 | 실단말 확인을 하지 않았다. 3.1·3.2절 캡처는 PC Chrome·Edge 실빌드의 좁은 폭 렌더링이며 실단말 동작이 아니다 | 담당자가 실단말에서 3절 화면과 4절 여정을 확인한다 |
| PC Edge 실빌드의 여정 확인 | 미검증 | Edge는 3.1절 초기 화면 캡처만 남겼고 4절 여정은 실행하지 않았다 | 담당자가 Edge에서 4절 여정을 확인한다 |
| iPhone Safari | 미검증(판정 대상 아님) | [ADR-WEB-004](../07-adr/platform/web-004-supported-browser-matrix.md) | 해제 조건 충족 시 매트릭스로 되돌린다 |
| 배포 환경(`masiton.click`)의 3차 확장 화면 | 미검증 | 이 검증은 로컬 `develop` 기준이다 | 3차 확장 배포 후 같은 여정을 확인한다 |
| 색 대비, 보조기기 낭독 | 미검증 | 이번 확인은 DOM 측정과 키보드 순회 기반이며 대비 계산과 실제 보조기기 확인을 포함하지 않는다 | 담당자가 수동 확인 범위를 정한다 |
| 컨테이너 통합 실행에서의 동일 여정 | 미검증 | 5.1절 결함으로 앱 컨테이너가 기동하지 못한다 | 5.1절 조치 후 재확인한다 |
| AI Worker가 실제로 도는 상태의 여정 | 미검증 | Worker와 Gemini 게이트가 모두 `false`다. 작업 상태는 Fixture로 만들었다 | `E3-T13` 운영 측정에서 확인한다 |
| 관리자 신규 영상 추가 화면의 폭 측정과 여정 판정 | 미검증 | 화면 캡처는 3.2절에 있으나, 3절 폭 측정과 4절 여정은 [#182](https://github.com/team-youngkk/masit-on/pull/182) 병합 전 기준 커밋에서 수행해 이 화면을 포함하지 않는다 | 담당자가 병합본에서 3절 측정과 4절 여정을 같은 기준으로 확인한다 |
| Webhook 신규 영상 접수 여정 | 미검증 | 검증 시점에는 채널 감시를 등록할 수단이 없었고, [#184](https://github.com/team-youngkk/masit-on/pull/184)로 구현된 API는 이 문서의 실행 이후에 병합됐다(5.4절) | 담당자가 감시 채널을 활성화한 뒤 알림 접수까지 확인한다 |

## 7. 키보드 확인

Tab 순회로 세 화면의 초점 이동을 기록했다.

- 자연어 탐색: 헤더 진입점 → 자연어 문장 입력 → `문장 검색` → 기존 필터(이름·자치구·음식·유튜버) → `검색` → 결과 카드 링크 순으로 이동한다.
- 맛집 코스: 헤더 → 검색 폼 → 결과별 `코스에 추가` → 선택 목록의 `위로`·`아래로`·`삭제` → `코스 계산` → 결과 패널의 `선택 수정`·`새 경로 조회`까지 모두 도달하고 순환한다. 비활성 버튼(첫 항목의 `위로`, 마지막 항목의 `아래로`)은 순회에서 빠진다.
- AI 작업 상세: 헤더 → 관리자 메뉴 → `작업 목록` → `새로고침` → `태그 코드 보정` → 상태별 조치 버튼까지 도달한다.

모든 초점 대상이 `outline: solid 2px`를 갖는다. 초점만으로 조작 불가능한 컨트롤은 없었다. 선택 목록 조작 버튼은 `아주 긴 이름을 가진 마라탕 전문점 성수 본점 위로 이동`처럼 맛집 이름을 포함한 접근 이름을 갖는다.

## 8. 재현 절차

1. `docker compose up -d postgres redis wiremock`
2. `.env`에 [README](../../README.md)의 로컬 JWT·메일·AI 보완 텍스트·Rate limit 값이 모두 있어야 한다. 예전 `.env`를 그대로 쓰면 `AI_TEMPORARY_INPUT_*` 줄이 없어 초기화 스크립트가 값을 채우지 못하고 AI 재시도가 실패한다(5.2절).
3. `./gradlew bootRun --args='--spring.profiles.active=local'` 후 `/internal/health/ready`가 `200`인지 확인한다. `.env`는 Docker Compose만 읽으므로 값을 셸 환경 변수로 넘겨야 한다.
4. 2.1절 Fixture를 넣는다. 확정 태그 연결, Mobility stub 좌표에 맞춘 맛집, AI 작업·Snapshot·실행 시도가 있어야 4절 여정을 모두 볼 수 있다.
5. `scripts/New-LocalAdmin.ps1`로 로컬 `ADMIN` 계정을 만든다. PATH의 `java`가 JDK 21이어야 한다.
6. `npm --prefix frontend run dev` 후 각 폭에서 3절 화면과 4절 여정을 확인한다.
7. 3.1절 초기 화면 캡처는 Chrome·Edge 실빌드의 `--headless=new --screenshot`으로 만든다. 768px 이상은 `--window-size`를 그대로 쓰고, 360·390px은 3.1절대로 500px 창 안의 iframe으로 그린 뒤 해당 폭만큼 잘라 저장한다.
8. 3.2절 여정 캡처는 Chrome을 `--headless=new --remote-debugging-port=9222`로 띄운 뒤 DevTools Protocol로 조작한다. `Emulation.setDeviceMetricsOverride`로 폭을 지정하면 최소 창 너비 제약을 받지 않는다. React 제어 입력은 native setter로 값을 넣고 `input` 이벤트를 보내야 상태가 갱신된다. 화면은 `Page.captureScreenshot`으로 저장한다.
9. 오류·복구 여정은 백엔드를 내렸다 올려 확인한다. 코스 외부 장애는 stub이 429를 주는 좌표를 출발 맛집으로 두면 재현된다.
