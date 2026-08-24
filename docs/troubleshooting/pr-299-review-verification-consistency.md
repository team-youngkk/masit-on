---
related_documents:
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
  - ../../frontend/package.json
  - ../../frontend/app/admin/layout.tsx
  - ../../frontend/app/globals.css
  - ../../frontend/components/layout/SiteHeader.tsx
  - ../../src/test/java/com/masiton/member/infrastructure/mail/MemberActionTokenMailAdapterTest.java
  - ../05-specs/api/README.md
---

# PR #299 리뷰 트러블슈팅: 검증 수치와 EOF 공백 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #299](https://github.com/team-youngkk/masit-on/pull/299) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-24 |
| 범위 | 프론트 테스트 수치와 `git diff --check` 결과 정정 |
| 주 문제 유형 | 기타(리뷰 검증 기록·저장소 공백 품질) |
| 기존 기록 | 기존 트러블슈팅 기록에서 동일 PR의 검증 수치·EOF 공백 문제는 확인되지 않았다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|
| [프론트 테스트 건수](https://github.com/team-youngkk/masit-on/pull/299#discussion_r3839979395) | 기타(검증 기록) | 수정 필요 | 현재 PR head에서 `npm test`를 다시 실행해 실제 311개 통과로 PR 본문 수치를 정정한다. |
| [EOF 빈 줄](https://github.com/team-youngkk/masit-on/pull/299#discussion_r3839981389) | 기타(저장소 공백 품질) | 수정 필요 | 변경된 프론트 9개 파일의 불필요한 EOF 빈 줄을 제거하고 `git diff --check`를 다시 실행한다. |

## 3. 문제 현상과 발생 조건

- PR 본문은 프론트 `npm test`를 305개 통과로 기록했지만, 리뷰 시점의 head `b168873d`에서는 307개가 실행되었다.
- 이후 `develop` 병합으로 테스트 목록이 추가된 현재 head `326a862`에서 동일 명령을 실행한 결과는 311개 통과였다.
- 프론트 변경 파일 9개는 파일 끝에 `CRLF` 빈 줄이 추가되어 리뷰 당시 `git diff --check`가 9건의 trailing whitespace와 new blank line at EOF를 보고했다.

## 4. 근본 원인과 증거

검증 결과를 커밋 시점에 고정하지 않고 PR 본문에 먼저 기록한 뒤, 리뷰 전 추가 커밋과 `develop` 병합으로 테스트 목록과 파일 끝 공백이 달라졌다. 따라서 코드 동작 문제가 아니라 PR의 검증 기록과 실제 패치 상태가 서로 다른 상태가 되었다.

확인한 증거는 다음과 같다.

- `326a862` 기준 `frontend/package.json`의 테스트 목록은 `login-page-navigation.test.ts`와 `participation-entry.test.ts`를 포함한다.
- 수정 전 `git diff --check 3b02017f...HEAD`는 프론트 9개 파일에서 EOF 공백을 보고했다.
- 수정 후 working tree에서 `git diff --check`는 오류 없이 종료했다.

## 5. 최종 해결

- `frontend/app/admin/layout.tsx`, `frontend/app/globals.css`, `frontend/app/me/page.module.css`, `frontend/app/me/page.tsx`의 파일 끝 빈 줄을 제거했다.
- `frontend/components/admin/AdminCurationScreen.module.css`, `frontend/components/admin/admin.module.css`, `frontend/components/layout/NotificationBell.tsx`, `frontend/components/layout/SiteHeader.module.css`, `frontend/components/layout/SiteHeader.tsx`의 파일 끝 빈 줄을 제거했다.
- PR 본문의 프론트 테스트 결과를 현재 head에서 측정한 `311개 통과, 실패 0개`로 정정한다.
- 기능 코드와 API·DB 계약은 변경하지 않았다.

## 6. 검증

| 검증 | 결과 | 확인 내용 |
|---|---|---|
| `npm test` | 통과 | `tests 311`, `pass 311`, `fail 0` |
| `npm run typecheck` | 통과 | TypeScript 검사 통과 |
| `git diff --check` | 통과 | EOF 공백 및 trailing whitespace 0건 |
| `./gradlew.bat test --tests com.masiton.member.infrastructure.mail.MemberActionTokenMailAdapterTest` | 통과 | 메일 인증·비밀번호 재설정 테스트 2건 통과 |

## 7. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법 | 도입 후 |
|---|---:|---|---:|
| PR 본문 프론트 테스트 수치 | 305개(본문), 307개(리뷰 시점 head) | 현재 PR head에서 `npm test` 실행 | 311개 통과, 실패 0개 |
| `git diff --check` 공백 오류 | 9개 파일 오류 | 현재 PR head와 base 비교 후 working tree 재검사 | 0건 |

## 8. 재발 방지 및 남은 사항

- PR 본문의 테스트 수와 공백 검사 결과는 추가 커밋·병합 직후 동일 명령으로 재실행한 결과만 기록한다.
- 테스트 파일 목록이나 병합으로 실행 수가 바뀌면 PR 본문과 이 기록을 함께 갱신한다.
- 운영 지표가 필요한 기능 변경이 아니므로 배포 후 비교 지표는 해당 없음이다.
