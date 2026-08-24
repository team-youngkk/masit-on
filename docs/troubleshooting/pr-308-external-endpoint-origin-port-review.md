---
related_documents:
  - ../07-adr/security/auth-006-cookie-origin-defense.md
  - pr-211-admin-refresh-logout-origin-review.md
  - ../05-specs/api/common/authentication-contract.md
---

# PR #308 리뷰 트러블슈팅: 외부 endpoint Origin 포트 검증

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#308 테스트 키와 외부 검증 endpoint fail-closed 강화](https://github.com/team-youngkk/masit-on/pull/308) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-24 |
| 범위 | Kakao·YouTube 외부 endpoint의 허용 Origin 비교, CI 회귀 수정 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | 있음. `OriginCanonicalizer`와 외부 Origin 방어 원칙은 [PR #211 기록](pr-211-admin-refresh-logout-origin-review.md)과 [ADR-AUTH-006](../07-adr/security/auth-006-cookie-origin-defense.md)에 있다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [허용 Origin 비교에 포트 포함](https://github.com/team-youngkk/masit-on/pull/308#discussion_r3840665583), [YouTube 포트 재확인](https://github.com/team-youngkk/masit-on/pull/308#discussion_r3840752830) | scheme·host뿐 아니라 유효 포트까지 비교하고, Kakao·YouTube 두 Adapter에도 회귀 테스트 추가 | 애플리케이션 | 수정 필요 | 세 Adapter가 공통 `OriginCanonicalizer.matches`를 사용하도록 변경하고 비표준 포트 차단 테스트를 추가했다. 동적 WireMock 테스트의 allowed origin도 실제 endpoint 포트와 함께 주입한다. | 집중 테스트 통과 및 PR CI run `32691401266` 성공 |
| [Kakao 빈 allowlist](https://github.com/team-youngkk/masit-on/pull/308#discussion_r3840805442), [YouTube 빈 allowlist](https://github.com/team-youngkk/masit-on/pull/308#discussion_r3840809683) | 운영용 생성자에서 빈·누락 `allowed-origins`를 허용하지 말고 세 Adapter에 회귀 테스트 추가 | 애플리케이션 | 수정 필요 | 운영용 생성자 경로는 빈 allowlist를 fail-closed로 거부하고, 기존 테스트용 4인자 생성자는 명시적으로 편의 경로로 유지했다. 세 Adapter에 빈 allowlist 초기화 실패 테스트를 추가했다. | 집중 테스트 및 최신 PR CI 결과 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. endpoint 허용 목록과 실제 요청 대상의 포트가 달라도 초기화가 통과하는 보안 계약 불일치였다.
- 재현 조건: `base-url=https://dapi.kakao.com:8443`, `allowed-origins=https://dapi.kakao.com`처럼 scheme·host는 같고 포트가 다른 설정을 사용한다. YouTube channel/video endpoint도 동일하다.
- 실제 결과: 기존 세 Adapter의 `isAllowedOrigin`이 scheme·host만 비교해 비표준 포트 endpoint를 허용할 수 있었고, 빈 allowlist에서는 모든 HTTP(S) endpoint를 허용했다.
- 기대 결과: HTTP 80·HTTPS 443은 기본 포트로 정규화하고, 그 외 포트는 허용 목록의 포트와 정확히 일치해야 한다. allowlist가 비어 있거나 누락된 경우도 API key를 전송하기 전에 fail-closed 해야 한다.
- 영향 범위: Kakao Local keyword 호출, YouTube channel verification 호출, YouTube video verification 호출.

## 4. 근본 원인

세 외부 Adapter에 동일한 `isAllowedOrigin` 구현이 복제되어 있었고, 각 구현이 URI의 scheme과 host만 비교했다. 이미 `OriginCanonicalizer`가 기본 포트 제거와 URI 구성요소 검증을 제공했지만 외부 Adapter가 이를 재사용하지 않아 포트 검증 정책이 분리되어 있었다. 또한 빈 allowlist를 허용하는 분기가 남아 있어 설정이 누락되면 모든 HTTP(S) endpoint가 통과할 수 있었다. strict 비교로 전환하면 local·test의 WireMock endpoint와 동적 통합 테스트 설정이 불일치할 수 있었으므로 해당 설정도 함께 정합화했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 미해결 review thread와 세 Adapter의 `isAllowedOrigin` 대조 | 세 곳 모두 scheme·host만 비교 | 수정 필요로 판단 |
| ADR-AUTH-006 및 PR #211의 canonical Origin 기록 대조 | 허용 목록은 scheme·host·명시 포트를 포함한 canonical Origin이어야 함 | 기존 공통 canonicalizer 재사용 |
| `OriginCanonicalizer.matches` 적용 검토 | 기본 포트 정규화와 사용자 정보·경로·query·fragment 차단을 한 곳에서 유지 가능 | 세 Adapter에 공통 비교 적용 |
| local·test 설정과 동적 WireMock property 대조 | strict port 비교 시 동적 WireMock 포트를 별도 허용하지 않으면 통합 테스트가 fail-closed | 정적 설정에 포트를 명시하고 동적 테스트에서 allowed origin도 함께 주입 |
| 비표준 포트 회귀 테스트 추가 | Kakao, YouTube channel, YouTube video가 각각 `:8443` 불일치를 차단 | 리뷰 요청 범위를 모두 검증 |
| 빈 allowlist 초기화 테스트 추가 | 운영용 5인자 경로가 빈 값을 거부하고 테스트용 4인자 경로는 기존 fixture를 유지 | 세 Adapter 생성자 경계를 분리 |

## 6. 최종 해결

- 변경 내용: Kakao Local, YouTube channel, YouTube video Adapter의 중복 비교를 `OriginCanonicalizer.matches`로 통합했다. 공통 canonicalizer는 HTTP 80·HTTPS 443을 기본 포트로 정규화하고 비표준 포트는 정확히 비교한다. 운영용 생성자 경로는 빈·누락 allowlist를 거부하고, 외부 Adapter endpoint 검증이 허용하는 root path `/`는 origin과 동등하게 처리한다.
- 설정 보완: local·test의 Kakao·YouTube allowed origin에 포트를 명시하고, WireMock 동적 endpoint를 사용하는 통합 테스트는 base URL과 같은 값을 allowed origin에도 등록한다.
- 선택 이유: Adapter마다 포트 비교를 복제하면 정책이 다시 어긋날 수 있으므로 기존 공통 canonicalizer를 보안 경계의 단일 비교 지점으로 사용했다.
- 변경 파일: `src/main/java/com/masiton/common/web/OriginCanonicalizer.java`, Kakao·YouTube 외부 Adapter 3개, local/test 설정, 동적 WireMock 통합 테스트 2개, 관련 단위 테스트.
- 고려한 대안: 세 Adapter에 포트·빈 값 검증을 각각 추가하는 방법은 단기 변경량은 작지만 동일 보안 정책의 재발 위험이 있어 채택하지 않았다. 테스트 fixture를 모두 5인자 생성자로 바꾸는 방법은 기존 4인자 테스트 편의 계약을 불필요하게 깨므로 운영용·테스트용 생성자 경계를 분리했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --no-daemon --console=plain --tests com.masiton.common.web.TrustedOriginResolverTest --tests com.masiton.restaurant.infrastructure.external.KakaoPlaceVerificationAdapterTest --tests com.masiton.creator.infrastructure.external.YouTubeChannelVerificationAdapterTest --tests com.masiton.video.infrastructure.external.YouTubeVideoVerificationAdapterTest` | 통과 | 공통 canonicalizer와 Kakao·YouTube 3개 Adapter의 endpoint·포트 회귀 테스트 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| GitHub Actions PR #308 backend build/test | CI에서 확인 | 전체 백엔드 컴파일·테스트와 통합 테스트 결과는 push 후 최신 run에서 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 외부 endpoint allowlist 비교는 `OriginCanonicalizer.matches`를 사용하고, 각 Adapter의 비허용 포트 회귀 테스트를 유지한다.
- 다음 확인: 최신 커밋의 GitHub Actions 전체 결과를 확인한 뒤 원본 review thread에 변경·검증·기록 링크로 답변하고 해결 처리한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 | 확인 시점 |
|---|---|---|---|---|---|
| 비허용 포트 endpoint 초기화 통과 경로 | Kakao 1개·YouTube 2개 Adapter에서 scheme·host만 일치하면 통과 가능 | 세 Adapter 회귀 테스트 | 0개 통과 | 비허용 `:8443` endpoint가 모두 fail-closed | PR #308 merge 전 CI |
| 빈 allowlist 운영 초기화 통과 경로 | 3개 Adapter에서 빈 값이면 endpoint 허용 | 세 Adapter 빈 allowlist 회귀 테스트 | 0개 통과 | 운영용 생성자는 모두 fail-closed | PR #308 merge 전 CI |

## 10. 남은 사항

- 로컬에서 Docker를 사용할 수 없어 Testcontainers 기반 전체 통합 테스트는 실행하지 못했다. CI의 최신 backend build/test 결과로 보완 확인한다.
- 리뷰 스레드 해결 후 reviewer의 최종 재검토가 필요하다.
