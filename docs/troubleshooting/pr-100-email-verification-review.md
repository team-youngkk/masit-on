---
related_documents:
  - README.md
  - ../04-product/prd/account/member-authentication.md
  - ../05-specs/api/account/member-authentication-api.md
---

# PR #100 리뷰 트러블슈팅: 이메일 인증 후속 흐름

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#100 회원가입 이메일 인증 토큰 입력 흐름 추가](https://github.com/team-youngkk/masit-on/pull/100) |
| 작성자 | 김인안 (`inan0226`) |
| 처리 일자 | 2026-08-03 |
| 범위 | 가입 접수 이메일 재발송 고정과 이메일 인증 장애 분류 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [가입 접수 시 사용한 이메일을 재발송 대상으로 고정](https://github.com/team-youngkk/masit-on/pull/100#discussion_r3701430980) | 가입 성공 후 변경된 이메일로 재발송되지 않게 함 | 수정 필요 | 접수 이메일을 별도 상태로 보존하고 입력을 읽기 전용으로 전환 | 상태 전환·재발송 대상 회귀 테스트 통과 |
| [일시 장애를 잘못된 토큰으로 처리하지 않음](https://github.com/team-youngkk/masit-on/pull/100#discussion_r3701458148) | 확정된 토큰 오류와 재시도 가능한 장애를 구분 | 수정 필요 | `400`에서만 토큰 정리·재발송 안내, `503`·네트워크 오류에서는 토큰 보존·재시도 안내 | 오류 유형별 회귀 테스트 통과 |

## 3. 문제 현상

- 재현 조건 1: 회원가입 접수 성공 후 이메일 입력값을 다른 주소로 바꾸고 재발송 버튼을 누른다.
- 실제 결과 1: 재발송 요청이 가입 접수에 사용한 이메일이 아니라 변경된 이메일을 사용한다.
- 기대 결과 1: 가입 접수 당시 이메일을 재발송 대상으로 고정한다.
- 재현 조건 2: 유효할 수 있는 인증 Token을 제출하는 동안 서버가 `503`을 반환하거나 네트워크 요청이 실패한다.
- 실제 결과 2: Token이 잘못됐다는 안내가 표시되고 입력 Token이 지워지며 재발송 화면이 열린다.
- 기대 결과 2: 계약상 확정된 `400` Token 오류만 무효로 처리하고, 일시 장애에서는 Token을 유지해 재시도할 수 있게 한다.
- 영향 범위: 회원가입 직후 인증 메일 재발송 대상과 이메일 인증 실패 UX다. API·DB 계약은 변경하지 않는다.

## 4. 근본 원인

회원가입 성공 여부만 상태로 저장하고 접수에 사용한 이메일을 별도로 보존하지 않아, 재발송 핸들러가 계속 수정 가능한 폼의 `email` 상태를 참조했다.

이메일 인증 조정 함수는 `verifyMemberEmail`이 던지는 `Response` 상태를 검사하지 않고 모든 예외를 같은 Token 오류로 변환했으며, `finally`에서 성공 여부와 실패 종류에 관계없이 Token을 제거했다. API 계약은 변조·만료·재사용 등의 확정 실패를 `400 INVALID_EMAIL_VERIFICATION_TOKEN`으로, 인증 상태 저장소 장애를 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`로 구분한다.

## 5. 해결

- 변경 내용: 가입 접수 이메일과 읽기 전용 전환 상태를 `AcceptedMemberRegistration`에 보존하고 재발송은 이 상태의 이메일만 사용한다.
- 변경 내용: 이메일 인증 성공과 `400`에서만 Token을 정리한다. 그 외 HTTP 상태와 네트워크 오류에서는 Token을 보존하고 일반 재시도 안내를 표시하며 재발송 화면을 열지 않는다.
- 선택 이유: 기존 API 계약을 바꾸지 않으면서 사용자가 시작한 가입과 재발송 대상을 일치시키고, 재시도 가능한 장애에서 유효 Token을 잃지 않게 하기 위해서다.
- 변경 파일: `frontend/components/member/MemberAuthForm.tsx`, `frontend/components/member/member-auth-form-coordination.ts`, `frontend/components/member/member-auth-form-coordination.test.ts`, `frontend/components/member/VerifyEmail.tsx`, `frontend/lib/member/email-verification-coordination.ts`, `frontend/lib/member/email-verification-coordination.test.ts`

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd --prefix frontend test` | 통과 | 프런트엔드 테스트 65건, 접수 이메일 고정과 `400`·`503`·네트워크 오류 분기 포함 |
| `npm.cmd --prefix frontend run typecheck` | 통과 | TypeScript 오류 없음 |
| `npm.cmd --prefix frontend run build` | 통과 | Next.js 프로덕션 빌드와 전체 프런트엔드 테스트 통과 |

## 7. 재발 방지

- 가입 접수 후 입력 상태와 재발송 요청 대상이 달라지지 않는 회귀 테스트를 추가했다.
- 인증 오류를 확정 실패와 재시도 가능 장애로 나눠 Token 정리 여부와 재발송 노출 여부를 검증한다.

## 8. 남은 사항

- 실제 메일 전달과 운영 프록시·APM의 Token 본문 마스킹은 이 프런트엔드 변경 범위에서 검증하지 않았다.
