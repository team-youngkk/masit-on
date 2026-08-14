---
related_documents:
  - ../05-specs/api/common/authentication-contract.md
  - ../05-specs/api/admin/authentication-api.md
  - ../05-specs/api/account/member-authentication-api.md
  - ../06-architecture/security-boundary.md
  - ../07-adr/security/auth-001-spring-security-jwt.md
  - ../07-adr/security/auth-002-member-jwt-refresh-token.md
  - ../07-adr/security/auth-006-cookie-origin-defense.md
---

# PR #211 리뷰 트러블슈팅: Refresh·Logout Origin 방어

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#211 관리자 Refresh·Logout Origin 방어](https://github.com/team-youngkk/masit-on/pull/211) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-14 |
| 범위 | Accepted ADR 변경 이력 분리와 회원·관리자 다중 Origin 헤더 방어 |
| 주 문제 유형 | 애플리케이션 / 기타(ADR 추적성) |
| 기존 기록 | 없음. 리뷰 스레드와 현재 인증 계약·ADR을 기준으로 신규 기록 작성 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [ADR 변경 이력](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3782844260) | Accepted ADR-AUTH-001을 직접 수정하지 말고 후속 ADR과 추적 관계로 분리 | 기타(ADR 추적성) | 수정 필요 | ADR-AUTH-001의 결정 본문을 원복하고 ADR-AUTH-006을 추가했다. index·traceability·관련 문서 목록을 연결한다. | `git diff origin/develop -- docs/07-adr/security/auth-001-spring-security-jwt.md`에서 `superseded_by`만 변경된 것을 확인 |
| [다중 Origin 헤더](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3782844263) | 회원 Refresh·Logout도 모든 Origin 헤더를 읽고 다중 값이면 403 | 애플리케이션 | 수정 필요 | 회원·관리자가 `TrustedOriginResolver`를 공유하고 회원 Refresh·Logout 다중 헤더 회귀 테스트를 추가했다. | `TrustedOriginResolverTest`, `MemberAuthenticationControllerTest`, `AdminAuthenticationControllerTest` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 보안 경계가 우회될 수 있는 계약 불일치였다.
- 발생 환경: PR #211 `fix/admin-refresh-logout-origin`, Spring MVC `HttpServletRequest` 기반 인증 Controller/Filter.
- 재현 조건: 허용된 Origin과 다른 Origin을 같은 요청의 `Origin` 헤더에 두 개 전달한다.
- 실제 결과: 관리자 Filter는 다중 값을 거부했지만 회원 Controller는 `getHeader`로 첫 번째 값만 비교할 수 있었다.
- 기대 결과: 회원·관리자 Refresh·Logout 모두 단일 허용 Origin만 통과하고 다중 헤더는 Token 처리 전에 403이어야 한다.
- 영향 범위: 쿠키 기반 회원·관리자 Refresh·Logout의 CSRF 보조 방어 및 인증 계약 일관성.

## 4. 근본 원인

PR은 관리자 Filter에서 `getHeaders`를 사용했지만 회원 Controller는 `getHeader`를 사용했다. Servlet API의 단일 값 조회는 동일 이름의 추가 헤더를 검증하지 않으므로 회원 경계와 관리자 경계의 다중 Origin 처리 결과가 달라질 수 있었다. 또한 관리자 Origin 방어 결정을 기존 Accepted ADR의 본문에 직접 추가해 결정 이력과 현재 구현 계약이 한 문서에 섞였다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #211의 미해결 review thread와 변경 파일 확인 | ADR-AUTH-001 직접 수정과 회원 `getHeader` 사용을 확인 | 두 지적 모두 수정 필요로 판단 |
| 공통 resolver 도입 검토 | 공통 헤더 cardinality 검증과 경계별 allowlist 정책을 분리할 수 있음 | `common.web`에 정적 resolver를 두고 관리자·회원이 공유 |
| Accepted ADR 원복 후 후속 ADR 작성 | 기존 ADR 본문을 기준선으로 유지하고 Origin 결정을 ADR-AUTH-006으로 분리 가능 | `superseded_by`/`supersedes`, index·traceability를 함께 갱신 |

## 6. 최종 해결

- 변경 내용: `Origin` 헤더를 `getHeaders`로 정확히 하나만 추출하는 공통 resolver를 추가하고 회원·관리자 쿠키 경계에 적용했다. 회원 Refresh·Logout의 다중 헤더 테스트를 추가했다.
- 선택 이유: 헤더 cardinality 검증은 공통화하되, 허용 Origin canonicalization은 관리자 `SecurityProperties`와 회원 `MemberCookieSettings`가 각각 소유하도록 책임을 분리했다.
- 변경 파일: `src/main/java/com/masiton/common/web/TrustedOriginResolver.java`, `src/main/java/com/masiton/security/infrastructure/web/AdminCookieOriginFilter.java`, `src/main/java/com/masiton/member/presentation/MemberAuthenticationController.java`, 관련 테스트와 `docs/07-adr/security/auth-006-cookie-origin-defense.md`.
- 고려한 대안: 회원 Controller에만 `getHeaders` 로직을 복제하는 방법은 경계별 구현 재발 위험이 있어 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.common.web.TrustedOriginResolverTest --tests com.masiton.member.presentation.MemberAuthenticationControllerTest --tests com.masiton.security.presentation.AdminAuthenticationControllerTest --no-daemon --console=plain` | 통과 | resolver의 누락·단일·다중 헤더와 회원·관리자 인증 API 회귀 테스트 통과 |
| `./gradlew.bat test --no-daemon --console=plain` | 통과 | 전체 테스트가 9분 37초에 성공. 일부 통합 테스트 종료 과정에서 기존 PostgreSQL 연결 경고가 있었으나 실패 테스트 없이 종료 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `git diff origin/develop -- docs/07-adr/security/auth-001-spring-security-jwt.md` | 통과 | Accepted ADR 본문은 원복되고 후속 ADR 연결만 남음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 공통 resolver 단위 테스트, 회원 Refresh·Logout 다중 Origin 테스트, ADR index·traceability 연결을 추가했다.
- 다음 확인: PR 브랜치 push 후 두 원본 review thread에 변경·검증·기록 링크로 답변하고 해결 처리한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 다중 Origin 헤더 보안 경계 통과 여부 | 회원 경계에 `getHeader` 단일 값 비교 | 회원·관리자 Controller/Filter 회귀 테스트 | 0건 통과를 기대 | 회원·관리자 모두 403으로 차단 | PR #211 작성자, merge 전 CI |

## 10. 남은 사항

- 로컬 집중·전체 테스트를 모두 통과했다. PR push 후 GitHub Actions에서 동일 변경 기준의 전체 빌드·테스트 결과를 추가 확인한다.
