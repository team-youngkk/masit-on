---
related_documents:
  - ../05-specs/api/admin/reference-data-api.md
  - ../05-specs/api/common/error-contract.md
  - ../08-planning/third-expansion-ai-candidate-registration-assist.md
  - ../06-architecture/external-integration.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #209 리뷰 트러블슈팅: 장소 링크와 null 요청 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#209 AI 후보 선택과 카카오 장소 자동 입력으로 정식 등록을 완결한다](https://github.com/team-youngkk/masit-on/pull/209) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-14 |
| 범위 | Kakao 장소 검색 결과 URL path 검증과 JSON `null` 요청의 400 오류 계약 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #170](pr-170-ai-video-extraction-review.md)의 Controller 입력 오류 경계와 [PR #141](pr-141-admin-curation-review.md)의 누락 필드 오류 구분을 확인했다. [PR #173](pr-173-ai-candidate-auto-registration-review.md)의 외부 장소 검증 단일성 원칙도 대조했다. URL path 누락 후보는 기존 기록에 없는 별도 결함이어서 이 문서에 기록하고, JSON `null`은 공통 예외 처리로 이미 400임을 확인했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [Kakao `place_url` path 검증](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782235531) | path 없는 장소 링크를 검색 결과에서 제외 | 애플리케이션 | 수정 필요 | URL 정규화에서 null·blank path를 제외하고 WireMock 회귀 테스트 추가 | 수정 전 신규 테스트 실패, 수정 후 관련 15건 통과 |
| [JSON `null` 요청 검증](https://github.com/team-youngkk/masit-on/pull/209#discussion_r3782235537) | null 본문이 NPE/500이 되지 않고 400을 반환하도록 보장 | 애플리케이션 | 이미 해결 | Spring MVC가 Controller 호출 전에 null 본문을 거부하고 `GlobalExceptionHandler`가 `400 INVALID_REQUEST`로 변환하는 현재 동작을 API 테스트로 고정 | MockMvc에서 `null` 요청 시 400과 `INVALID_REQUEST` 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 검색 단계에서는 오류가 없고, path 없는 후보를 선택한 뒤 등록 미리보기에서 `400 INVALID_FIELD_VALUE`가 발생할 수 있다. JSON `null`은 현재 `400 INVALID_REQUEST`다.
- 발생 환경: Java 21, Spring Boot 4.1.0, PR #209 `feature/ws-15-ai-candidate-registration-assist`.
- 재현 조건: Kakao 문서의 `place_url`이 `https://place.map.kakao.com`처럼 host만 포함하거나, 장소 검색 요청 본문이 JSON `null`인 경우.
- 실제 결과: path 없는 URL이 장소 검색 후보 1건으로 반환됐다. JSON `null`은 Controller에 도달하지 않고 공통 예외 처리에서 이미 400으로 종료됐다.
- 기대 결과: 이후 등록 미리보기에 제출할 수 없는 장소 URL은 검색 단계에서 제외하고, 해석할 수 없는 null 본문은 4xx 오류로 종료해야 한다.
- 영향 범위: 관리자 AI 후보 장소 선택과 맛집 등록 미리보기 사이의 계약 일관성. DB 저장과 외부 API 계약 변경은 없다.

## 4. 근본 원인

`KakaoPlaceSearchAdapter.canonicalPlaceUrl`은 scheme·host·userinfo·port만 확인하고 path를 그대로 canonical URL에 복사했다. 반면 `RestaurantRegistrationService.kakaoPlaceUrl`은 path가 비어 있으면 등록 입력을 거부하므로, 검색 출력 계약이 다음 단계 입력 계약보다 약했다.

JSON `null` 지적의 NPE/500 가정은 현재 Spring MVC 동작에는 해당하지 않았다. `@RequestBody`의 기본 `required=true` 처리에서 null 본문은 Handler method 호출 전에 `HttpMessageNotReadableException`으로 분류되고, 공통 예외 처리기가 이를 `400 INVALID_REQUEST`로 변환한다. 따라서 Controller 내부 null 검사는 도달하지 않으며 같은 검사를 추가하면 경계가 중복된다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API 계약과 `RestaurantRegistrationService.kakaoPlaceUrl` 대조 | 등록 URL은 HTTPS Kakao host와 비어 있지 않은 path를 요구 | 검색 Adapter도 같은 최소 path 조건 적용 |
| path 없는 Kakao 문서 WireMock 테스트를 수정 전 실행 | 후보가 1건 반환되어 테스트 실패 | 리뷰 지적을 재현하고 Adapter 수정 |
| JSON `null` MockMvc 테스트를 `MISSING_REQUIRED_FIELD`로 기대해 실행 | 실제 상태는 400이고 코드는 `INVALID_REQUEST` | NPE/500은 재현되지 않음. 공통 오류 계약에 맞게 현재 동작을 회귀 테스트로 고정 |
| PR #170·#141·#173 기록 검색 | 입력 오류를 기능·공통 경계에 맞춰 구분하고 외부 검증을 다음 단계 계약보다 약하게 두지 않는 원칙 확인 | 현재 API·오류 계약과 충돌하지 않는 항목만 적용 |

## 6. 최종 해결

- 변경 내용: Kakao 검색 URL의 path가 null 또는 blank면 해당 문서를 조용히 제외한다. JSON `null` 요청은 기존 `400 INVALID_REQUEST` 동작을 변경하지 않고 Controller API 테스트를 추가한다.
- 선택 이유: 검색 결과가 등록 미리보기 입력 계약을 만족하게 하면서, Spring MVC와 공통 예외 처리기에 이미 존재하는 null 본문 경계를 중복 구현하지 않기 위해서다.
- 변경 파일: `KakaoPlaceSearchAdapter.java`, `KakaoPlaceSearchAdapterWireMockIntegrationTest.java`, `RestaurantPlaceSearchControllerApiTest.java`, 이 문서와 트러블슈팅 인덱스.
- 고려한 대안: Controller에 `request == null` 검사를 추가하는 방법은 `@RequestBody(required=true)`에서 Handler 호출 전에 요청이 거부되어 도달하지 않으므로 채택하지 않았다. 공통 예외 코드를 `MISSING_REQUIRED_FIELD`로 바꾸는 것은 다른 API의 오류 계약까지 바꾸므로 범위를 벗어난다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests "com.masiton.restaurant.presentation.RestaurantPlaceSearchControllerApiTest" --tests "com.masiton.restaurant.infrastructure.external.KakaoPlaceSearchAdapterWireMockIntegrationTest" --no-daemon --console=plain` | 통과 | Controller 6건과 WireMock Adapter 9건, 총 15건 통과 |
| `git diff --check` | 통과 | 패치 공백·형식 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: path 없는 Kakao 장소 문서 제외와 JSON `null`의 400 오류를 각각 WireMock·MockMvc 회귀 테스트로 고정했다.
- 다음 확인: 최종 PR head의 GitHub Actions 백엔드 전체 빌드·테스트 결과를 병합 전에 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---:|---|---:|---|---|
| path 없는 Kakao 문서의 후보 반환 수 | 1건 | WireMock 문서 1건 입력 후 검색 결과 크기 | 0건 | 등록 불가 후보 노출 제거 | `tjdgns0618`, PR #209 검증 시점 |
| JSON `null` 요청의 500 응답 수 | 0건 | MockMvc JSON `null` 요청 1회 | 0건 | 기존 400 경계 유지, 회귀 테스트 추가 | `tjdgns0618`, PR #209 검증 시점 |
| 미해결 리뷰 스레드 | 2건 | GitHub review thread GraphQL 조회 | 0건 | 2건 모두 원래 스레드에 답글을 남기고 해결 처리 | `tjdgns0618`, PR #209 리뷰 반영 시점 |

## 10. 남은 사항

- 최종 커밋 push 뒤 자동 실행되는 GitHub Actions 백엔드 전체 빌드·테스트 확인.
