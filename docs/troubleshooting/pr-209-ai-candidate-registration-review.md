---
related_documents:
  - ../05-specs/api/admin/reference-data-api.md
  - ../05-specs/api-review.md
  - ../05-specs/api/common/error-contract.md
  - ../08-planning/third-expansion-ai-candidate-registration-assist.md
  - ../06-architecture/external-integration.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #209 리뷰 트러블슈팅: AI 후보 등록 입력·비동기·외부 연동 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#209 AI 후보 선택과 카카오 장소 자동 입력으로 정식 등록을 완결한다](https://github.com/team-youngkk/masit-on/pull/209) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-14 |
| 범위 | Kakao 장소 검색 결과·입력·관찰성, 중복 등록 안내, 검색 응답 최신성, 공유 정책 경계 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #170](pr-170-ai-video-extraction-review.md)의 Controller 입력 오류 경계, [PR #141](pr-141-admin-curation-review.md)의 누락 필드 오류 구분, [PR #173](pr-173-ai-candidate-auto-registration-review.md)의 외부 장소 검증 단일성, [PR #176](pr-176-natural-language-review.md)의 서로 다른 stale-request 상태 머신 비공통화, [PR #182](pr-182-admin-ai-video-intake-review.md)의 동기 in-flight guard를 확인했다. 현재 계약과 일치하는 항목만 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [Kakao `place_url` path 검증](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782235531) | path 없는 장소 링크를 검색 결과에서 제외 | 애플리케이션 | 수정 필요 | URL 정규화에서 null·blank path를 제외하고 WireMock 회귀 테스트 추가 | 수정 전 신규 테스트 실패, 수정 후 관련 15건 통과 |
| [JSON `null` 요청 검증](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782235537) | null 본문이 NPE/500이 되지 않고 400을 반환하도록 보장 | 애플리케이션 | 이미 해결 | Spring MVC가 Controller 호출 전에 null 본문을 거부하고 `GlobalExceptionHandler`가 `400 INVALID_REQUEST`로 변환하는 현재 동작을 API 테스트로 고정 | MockMvc에서 `null` 요청 시 400과 `INVALID_REQUEST` 확인 |
| [중복 안내 뒤 명시적 진행](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324236) | 중복 자원 재사용을 표시한 뒤 관리자가 다음 단계로 진행 | 애플리케이션 | 수정 필요 | 동기 완료 콜백을 제거하고 중복 안내·기존 정보·확인 버튼을 렌더링 | 정적 렌더 테스트 2건과 프론트 빌드 통과 |
| [유니코드 공백 상호명](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324242) | 전각 공백뿐인 이름을 400으로 거부하고 외부 Port 미호출 | 애플리케이션 | 수정 필요 | `trim()`을 Unicode-aware `strip()`으로 변경 | 서비스 테스트에서 `INVALID_FIELD_VALUE`와 Port 0회 확인 |
| [입력 복원 뒤 검색 응답](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324245) | 요청 중 입력을 바꿨다가 원래 seed로 복원하면 응답 반영 | 애플리케이션 | 수정 필요 | 검색 최신성은 generation이 아니라 session+seed identity로 판정 | 왕복 입력 identity 회귀 테스트 통과 |
| [Kakao 호출·URL 정책 공통화](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324251) | 검색·검증 Adapter의 HTTP·인증·JSON·URL 규칙 공유 | 애플리케이션 | 수정 필요 | `KakaoLocalKeywordClient`와 `KakaoPlaceUrlPolicy`로 기술 경계 추출 | 검색·검증 Adapter 19건 통과 |
| [전량 제외 응답 관찰성](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324254) | 정상 빈 결과와 모든 문서 계약 위반을 운영에서 구분 | 애플리케이션 | 수정 필요 | 원문 없이 전체·필수값 누락·URL 오류·비객체 문서 수를 경고 로그로 기록 | CapturedOutput에서 사유별 count와 원문 비노출 확인 |
| [`roadAddressHint` 길이 상한](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324256) | 주소 힌트 최대 길이와 400 계약 추가 | 애플리케이션 | 수정 필요 | `RV-API-010`의 도로명주소 255자 상한을 API 계약과 서비스 검증에 반영 | 정규화 후 255자 통과·256자 거부·Port 미호출 테스트 통과 |
| [서울 주소 정규화 공통화](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324261) | 세 호출부의 서울 시도명 확장 규칙 공유 | 애플리케이션 | 수정 필요 | `SeoulRoadAddressNormalizer`로 application·두 Adapter 규칙 통합 | 정규화 단위 테스트와 두 Adapter 회귀 통과 |
| [프론트 요청 훅 공통화](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782324265) | 두 컴포넌트의 generation·identity·in-flight 방식을 하나의 훅으로 통합 | 애플리케이션 | 수정 불필요 | Preview/Create와 Search/Visit의 요청 identity·취소·완료 상태가 달라 하나의 훅으로 묶지 않음 | 각 흐름의 재현 결함을 해당 상태 머신에서 수정하고 전체 프론트 빌드 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: path 없는 후보를 선택한 뒤 등록 미리보기에서 `400 INVALID_FIELD_VALUE`가 발생할 수 있다. JSON `null`은 현재 `400 INVALID_REQUEST`다. 유니코드 공백 이름은 외부 실패로 오분류될 수 있고, 255자를 넘는 주소 힌트도 외부 검색까지 전달됐다. Kakao 문서 전량 제외는 정상 빈 결과처럼 보였다.
- 발생 환경: Java 21, Spring Boot 4.1.0, PR #209 `feature/ws-15-ai-candidate-registration-assist`.
- 재현 조건: path 없는 Kakao URL, JSON `null`, U+3000만 있는 이름, 정규화 후 256자인 주소 힌트, 중복 미리보기, 검색 중 seed 변경 후 원복, Kakao 문서가 모두 필수값·URL 계약을 위반하는 경우.
- 실제 결과: 등록 불가 URL이 후보로 반환됐고, 256자 주소 힌트가 검증 없이 외부 Port로 전달됐다. 중복 안내는 렌더 전에 다음 단계로 언마운트됐다. 원래 seed로 복원한 검색 응답은 generation 불일치로 폐기됐으며 전량 제외 응답에는 운영 신호가 없었다. JSON `null`은 이미 400이었다.
- 기대 결과: 검색 출력이 등록 입력 계약을 만족하고, 중복 재사용은 관리자가 확인하며, 현재 seed와 같은 응답은 표시하고, 외부 계약 위반 전량 제외는 민감정보 없는 운영 로그로 구분해야 한다.
- 영향 범위: 관리자 AI 후보 장소 선택과 맛집 등록 미리보기 사이의 계약 일관성. DB 저장과 외부 API 계약 변경은 없다.

## 4. 근본 원인

`KakaoPlaceSearchAdapter.canonicalPlaceUrl`은 scheme·host·userinfo·port만 확인하고 path를 그대로 canonical URL에 복사했다. 반면 `RestaurantRegistrationService.kakaoPlaceUrl`은 path가 비어 있으면 등록 입력을 거부하므로, 검색 출력 계약이 다음 단계 입력 계약보다 약했다.

JSON `null` 지적의 NPE/500 가정은 현재 Spring MVC 동작에는 해당하지 않았다. `@RequestBody`의 기본 `required=true` 처리에서 null 본문은 Handler method 호출 전에 `HttpMessageNotReadableException`으로 분류되고, 공통 예외 처리기가 이를 `400 INVALID_REQUEST`로 변환한다. 따라서 Controller 내부 null 검사는 도달하지 않으며 같은 검사를 추가하면 경계가 중복된다.

중복 미리보기 성공 콜백은 `setPreview`와 부모 `onCompleted`를 같은 tick에 실행해 안내가 렌더되기 전에 현재 단계를 언마운트했다. 검색 최신성은 실제 요청 identity가 같은지보다 모든 입력 변경에 증가하는 generation까지 요구해, 입력을 원래 값으로 복원해도 유효한 응답을 stale로 오판정했다. 이름 검증은 ASCII 중심 `trim()`을 사용해 U+3000을 제거하지 못했다.

두 Kakao Adapter는 HTTP timeout·인증·JSON 파싱과 URL 정규화를 복사했고, 서울 주소 확장 규칙은 application과 두 Adapter에 세 번 존재했다. 실제 path 검증 누락이 검색과 등록 사이의 드리프트로 드러났으므로 단순 취향이 아니라 재발한 정책 분산으로 판단했다. 반면 `RegistrationFlow`의 Preview/Create는 고정 endpoint의 한 요청군이고 `AiCandidateRegistration`의 Search/Visit는 seed identity와 session identity가 다른 두 요청군이므로, 하나의 범용 훅은 서로 다른 완료·무효화 의미를 숨긴다.

`roadAddressHint` 구현과 신규 장소 검색 API 표만 확인해 상한을 새 계약 결정으로 오분류했지만, 상위 확정 계약인 `RV-API-010`은 검색과 모든 관리자 등록 API의 도로명주소를 최대 255자로 제한한다. 신규 API 구현 시 이 공통 계약을 역추적하지 못한 것이 검증 누락의 근본 원인이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API 계약과 `RestaurantRegistrationService.kakaoPlaceUrl` 대조 | 등록 URL은 HTTPS Kakao host와 비어 있지 않은 path를 요구 | 검색 Adapter도 같은 최소 path 조건 적용 |
| path 없는 Kakao 문서 WireMock 테스트를 수정 전 실행 | 후보가 1건 반환되어 테스트 실패 | 리뷰 지적을 재현하고 Adapter 수정 |
| JSON `null` MockMvc 테스트를 `MISSING_REQUIRED_FIELD`로 기대해 실행 | 실제 상태는 400이고 코드는 `INVALID_REQUEST` | NPE/500은 재현되지 않음. 공통 오류 계약에 맞게 현재 동작을 회귀 테스트로 고정 |
| PR #170·#141·#173 기록 검색 | 입력 오류를 기능·공통 경계에 맞춰 구분하고 외부 검증을 다음 단계 계약보다 약하게 두지 않는 원칙 확인 | 현재 API·오류 계약과 충돌하지 않는 항목만 적용 |
| 중복 Preview 콜백과 부모 단계 전환 대조 | `setPreview` 렌더 전에 부모 `setStep`으로 언마운트 | 중복 안내와 확인 버튼을 별도 렌더 컴포넌트로 고정 |
| `String.trim()`과 U+3000 입력 대조 | 길이 1의 유효 이름으로 남아 Port 호출 가능 | `strip()` 적용, Port 무호출 테스트 추가 |
| 검색 identity·generation 변경 순서 추적 | A 요청 중 B→A로 복원하면 identity는 같지만 generation은 달라짐 | Search는 identity, Visit는 generation+session을 유지 |
| 외부 연동 설계 2절과 두 Kakao Adapter diff 대조 | HTTP·인증·파싱·URL 정책이 실제 중복되고 path 규칙이 드리프트 | 기술 client·URL policy만 공유하고 Adapter별 404·후보 판정 유지 |
| ADR-OBS-001·외부 연동 11절 대조 | 정상 빈 결과와 Provider 문서 전량 계약 위반을 구분해야 하며 원문 로깅은 금지 | count-only warning과 CapturedOutput 비노출 테스트 적용 |
| `roadAddressHint` 계약 재확인 | 신규 API 표에는 상한이 없었지만 `RV-API-010`은 검색·모든 관리자 등록 API의 도로명주소를 최대 255자, 초과를 `INVALID_FIELD_VALUE`로 확정 | 신규 결정 없이 문서·서비스·경계 테스트 동기화 |
| PR #176과 두 프론트 상태 머신 비교 | 요청군·identity·취소·완료 의미가 서로 다름 | 범용 훅 추출 없이 각 결함만 소유 흐름에서 수정 |

## 6. 최종 해결

- 변경 내용: Kakao URL path 검증, 공통 keyword client·URL policy·서울 주소 정규화, 전량 제외 count 로그, Unicode 이름 검증과 정규화 후 주소 힌트 255자 상한을 적용했다. 프론트는 중복 확인 버튼과 identity 중심 검색 응답 판정을 추가했다. JSON `null`의 기존 400은 유지했다.
- 선택 이유: 재현된 결함은 가장 가까운 소유 경계에서 차단하고, 실제로 드리프트한 제공자 기술 정책만 공유하며, 의미가 다른 프론트 상태 머신은 억지로 합치지 않기 위해서다.
- 변경 파일: `reference-data-api.md`, `SearchAdminPlaceCandidatesService.java`와 테스트, `KakaoLocalKeywordClient.java`, `KakaoPlaceUrlPolicy.java`, `SeoulRoadAddressNormalizer.java`, 두 Kakao Adapter와 관련 테스트, `RegistrationFlow.tsx`, `RegistrationDuplicateResult.ts`, `AiCandidateRegistration.tsx`, coordination·렌더 테스트, 이 문서.
- 고려한 대안: Controller에 `request == null` 검사를 추가하는 방법은 `@RequestBody(required=true)`에서 Handler 호출 전에 요청이 거부되어 도달하지 않으므로 채택하지 않았다. 공통 예외 코드를 `MISSING_REQUIRED_FIELD`로 바꾸는 것은 다른 API의 오류 계약까지 바꾸므로 범위를 벗어난다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests "com.masiton.restaurant.presentation.RestaurantPlaceSearchControllerApiTest" --tests "com.masiton.restaurant.infrastructure.external.KakaoPlaceSearchAdapterWireMockIntegrationTest" --no-daemon --console=plain` | 통과 | Controller 6건과 WireMock Adapter 9건, 총 15건 통과 |
| `git diff --check` | 통과 | 패치 공백·형식 오류 없음 |
| `./gradlew test --tests "com.masiton.restaurant.application.SearchAdminPlaceCandidatesServiceTest" --tests "com.masiton.restaurant.application.SeoulRoadAddressNormalizerTest" --tests "com.masiton.restaurant.infrastructure.external.KakaoPlaceVerificationAdapterTest" --tests "com.masiton.restaurant.infrastructure.external.KakaoPlaceSearchAdapterWireMockIntegrationTest"` | 통과 | 입력·공통 정책·관찰성·두 Adapter 31건 |
| `./gradlew test --tests "com.masiton.restaurant.presentation.RestaurantPlaceSearchControllerApiTest" --tests "com.masiton.restaurant.presentation.RestaurantRegistrationControllerApiTest" --tests "com.masiton.architecture.ArchitectureTest"` | 통과 | Controller·아키텍처 관련 회귀 17건 |
| `./gradlew test --tests "com.masiton.security.infrastructure.configuration.SecurityConfigurationApiTest"` | 통과 | 새 관리자 검색 경로를 포함한 SecurityFilterChain 회귀 23건 |
| `npm.cmd run build` | 통과 | pretest 10건, 본 테스트 210건, TypeScript 검사, Next.js 29개 route production build |

## 8. 재발 방지 및 다음 확인

- 재발 방지: path 없는 장소, 전량 제외 로그, Unicode 공백, 주소 정규화, 중복 안내 렌더, seed 복원 응답을 자동 테스트로 고정했다. Kakao HTTP·URL 정책은 한 기술 경계에서만 유지한다.
- 다음 확인: 최종 PR head의 GitHub Actions를 병합 전에 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---:|---|---:|---|---|
| path 없는 Kakao 문서의 후보 반환 수 | 1건 | WireMock 문서 1건 입력 후 검색 결과 크기 | 0건 | 등록 불가 후보 노출 제거 | `tjdgns0618`, PR #209 검증 시점 |
| JSON `null` 요청의 500 응답 수 | 0건 | MockMvc JSON `null` 요청 1회 | 0건 | 기존 400 경계 유지, 회귀 테스트 추가 | `tjdgns0618`, PR #209 검증 시점 |
| 미해결 리뷰 스레드 | 2건 | GitHub review thread GraphQL 조회 | 0건 | 2건 모두 원래 스레드에 답글을 남기고 해결 처리 | `tjdgns0618`, PR #209 리뷰 반영 시점 |
| 후속 미해결 리뷰 스레드 | 8건 | GitHub review thread GraphQL 조회 | 0건 | 코드 결함·검증된 유지보수 항목 7건 해결, 상태 머신 공통화 1건 수정 불필요 해결 | `tjdgns0618`, PR #209 후속 리뷰 시점 |
| 256자 주소 힌트의 외부 Port 호출 | 1회 | 서비스 테스트에서 256자 입력 후 mock 호출 확인 | 0회 | 확정 계약 위반 입력을 외부 호출 전에 차단 | `tjdgns0618`, PR #209 후속 리뷰 시점 |
| 중복 재사용 확인 UI | 0개 | 중복 Preview 뒤 정적 렌더 | 안내와 확인 버튼 1세트 | 다음 단계 이동 전에 관리자 인지 가능 | `tjdgns0618`, PR #209 후속 리뷰 시점 |
| 원래 seed 검색 응답 반영 | generation 불일치로 거부 | A 요청 중 B→A 복원 coordination 테스트 | identity 일치로 허용 | 불필요한 재검색 제거 | `tjdgns0618`, PR #209 후속 리뷰 시점 |
| Kakao 기술 정책 구현 지점 | HTTP·파싱 2곳, URL 규칙 2곳, 주소 규칙 3곳 | 정적 참조 검색 | client 1곳, URL policy 1곳, 주소 normalizer 1곳 | 정책 드리프트 지점 축소 | `tjdgns0618`, PR #209 후속 리뷰 시점 |
| 전량 계약 위반 응답 신호 | 0개 | Kakao 문서 2건 모두 제외 CapturedOutput | count-only warning 1개 | 정상 빈 결과와 외부 계약 이상 구분 | `tjdgns0618`, PR #209 후속 리뷰 시점 |

## 10. 남은 사항

- 최종 커밋 push 뒤 자동 실행되는 GitHub Actions 백엔드 전체 빌드·테스트 확인.
