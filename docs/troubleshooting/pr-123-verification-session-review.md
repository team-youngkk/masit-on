---
related_documents:
  - ../05-specs/api/common/validation-access-contract.md
  - ../07-adr/platform/deploy-003-validation-cookie-session.md
  - ../06-architecture/security-boundary.md
---

# PR #123 리뷰 트러블슈팅: 검증 세션 프록시·자격 증명 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#123](https://github.com/team-youngkk/masit-on/pull/123) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-03 |
| 범위 | 검증 참여자 출처 판정, 자격 증명 비교와 Nginx fragment 구문 리뷰 3건 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [신뢰 프록시 판정](https://github.com/team-youngkk/masit-on/pull/123#discussion_r3703444162) | 루프백 하드코딩 대신 기존 설정 기반 Resolver 패턴 적용 | 수정 필요 | 검증 전용 Resolver와 설정·fail-fast 검증 추가 | Resolver·Controller·ArchUnit 테스트 통과 |
| [bcrypt 비교 균질화](https://github.com/team-youngkk/masit-on/pull/123#discussion_r3703444167) | 로그인 ID 불일치와 무관하게 bcrypt 비교 수행 | 수정 필요 | 유효 설정 해시 또는 더미 해시로 항상 bcrypt 비교 | 자격 증명 Verifier 테스트 통과 |
| [Nginx fragment 구문](https://github.com/team-youngkk/masit-on/pull/123#discussion_r3703549327) | `#`가 주석으로 해석되지 않도록 redirect URI 인용 | 수정 필요 | fragment 포함 URI 전체를 큰따옴표로 감쌈 | `nginx -t`, 무세션 화면 302 Location 검증 |

## 3. 문제 현상

- 재현 조건: Nginx 뒤에서 검증 참여자 로그인을 수행하거나 존재하지 않는 로그인 ID를 제출한다.
- 실제 결과: 출처 신뢰 범위가 Controller의 루프백 상수에 고정됐고, 설정 해시가 비정상인 경우 bcrypt 비교를 건너뛸 수 있었다. Nginx redirect URI의 따옴표 밖 `#`는 주석 시작으로 해석돼 지시문 세미콜론까지 제거했다.
- 기대 결과: 신뢰 프록시는 운영 설정으로 명시하고 잘못된 설정은 기동 시 거부하며, 로그인 ID 일치 여부와 무관하게 bcrypt 작업량을 유지한다.
- 영향 범위: 검증 참여자 로그인 실패 제한의 출처 식별, 자격 증명 열거 방지, 운영 토폴로지 변경 안전성.

## 4. 근본 원인

회원·지도 경계에 이미 설정 기반 출처 Resolver가 있었지만 검증 세션 구현은 현재 단일 EC2 토폴로지만 고려해 `127.0.0.1`과 `::1`을 Controller에 직접 넣었다. 또한 ID 비교와 bcrypt 비교 결과를 마지막에 결합했지만, 구성 해시 유효성 검사와 bcrypt 호출을 `&&`로 연결해 구성 오류 시 bcrypt 호출이 생략됐다. Nginx 설정의 `#`가 URI fragment가 아니라 주석 구문이라는 점을 정적 셸 검사만으로 발견하지 못했고 실제 `nginx -t`를 실행하지 않았다.

## 5. 해결

- 변경 내용: `VerificationClientAddressResolver`를 추가하고 `trusted-proxy-addresses`와 `reverse-proxy-enabled`를 운영 환경에서 주입했다. 프록시 모드인데 신뢰 주소가 없으면 기동을 거부한다. 자격 증명 비교는 구성 해시가 정상이면 실제 해시, 아니면 더미 BCrypt 해시를 사용해 항상 한 번 실행한다. Nginx의 fragment 포함 redirect URI 전체를 큰따옴표로 감쌌다.
- 선택 이유: 회원·지도 경계와 동일한 신뢰 모델을 사용하면서 검증 세션 namespace와 소유권은 `security`에 유지한다.
- 변경 파일: `VerificationAccessProperties.java`, `VerificationClientAddressResolver.java`, `VerificationSessionController.java`, `BcryptVerificationCredentialVerifier.java`, `application.yml`, `deploy/scripts/app-run.sh`와 관련 테스트.
- 고려한 대안: 회원 Resolver 직접 재사용은 `security`가 `member` 인프라에 의존하게 되므로 채택하지 않았다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests VerificationClientAddressResolverTest --tests BcryptVerificationCredentialVerifierTest --tests VerificationSessionControllerTest --tests VerificationSessionServiceTest --tests ArchitectureTest` | 통과 | 신뢰/비신뢰 프록시, 다중 전달 헤더, fail-fast, ID 불일치·구성 오류 bcrypt 실행, 기존 세션 계약과 계층 경계 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `nginx -t` 및 무세션 화면 요청 | 통과 | 설정 구문 정상, `Location`에 `/verification/login#returnTo=...` 보존 |

## 7. 재발 방지

- 검증 프록시 모드의 신뢰 주소 누락을 기동 시점 검증과 단위 테스트로 고정했다.
- 로그인 ID 불일치와 구성 해시 오류에서도 `PasswordEncoder.matches` 호출을 검증하는 회귀 테스트를 추가했다.
- Nginx fragment redirect는 실제 Nginx 설정 검사와 응답 헤더로 검증했다.

## 8. 남은 사항

- 없음.
