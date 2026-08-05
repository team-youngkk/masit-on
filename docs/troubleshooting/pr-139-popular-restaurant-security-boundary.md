---
related_documents:
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../06-architecture/security-boundary.md
  - ../05-specs/api/discovery/popular-restaurant-api.md
---

# PR #139 리뷰 트러블슈팅: 인기 맛집 공개 조회의 회원 인증·세션 경계 오분류

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#139 인기 맛집 API와 화면](https://github.com/team-youngkk/masit-on/pull/139) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-05 |
| 범위 | `GET /api/restaurants/popular`가 보안 필터에서 맛집 상세와 같은 "선택적 회원 인증" 경로로 오분류되는 문제, PR 본문 테스트 건수 표기 정정 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | 검색 결과 같은 증상의 기존 기록은 없었다. `docs/06-architecture/security-boundary.md`, `SecurityConfiguration.isCreatorDetailReadRequest`의 "회원 문맥 없는 완전 공개 조회" 선례를 판단 기준으로 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [보안 경계 경로 판정 보완 필요](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717575255) (P2, w00lam) + [완전 공개 읽기 경계에 포함](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717597247) (P2, inan0226) | `/api/restaurants/popular`가 맛집 상세와 같은 선택적 회원 인증 경로로 분류됨 | 애플리케이션 | 수정 필요 | `SecurityConfiguration`과 `MemberSessionRevocationFilter`에 `popular` 세그먼트 예외를 추가해 완전 공개 경로로 재분류했다. w00lam이 제시한 "만료 토큰 → 401" 증상은 코드 추적 결과 재현되지 않았고, inan0226이 지적한 "유효 회원 토큰이 있으면 불필요하게 회원 세션 저장소를 조회한다"는 것이 실제 근본 원인이었다. | `MemberSessionRevocationFilterTest`, `SecurityBoundaryApiTest` 신규 테스트 통과 |
| [만료된 Bearer Token 공개 읽기 검증 테스트 추가 권장](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717575258) (P3, w00lam) | `PopularRestaurantApiTest`에 만료 토큰 테스트 추가 요청 | 애플리케이션 | 수정 필요 | 같은 목적의 기존 경계 테스트 파일 `SecurityBoundaryApiTest`(유튜버 상세용 `UNVERIFIABLE_JWT` 패턴)에 동일 패턴으로 추가했다. `PopularRestaurantApiTest`가 아니라 이 파일에 둔 이유는 답글에 남긴다. | `SecurityBoundaryApiTest` 신규 테스트 통과 |
| [DTO 계약 및 순위 부여 구조 정합성](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717575261) (P3 Pass, w00lam) | 승인성 코멘트, 조치 요청 없음 | 해당 없음 | 수정 불필요 | 원래 구현을 유지했다. | 해당 없음 |
| [SSR cache 옵션 및 에러 핸들링 정합성](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717575264) (P3 Pass, w00lam) | 승인성 코멘트, 조치 요청 없음 | 해당 없음 | 수정 불필요 | 원래 구현을 유지했다. | 해당 없음 |
| [PR 본문 테스트 건수 표기 정정](https://github.com/team-youngkk/masit-on/pull/139#discussion_r3717593823) (jinyp01) | PR 본문이 "4건"이라 썼지만 실제 `@Test` 메서드는 3개 | 애플리케이션(문서 정확성) | 수정 필요 | PR 본문을 "3건(정상 스키마·인증 없이 200을 한 메서드에서 함께 검증해 4개 시나리오)"으로 정정하고, 이번 수정으로 늘어난 테스트도 함께 반영했다. | PR 본문 갱신 diff로 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음(런타임 오류가 아니라 보안 경계 분류 로직의 오분류)
- 발생 환경: `SecurityConfiguration`(`src/main/java/com/masiton/security/infrastructure/configuration/SecurityConfiguration.java`), `MemberSessionRevocationFilter`(`src/main/java/com/masiton/security/infrastructure/web/MemberSessionRevocationFilter.java`)
- 재현 조건: `GET /api/restaurants/popular` 요청에 유효한 회원 `Authorization: Bearer` 토큰을 포함해 보낸다.
- 실제 결과(수정 전): 두 클래스 모두 `/api/restaurants/` 뒤에 `/`가 없는 문자열이면 무조건 "맛집 상세 조회"로 간주하는 문자열 휴리스틱을 쓴다. `popular`도 이 조건에 걸려, 유효한 회원 JWT가 있으면 `MemberSessionRevocationFilter`가 `MemberSessionAccessChecker`(Redis 기반 세션 폐기 확인)를 호출한다.
- 기대 결과: `API-POPULAR-001`은 회원 문맥을 쓰지 않는 완전 공개 조회다(`docs/05-specs/api/discovery/popular-restaurant-api.md`). 공개 목록 조회가 회원 세션 저장소 가용성에 의존해서는 안 된다.
- 영향 범위: 유효한 회원 토큰을 들고 인기 맛집을 조회하는 요청마다 불필요한 Redis 호출이 붙는다. 다만 `MemberSessionRevocationFilter`가 세션이 `ALLOWED`가 아니어도 이 경로에서는 익명으로 폴백하도록 이미 분기되어 있어, Redis 장애 시에도 401/503으로 사용자에게 노출되지는 않았다(w00lam이 제시한 "401 반환" 증상은 이 폴백 때문에 재현되지 않는다).

## 4. 근본 원인

`SecurityConfiguration.isOptionalMemberAuthenticationRequest`와 `MemberSessionRevocationFilter.isOptionalRestaurantDetailRequest`는 "`/api/restaurants/{단일 세그먼트}`는 맛집 상세다"라는 문자열 휴리스틱만으로 판단하고, 어떤 세그먼트 값이 오는지 검사하지 않는다. 이 저장소는 새 리터럴 하위 경로(`popular`)를 추가할 때 이 휴리스틱을 갱신하는 것을 강제하는 장치(타입, 라우트 목록, 컴파일 검사 등)가 없어, `PopularRestaurantController`를 추가하면서 두 판정 로직을 함께 갱신하지 않았다.

이미 같은 클래스에 `isCreatorDetailReadRequest`가 "회원 문맥 없는 완전 공개 조회는 Bearer Token을 해석하지 않는다"는 선례를 남겨 두었지만(주석에 명시), `restaurant` 도메인에는 대응하는 예외 처리가 없어 신규 리터럴 경로가 기존 상세 조회 판정에 그대로 흡수됐다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `SecurityConfiguration.isOptionalMemberAuthenticationRequest`/`isAnonymousPublicReadRequest` 코드 추적 | `/api/restaurants/popular`가 `isOptionalMemberAuthenticationRequest`에는 걸리고 `isAnonymousPublicReadRequest`에는 걸리지 않음을 확인 | w00lam·inan0226의 오분류 지적이 코드로 재현됨. 수정 필요로 확정 |
| `optionalMemberBearerTokenResolver`, `authenticationManagerResolver`, `authenticatePublicRequest` 코드 추적 | 무효·만료 토큰은 `authenticatePublicRequest`가 `AuthenticationException`을 잡아 `null` Authentication으로 폴백해 401을 던지지 않음 | w00lam이 제시한 "만료 토큰 → 401" 증상은 현재 코드로 재현되지 않는다. 근본 원인은 이 증상이 아니라 inan0226이 지적한 세션 저장소 호출 쪽임 |
| `MemberSessionRevocationFilter.doFilterInternal` 코드 추적 | `isOptionalRestaurantDetailRequest`가 `true`면 세션이 `ALLOWED`가 아니어도 익명으로 폴백해 401/503을 던지지 않음 | Redis 장애 시에도 사용자에게 오류가 노출되지는 않지만, 유효한 회원 토큰이 있으면 매번 `sessionAccessChecker.check(...)` 호출(Redis 조회)이 실행됨을 확인. 완전 공개 조회가 회원 세션 저장소에 의존하는 것은 `API-POPULAR-001` 계약 위반이므로 수정 대상으로 확정 |
| 기존 유사 선례 검색 | `isCreatorDetailReadRequest`가 동일한 "회원 문맥 없는 완전 공개 조회" 문제를 이미 `isAnonymousPublicReadRequest`에 명시적으로 추가해 해결한 선례를 발견 | 같은 패턴(예외 목록에 명시적으로 추가)을 `restaurant` 도메인에도 적용하기로 결정 |
| 기존 보안 경계 테스트 파일 탐색(`SecurityBoundaryApiTest`, `MemberSessionRevocationFilterTest`) | 유튜버 상세용 `UNVERIFIABLE_JWT` 무효 토큰 테스트, `MemberSessionAccessChecker`를 목으로 검증하는 단위 테스트 패턴이 이미 존재 | 새 테스트를 `PopularRestaurantApiTest`가 아니라 이 두 파일에 같은 패턴으로 추가하기로 결정 |

## 6. 최종 해결

- 변경 내용:
  - `SecurityConfiguration.isAnonymousPublicReadRequest`에 `requestUri.equals("/api/restaurants/popular")`를 추가해 Bearer Token 해석 자체를 건너뛰게 했다.
  - `SecurityConfiguration.isOptionalMemberAuthenticationRequest`와 `MemberSessionRevocationFilter.isOptionalRestaurantDetailRequest`에 `!"popular".equals(restaurantId)` 예외를 추가해, 위 방어가 우회되더라도 두 판정 로직 모두 `popular`를 상세 조회로 다시 흡수하지 않게 했다.
  - `MemberSessionRevocationFilterTest`에 유효한 회원 인증이 있어도 인기 맛집 조회가 `MemberSessionAccessChecker`를 호출하지 않는지 검증하는 단위 테스트를 추가했다.
  - `SecurityBoundaryApiTest`에 검증할 수 없는 Bearer Token이 있어도 인기 맛집 조회가 401을 반환하지 않는지 검증하는 통합 테스트를 추가했다(유튜버 상세 테스트와 동일 패턴).
  - PR 본문의 테스트 건수 표기를 실제 `@Test` 메서드 수와 시나리오 수가 다르다는 지적에 맞춰 정정했다.
- 선택 이유: `isCreatorDetailReadRequest`가 이미 같은 문제를 "완전 공개 조회 예외 목록에 명시적으로 추가"하는 방식으로 해결한 선례이므로, 새 문자열 파싱 규칙을 만들지 않고 같은 패턴을 재사용했다. `isAnonymousPublicReadRequest`만 고쳐도 Bearer Token이 아예 해석되지 않아 기능적으로는 충분하지만, 두 상세 판정 메서드도 함께 고쳐 향후 리팩터링에서 방어선이 하나만 남는 상태를 피했다(inan0226의 요청과 일치).
- 변경 파일:
  - `src/main/java/com/masiton/security/infrastructure/configuration/SecurityConfiguration.java`
  - `src/main/java/com/masiton/security/infrastructure/web/MemberSessionRevocationFilter.java`
  - `src/test/java/com/masiton/security/infrastructure/web/MemberSessionRevocationFilterTest.java`
  - `src/test/java/com/masiton/security/SecurityBoundaryApiTest.java`
  - `src/main/java/com/masiton/restaurant/presentation/rest/PopularRestaurantController.java`(이 리뷰 회차 이전에 추가한 쿼리 파라미터 400 가드는 별도 스레드 처리, 4절 참고)
- 고려한 대안: `/api/restaurants/{restaurantId}` 상세 경로 자체를 `PathVariable` 열거형이나 별도 라우트 목록으로 리팩터링해 문자열 휴리스틱을 근본적으로 없애는 방안을 검토했으나, 리뷰 범위를 넘는 리팩터링이라 채택하지 않았다. jinyp01이 정보성 코멘트로 남긴 "향후 리터럴 경로가 더 늘어나면 이 휴리스틱을 다시 마주칠 수 있다"는 지적은 8절 다음 확인에 남긴다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests "com.masiton.security.infrastructure.web.MemberSessionRevocationFilterTest" --tests "com.masiton.security.SecurityBoundaryApiTest" --tests "com.masiton.security.infrastructure.configuration.SecurityConfigurationApiTest" --tests "com.masiton.restaurant.presentation.rest.PopularRestaurantApiTest"` | 통과 | 신규 테스트 2건 포함 전부 통과, 기존 보안 경계 테스트(`SecurityConfigurationApiTest` 23건) 회귀 없음 |
| `./gradlew clean build` | 통과 | 전체 테스트 스위트 회귀 없음(정확한 건수는 8절 참고) |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `MemberSessionRevocationFilterTest`와 `SecurityBoundaryApiTest`에 이번 경로 전용 회귀 테스트를 추가해, 향후 두 판정 메서드를 수정해도 즉시 실패로 드러나게 했다.
- 다음 확인: jinyp01이 정보성으로 남긴 "문자열 휴리스틱이 향후 리터럴 경로 추가 시 다시 문제가 될 수 있다"는 지적은 이번 PR 범위를 넘는 설계 개선이라 이번에는 적용하지 않았다. `restaurant` 도메인에 완전 공개 리터럴 경로가 하나 더 추가되는 시점에 `SecurityConfiguration`/`MemberSessionRevocationFilter` 소유자(김인안)와 함께 판정 방식 자체를 재검토할지 결정한다. 별도 추적 이슈는 없다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 유효 회원 토큰을 포함한 `/api/restaurants/popular` 요청당 Redis 호출 수 | 1회(수정 전, 코드 추적으로 확인) | 단위 테스트(`MemberSessionRevocationFilterTest`)로 재현·확인 | 0회(수정 후, 테스트로 확인) | 개선 — 완전 공개 조회에서 회원 세션 저장소 의존 제거 | 코드 리뷰 시점에 테스트로 즉시 확인, 운영 지표(Redis 호출량 대시보드)로 별도 검증은 하지 않음 |

## 10. 남은 사항

없음. 이번 회차의 5개 리뷰 스레드 모두 처리했다.
