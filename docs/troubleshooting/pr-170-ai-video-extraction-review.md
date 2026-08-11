---
related_documents:
  - README.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
---

# PR #170 리뷰 트러블슈팅: AI 영상 추출 Provider·Webhook 리뷰와 CI 실패 반영

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#170 AI 영상 추출 접수·Webhook·Gemini Provider 구현](https://github.com/team-youngkk/masit-on/pull/170) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-11 |
| 범위 | Gemini 요청·응답 경계, S1 Schema, Webhook URL 정규화, Provider 오류 분류, WireMock CI 실패 |
| 주 문제 유형 | 애플리케이션·외부 연동·보안·테스트 |
| 기존 기록 | 없음. 이번 PR의 AI Provider·Webhook 구현을 대상으로 새 기록을 작성했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [빈 응답 SCHEMA](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3754985030) | Jackson `readTree`의 null 결과를 `SCHEMA`로 정규화 | 외부 연동·테스트 | 수정 필요 | envelope와 S1 payload의 null을 fail-closed 처리하고 빈 2xx 회귀 테스트 추가 | `GeminiHttpVideoExtractionAdapterTest` 통과 |
| [보완 텍스트 경계](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3754985032) | 관리자 입력을 시스템 지시와 분리하고 Prompt Injection의 후보 확정을 차단 | 보안 | 수정 필요 | Gemini `systemInstruction`과 사용자 콘텐츠를 분리하고 비신뢰 데이터 경계를 추가. 근거 없는 후보 회귀 테스트 추가 | 시스템 지시에 주입 문자열이 없고 응답은 `SCHEMA`로 거부됨 |
| [근거 위치 Schema](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3755001384) | `TIMESTAMP`·`TEXT_RANGE` 위치 필드를 요청 Schema에 선언 | 애플리케이션·외부 연동 | 수정 필요 | `startMs`, `endMs`, `startOffset`, `endOffset`, `sourceHash`를 Schema에 추가 | 두 근거 유형 정상 응답 테스트 통과 |
| [Webhook http scheme](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3755012307) | Atom alternate URL의 `http` 허용과 canonical `https` 저장 | 외부 연동 | 수정 필요 | `http`·`https`를 모두 검증하고 반환 URL을 `https://www.youtube.com/watch?v={videoId}`로 정규화 | Parser·Controller fixture가 공식 `http` 입력으로 통과 |
| [근거 Schema 중복 지적](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3755012315) | 검증기가 요구하는 위치 필드를 Schema와 일치 | 애플리케이션·외부 연동 | 수정 필요 | 위 근거 위치 Schema 수정으로 함께 처리 | `TIMESTAMP`·`TEXT_RANGE` 정상 응답 테스트 통과 |
| [`missingFields` enum](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3755012317) | 허용 필드 목록과 완전·부분 결과 결합 규칙을 요청에 전달 | 애플리케이션 | 수정 필요 | enum을 S1 허용 목록과 일치시키고 `systemInstruction`에 `COMPLETE`/`PARTIAL` 규칙 명시 | 요청 JSON Schema와 지시 문구 회귀 검증 |
| [4xx 분류](https://github.com/team-youngkk/masit-on/pull/170#discussion_r3755012321) | quota 차단과 요청 오류를 다른 범주로 분류 | 외부 연동 | 수정 필요 | `401`·`403`·`429`는 `PROVIDER_BLOCKED`, 그 밖의 4xx는 `UPSTREAM`, `408`은 `TIMEOUT`으로 분류 | 401·403·429·400·404·415 테스트 통과 |

## 3. 문제 현상과 발생 조건

- CI 오류: [백엔드 빌드·테스트 job](https://github.com/team-youngkk/masit-on/actions/runs/31454150011/job/93664403801)에서 `GeminiHttpVideoExtractionAdapterWireMockIntegrationTest`의 정상 2xx 응답이 `SCHEMA`로 실패했다.
- CI 결과: `795 tests completed, 1 failed, 2 skipped`.
- 발생 조건: WireMock mapping이 `jsonBody`만 설정하고 `Content-Type: application/json` 응답 헤더를 명시하지 않았다. Adapter는 JSON media type이 없으면 계약상 `SCHEMA`로 거부한다.
- 코드 현상: 빈 응답 본문에 대한 `readTree` 결과와 S1 payload 결과를 null-safe하게 처리하지 않았고, 관리자 보완 텍스트를 시스템 지시 문자열 뒤에 직접 이어 붙였다.
- 계약 불일치: `evidenceSchema()`와 `missingFields` Schema가 실제 `validEvidence()`·`isAllowedMissingField()` 검증보다 느슨하거나 다른 필드를 선언했다. Webhook parser는 공식 Atom payload의 `http` alternate를 거부했고, provider의 모든 4xx를 차단으로 표시했다.

## 4. 근본 원인

초기 구현이 Provider 응답의 정상 JSON 모양과 성공 경로만 중심으로 작성되어, 빈 본문·구조화 payload null·JSON media type 누락 같은 경계 조건을 회귀 테스트로 고정하지 않았다. 요청 Schema도 응답 정규화 검증기에서 이미 허용한 근거 위치와 누락 필드 목록을 재사용하지 않고 별도로 작성해 계약이 분리됐다.

또한 관리자 보완 텍스트의 목적을 단순 문자열 보완으로만 취급해 시스템 지시와 같은 Gemini text part에 결합했다. 그 결과 NFR-SECURITY-007의 비신뢰 입력 경계가 요청 구조에 드러나지 않았다. Webhook URL은 `https`만 허용하는 보수적 검증이 실제 PubSubHubbub payload 변형과 어긋났고, HTTP 상태 분류는 요청 오류와 quota 차단을 구분하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub review thread GraphQL 조회 | 미해결 스레드 7개 확인 | 모두 이번 변경 범위로 판단하고 코드·테스트에 반영 |
| GitHub Actions 로그와 `backend-test-results` XML 확인 | WireMock 통합 테스트 1건만 실패, Adapter 197행 `SCHEMA` | Fixture 응답의 `Content-Type` 누락으로 원인 확정 |
| Gemini adapter 요청·응답 코드와 S1 검증기 대조 | Schema 필드와 검증 허용 집합 불일치 확인 | 요청 Schema·지시·검증기를 같은 허용 목록으로 정렬 |
| NFR-SECURITY-007·AI 추출 PRD·AI 후보 경계 ADR 대조 | 관리자 보완 텍스트는 신뢰하지 않는 입력이며 후보 확정 전 근거·검증 경계가 필요 | `systemInstruction`/사용자 콘텐츠 분리와 근거 없는 후보 테스트 적용 |
| 로컬 Docker 상태 확인 | Docker daemon을 사용할 수 없어 Testcontainers WireMock 통합 테스트는 로컬 실행 불가 | Docker가 제공되는 GitHub Actions 재실행으로 최종 검증 |

## 6. 최종 해결

- `GeminiHttpVideoExtractionAdapter`의 `systemInstruction`에 P1·S1·완전성 규칙을 두고, 관리자 보완 텍스트는 `<untrusted-administrator-supplement>` 경계의 사용자 콘텐츠로 전달했다.
- 빈 응답 envelope와 null payload를 `SCHEMA`로 정규화했다. S1 요청 Schema에 근거 위치 필드와 허용 `missingFields` enum을 추가했다.
- `TIMESTAMP`·`TEXT_RANGE` 정상 응답, Prompt Injection과 근거 없는 후보, 빈 2xx 응답, 4xx 상태 분류 회귀 테스트를 추가했다.
- Webhook parser가 `http`·`https` alternate를 모두 허용하되 외부로 전달하는 URL은 canonical `https` watch URL로 반환하도록 변경했다. Controller와 parser fixture를 `http` 입력으로 바꿨다.
- WireMock mapping 응답에 `Content-Type: application/json`을 명시했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |
| `.\gradlew.bat test --tests "com.masiton.ai.infrastructure.provider.config.GeminiHttpVideoExtractionAdapterTest" --tests "com.masiton.ai.presentation.webhook.YoutubeAtomNotificationParserTest" --tests "com.masiton.ai.presentation.webhook.YouTubeChannelWebhookControllerTest" --no-daemon --console=plain` | 통과 | Gemini adapter·Webhook parser·Controller 관련 테스트 전부 통과 |
| GitHub Actions `백엔드 빌드·테스트` 재실행 | 반영 후 확인 | Docker/Testcontainers WireMock 통합 테스트와 전체 CI 결과를 push 후 확인 |

로컬 환경에서는 Docker daemon이 없어 Testcontainers 통합 테스트를 실행하지 못했다. 따라서 WireMock fixture 수정의 최종 증거는 push 후 GitHub Actions 재실행으로 확인한다.

## 8. 재발 방지 및 다음 확인

- 요청 Schema와 응답 정규화 검증기가 같은 허용 필드·근거 타입을 선언하도록 관련 회귀 테스트를 유지한다.
- 외부 Provider fixture는 성공 상태, JSON media type, 실제 요청 계약을 함께 명시한다.
- 관리자 보완 텍스트는 시스템 지시와 분리된 비신뢰 콘텐츠로만 전달하고, 후보 확정에는 영상 근거와 후속 검증을 요구한다.
- push 후 GitHub Actions 전체 백엔드 job이 통과하는지 확인하고, 7개 리뷰 스레드에 수정 내용·검증 결과를 답글로 남긴 뒤 resolve한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 비교 결과 | 해석 |
|---|---:|---|---|---|
| 관련 회귀 테스트 실패 | CI 1건 실패 | PR #170 CI 및 targeted Gradle test | 수정 후 CI 재실행에서 확인 예정 | WireMock 응답 계약과 adapter media type 검증의 일치 여부 확인 |
| 미해결 리뷰 스레드 | 7개 | PR #170 review threads | 수정 후 답글·resolve에서 확인 예정 | 리뷰 요청의 코드·테스트 반영 여부 확인 |

## 10. 남은 사항

로컬 Docker 제약으로 Testcontainers 테스트는 미실행 상태다. push 후 CI가 통과하면 남은 사항을 없음으로 갱신한다.
