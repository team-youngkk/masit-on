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
| 범위 | 회원·관리자 Refresh·Logout Origin 방어, canonical 비교, ADR 추적성 |
| 주 문제 유형 | 애플리케이션 / 기타(ADR 추적성) |
| 기존 기록 | 있음. 후속 리뷰에서 발견된 canonical 비교·ADR 상태 불일치를 기존 기록에 추가 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [ADR 변경 이력](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3782844260) | Accepted ADR-AUTH-001을 직접 수정하지 말고 후속 ADR과 추적 관계로 분리 | 기타(ADR 추적성) | 수정 필요 | ADR-AUTH-001의 결정 본문을 원복하고 ADR-AUTH-006을 추가했다. index·traceability·관련 문서 목록을 연결한다. | `git diff origin/develop -- docs/07-adr/security/auth-001-spring-security-jwt.md`에서 `superseded_by`만 변경된 것을 확인 |
| [다중 Origin 헤더](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3782844263) | 회원 Refresh·Logout도 모든 Origin 헤더를 읽고 다중 값이면 403 | 애플리케이션 | 수정 필요 | 회원·관리자가 `TrustedOriginResolver`를 공유하고 회원 Refresh·Logout 다중 헤더 회귀 테스트를 추가했다. | `TrustedOriginResolverTest`, `MemberAuthenticationControllerTest`, `AdminAuthenticationControllerTest` 통과 |
| [회원 canonical Origin 1](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3783202424), [회원 canonical Origin 2](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3783206551) | 회원도 설정·요청 Origin을 canonical form으로 비교하고 기본 포트·대소문자 동등값을 허용 | 애플리케이션 | 수정 필요 | `OriginCanonicalizer`를 공통 컴포넌트로 추출하고 회원 설정을 기동 시 canonicalize·fail-fast했다. 회원 Refresh·Logout이 동등 Origin을 허용하도록 회귀 테스트를 추가했다. | `TrustedOriginResolverTest`, `MemberAuthenticationControllerTest`, `SecurityConfigurationApiTest` 통과 |
| [ADR 상태·적용 범위](https://github.com/team-youngkk/masit-on/pull/211#discussion_r3783202432) | AUTH-006이 AUTH-001 전체를 대체하지 않으므로 `supersedes`/`superseded_by`와 상태를 일치 | 기타(ADR 추적성) | 수정 필요 | 두 ADR을 모두 `Accepted` 보완 결정으로 유지하고 양쪽 대체 필드를 제거했다. AUTH-001의 관련 문서에 AUTH-006을 추가했다. | `adr-index.md`, `adr-traceability.md`, 두 ADR frontmatter 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 보안 경계가 우회될 수 있는 계약 불일치였다.
- 발생 환경: PR #211 `fix/admin-refresh-logout-origin`, Spring MVC `HttpServletRequest` 기반 인증 Controller/Filter.
- 재현 조건: 허용된 Origin과 다른 Origin을 같은 요청의 `Origin` 헤더에 두 개 전달하거나, 회원 설정을 `HTTPS://MASITON.CLICK:443`로 두고 브라우저 Origin `https://masiton.click`을 전달한다.
- 실제 결과: 관리자 Filter는 다중 값을 거부했지만 회원 Controller는 `getHeader`로 첫 번째 값만 비교할 수 있었고, 회원 설정은 raw 문자열 비교로 canonical 동등 Origin을 거부할 수 있었다.
- 기대 결과: 회원·관리자 Refresh·Logout 모두 단일 허용 Origin만 통과하고, 설정·후보는 동일한 canonical 정책으로 비교되며 다중 헤더는 Token 처리 전에 403이어야 한다.
- 영향 범위: 쿠키 기반 회원·관리자 Refresh·Logout의 CSRF 보조 방어 및 인증 계약 일관성.

## 4. 근본 원인

PR은 관리자 Filter에서 `getHeaders`를 사용했지만 회원 Controller는 `getHeader`를 사용했다. Servlet API의 단일 값 조회는 동일 이름의 추가 헤더를 검증하지 않으므로 회원 경계와 관리자 경계의 다중 Origin 처리 결과가 달라질 수 있었다. 또한 canonical Origin 파싱·정규화가 관리자 `SecurityProperties`에만 있어 회원 경계가 raw 설정 문자열을 비교했다. 관리자 Origin 방어 결정을 기존 Accepted ADR의 본문에 직접 추가하고 대체 관계를 잘못 표시해 결정 이력·적용 범위도 어긋났다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #211의 미해결 review thread와 변경 파일 확인 | ADR-AUTH-001 직접 수정과 회원 `getHeader` 사용을 확인 | 두 지적 모두 수정 필요로 판단 |
| 공통 resolver 도입 검토 | 공통 헤더 cardinality 검증과 경계별 allowlist 정책을 분리할 수 있음 | `common.web`에 정적 resolver를 두고 관리자·회원이 공유 |
| Accepted ADR 원복 후 후속 ADR 작성 | 기존 ADR 본문을 기준선으로 유지하고 Origin 결정을 ADR-AUTH-006으로 분리 가능 | `superseded_by`/`supersedes`, index·traceability를 함께 갱신 |
| 관리자 canonicalization과 회원 설정·Controller 비교 대조 | 관리자만 URI 정규화하고 회원은 raw 문자열을 비교 | URI 정규화 책임을 `OriginCanonicalizer`로 공통화하고 회원 설정·후보에 적용 |
| AUTH-001·AUTH-006의 결정 범위 대조 | AUTH-006은 쿠키 Origin 방어만 보완하고 JWT·키 회전·Redis를 대체하지 않음 | 두 ADR을 Accepted로 유지하고 관련 문서 링크만 남김 |

## 6. 최종 해결

- 변경 내용: `Origin` 헤더를 `getHeaders`로 정확히 하나만 추출하는 resolver와 URI 정규화·동등 비교를 담당하는 `OriginCanonicalizer`를 공통화했다. 회원 설정은 생성·기동 시 canonicalize·fail-fast하고 회원 Refresh·Logout 후보도 같은 정책으로 비교한다. 회원의 다중 헤더·동등 Origin 회귀 테스트를 추가했다.
- 선택 이유: 헤더 cardinality와 canonicalization을 모두 공통화해야 회원·관리자 쿠키 경계가 동일한 인증 계약을 보장할 수 있다. AUTH-006은 AUTH-001 전체를 대체하지 않는 보완 결정이므로 ADR 대체 관계 대신 관련 문서 링크로 추적한다.
- 변경 파일: `src/main/java/com/masiton/common/web/OriginCanonicalizer.java`, `src/main/java/com/masiton/common/web/TrustedOriginResolver.java`, `src/main/java/com/masiton/security/infrastructure/web/AdminCookieOriginFilter.java`, `src/main/java/com/masiton/common/security/MemberCookieSettings.java`, `src/main/java/com/masiton/member/presentation/MemberAuthenticationController.java`, `src/main/java/com/masiton/security/infrastructure/configuration/SecurityProperties.java`, 관련 테스트와 ADR 문서.
- 고려한 대안: 회원 Controller에만 `getHeaders` 로직을 복제하는 방법은 경계별 구현 재발 위험이 있어 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.common.web.TrustedOriginResolverTest --tests com.masiton.member.presentation.MemberAuthenticationControllerTest --tests com.masiton.security.presentation.AdminAuthenticationControllerTest --no-daemon --console=plain` | 통과 | resolver의 누락·단일·다중 헤더와 회원·관리자 인증 API 회귀 테스트 통과 |
| `./gradlew.bat test --tests com.masiton.security.infrastructure.configuration.SecurityConfigurationApiTest --tests com.masiton.security.infrastructure.web.MemberSessionRevocationFilterTest --tests com.masiton.security.infrastructure.web.SecurityErrorWriterTest --no-daemon --console=plain` | 통과 | 애플리케이션 기동 설정과 회원 세션 폐기·보안 오류 응답 회귀 테스트 통과 |
| `./gradlew.bat test --no-daemon --console=plain` | 통과 | 전체 테스트가 9분 37초에 성공. 일부 통합 테스트 종료 과정에서 기존 PostgreSQL 연결 경고가 있었으나 실패 테스트 없이 종료 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `git diff -- docs/07-adr/security/auth-001-spring-security-jwt.md docs/07-adr/security/auth-006-cookie-origin-defense.md` | 통과 | 두 ADR을 Accepted 보완 결정으로 유지하고 대체 필드를 제거 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 공통 resolver·canonicalizer 단위 테스트, 회원 Refresh·Logout 다중 Origin·동등 Origin 테스트, 회원 설정 fail-fast, ADR index·traceability 연결을 유지한다.
- 다음 확인: 현재 변경은 로컬 작업 트리에 반영되어 있다. PR 브랜치에 push한 뒤 네 개 원본 review thread에 변경·검증·기록 링크로 답변하고 해결 처리한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 다중 Origin 헤더 보안 경계 통과 여부 | 회원 경계에 `getHeader` 단일 값 비교 | 회원·관리자 Controller/Filter 회귀 테스트 | 0건 통과를 기대 | 회원·관리자 모두 403으로 차단 | PR #211 작성자, merge 전 CI |
| canonical 동등 Origin 허용 여부 | 회원 raw 문자열 비교로 기본 포트·대소문자 동등값 거부 가능 | canonicalizer·회원 Refresh·Logout 회귀 테스트 | 동등 Origin 허용 | 설정·후보를 동일 canonical form으로 비교 | PR #211 작성자, merge 전 CI |

## 10. 남은 사항

- 로컬 집중·설정 경계 테스트를 통과했다. 전체 테스트는 기존 PR HEAD에서 통과한 기록이 있으며, 이번 로컬 수정분은 아직 push하지 않았으므로 PR 브랜치 push 후 GitHub Actions에서 전체 빌드·테스트를 다시 확인해야 한다.
- 현재는 커밋·push 권한을 별도로 요청받지 않아 원격 review thread 답변·해결 처리는 보류한다.
