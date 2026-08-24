---
related_documents:
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/account/member-authentication.md
  - ../05-specs/api/account/member-authentication-api.md
  - ../07-adr/security/auth-005-member-action-mail-outbox.md
  - ../../src/main/java/com/masiton/member/infrastructure/configuration/MemberActionMailProperties.java
  - ../../frontend/components/member/MemberAuthForm.tsx
---

# PR #302 리뷰 트러블슈팅: 비밀번호 재설정 URL과 최신 fragment 처리

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #302](https://github.com/team-youngkk/masit-on/pull/302) |
| 작성자 | [@w00lam](https://github.com/w00lam) |
| 처리 일자 | 2026-08-24 |
| 범위 | 비밀번호 재설정 URL의 운영 HTTPS 강제와 동일 탭 fragment 갱신 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #299 검증 수치와 EOF 공백 정합성](pr-299-review-verification-consistency.md)을 확인했으나, 이번 두 증상과 동일한 기록은 없어 신규 기록으로 작성했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [운영 URL HTTPS 강제](https://github.com/team-youngkk/masit-on/pull/302#discussion_r3840275819) | 운영 환경 변수로 HTTP 재설정 URL이 주입되지 않도록 검증 | 애플리케이션 | 수정 필요 | `prod` 프로필에서 HTTPS만 허용하고 비운영 HTTP는 loopback만 허용하는 검증과 회귀 테스트 추가 | `MemberActionMailPropertiesTest`, 백엔드 대상 테스트 통과 |
| [동일 탭 최신 토큰](https://github.com/team-youngkk/masit-on/pull/302#discussion_r3840275824) | `hashchange` 때 새 fragment 토큰을 다시 읽고 주소에서 제거 | 애플리케이션 | 수정 필요 | fragment 소비·주소 제거·구독을 `watchPasswordResetToken`으로 분리하고 `hashchange` 이벤트를 구독 | 프론트 테스트 313개 통과, TypeScript 검사 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음
- 발생 환경: PR #302 head, Spring Boot 설정 바인딩과 Next.js 클라이언트 비밀번호 재설정 화면
- 재현 조건:
  - `prod` 프로필에서 `PASSWORD_RESET_PUBLIC_URL=http://...`을 설정한다.
  - 비밀번호 재설정 화면이 열린 상태에서 새 메일의 `#token=...` 링크를 같은 탭으로 연다.
- 실제 결과: 운영 HTTP URL이 설정 검증을 통과할 수 있었고, 같은 탭의 새 fragment가 기존 React 상태에 반영되지 않았다.
- 기대 결과: 운영에서는 HTTPS가 아니면 기동 시 거부하고, 화면이 열린 뒤에도 최신 fragment 토큰을 사용한다.
- 영향 범위: 운영에서 재설정 토큰이 HTTP 화면으로 전달될 위험과, 최신 재설정 메일을 사용한 정상 회원의 비밀번호 변경 실패

## 4. 근본 원인

첫 번째 문제의 원인은 URL 형식 검증이 scheme 존재만 확인하고 현재 프로필을 고려하지 않았던 것이다. 따라서 `application-prod.yml`의 HTTPS 기본값은 환경 변수로 HTTP가 주입되는 경우를 막지 못했다.

두 번째 문제의 원인은 토큰을 읽는 `useEffect`가 `mode` 변경 때만 실행되고 `window.location.hash` 변경을 구독하지 않았던 것이다. 서버가 새 재설정 토큰을 발급하면 이전 토큰이 폐기되므로, 새 fragment를 읽지 못하면 사용자가 정상 링크를 열어도 이전 토큰으로 요청하게 된다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `MemberActionMailProperties`와 `application-prod.yml`의 URL 바인딩 확인 | 운영 기본값은 HTTPS지만 환경 변수 재정의를 별도로 거부하지 않음 | 프로필 기반 기동 검증 추가 |
| 기존 `extractPasswordResetToken`과 `MemberAuthForm` effect 확인 | 초기 마운트·mode 변경 외의 `hashchange` 경로가 없음 | 이벤트 구독과 해제 가능한 정리 함수 추가 |
| 기존 `docs/troubleshooting` 검색 | 동일 증상의 기존 기록 없음 | PR #302 전용 기록 작성 |
| 프론트 회귀 테스트 실행 | 새 토큰이 초기 읽기와 hash 변경에 반영되고 unsubscribe 후 무시됨 | 수정 채택 |
| 백엔드 URL 설정 회귀 테스트 실행 | prod HTTP 거부, prod HTTPS 허용, test loopback HTTP 허용 | 수정 채택 |

## 6. 최종 해결

- 변경 내용:
  - `MemberActionMailProperties`가 `prod` 프로필에서 HTTPS만 허용하도록 변경했다.
  - 비운영 HTTP 재설정 URL은 `localhost`, `127.0.0.1`, `::1` loopback만 허용한다.
  - `watchPasswordResetToken`이 초기 fragment와 `hashchange` fragment를 읽고 주소 제거 콜백을 실행하도록 분리했다.
  - 이벤트 구독 해제와 운영 URL 경계를 회귀 테스트로 고정했다.
- 선택 이유: 환경 변수로 운영 URL이 덮어써지는 경로를 프로필 판정으로 차단하면서 로컬·테스트의 loopback 개발 URL은 유지하고, 브라우저 이벤트 수명 주기를 effect cleanup과 일치시킬 수 있다.
- 변경 파일:
  - `src/main/java/com/masiton/member/infrastructure/configuration/MemberActionMailProperties.java`
  - `src/test/java/com/masiton/member/infrastructure/configuration/MemberActionMailPropertiesTest.java`
  - `frontend/components/member/MemberAuthForm.tsx`
  - `frontend/components/member/member-auth-form-coordination.ts`
  - `frontend/components/member/member-auth-form-coordination.test.ts`
  - `docs/troubleshooting/pr-302-password-reset-review.md`
  - `docs/troubleshooting/README.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm test` | 통과 | 프론트 테스트 313개 통과 |
| `npm run typecheck` | 통과 | TypeScript 검사 통과 |
| `./gradlew.bat test --tests com.masiton.member.infrastructure.configuration.MemberActionMailPropertiesTest --tests com.masiton.member.infrastructure.mail.MemberActionTokenMailAdapterTest --no-daemon` | 통과 | URL 프로필 경계와 기존 메일 렌더링 테스트 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 운영 HTTP URL 거부, 비운영 loopback HTTP 허용, 초기·hashchange 최신 토큰 반영과 cleanup을 자동 테스트로 고정했다.
- 다음 확인: 실제 SMTP 전송과 실제 브라우저 메일 링크 렌더링은 기존 PR 본문의 운영 전 확인 항목으로 남아 있으며, 이 수정 자체에서 외부 연동을 호출하지 않았다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 비밀번호 재설정 URL 보안 위반 | 재현 가능한 설정 경로 1건 | prod HTTP 설정 테스트 | 확인 예정 | 배포 후 운영 설정에서 HTTP 기동 실패 여부 확인 | 인증 담당자, 운영 배포 전 |
| 동일 탭 최신 토큰 반영 | hashchange 미처리 | 프론트 회귀 테스트 | 해당 없음 | 이벤트 처리와 cleanup을 테스트로 고정 | 해당 없음 |

## 10. 남은 사항

- 없음. 실제 SMTP 전송과 브라우저 렌더링은 PR 범위 밖 외부 시스템 검증으로 남겨 두었다.
