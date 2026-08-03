---
related_documents:
  - README.md
  - ../05-specs/api/account/member-authentication-api.md
  - ../07-adr/security/auth-005-member-action-mail-outbox.md
  - ../08-planning/expansion-1-task-breakdown.md
---

# PR #124 리뷰 트러블슈팅: 가입 이메일 인증 코드 입력·제한 정합화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#124 가입 이메일 인증 8자 코드 전환](https://github.com/team-youngkk/masit-on/pull/124) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-03 |
| 범위 | Redis 제출 제한 Lua, 이메일 인증 요청 검증 위치, 프론트엔드 코드 붙여넣기 리뷰 3건 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [Redis Lua 도달 불가능 분기](https://github.com/team-youngkk/masit-on/pull/124#discussion_r3703584564) | 제출 횟수 증가 뒤의 중복 초과 분기 제거 검토 | 수정 필요 | 원자적 Lua에서 도달할 수 없는 두 번째 초과 분기를 제거 | 10회 허용·11번째 차단 Redis 통합 테스트로 경계 유지 확인 |
| [Controller Token 길이 상한](https://github.com/team-youngkk/masit-on/pull/124#discussion_r3703584569) | `@Valid` 제거로 `@Size(max = 200)`도 사라진 것이 의도인지 확인 | 수정 불필요 | Controller 길이 검증을 복원하지 않음 | 출처 제한을 모든 제출에 먼저 적용한 뒤 Application의 `^[A-HJ-NP-Z2-9]{8}$`로 정확한 계약 형식을 검증함 |
| [정규화 전 브라우저 잘림](https://github.com/team-youngkk/masit-on/pull/124#discussion_r3703598743) | 앞뒤 공백이 있는 8자 코드를 붙여넣어도 코드가 잘리지 않게 수정 | 수정 필요 | 입력의 `maxLength={8}`을 제거하고 공백 포함 붙여넣기 회귀 예시를 테스트에 추가 | 정규화 함수가 ` AB7K9M2Q `를 `AB7K9M2Q`로 보존함을 프론트 테스트로 확인 |

## 3. 문제 현상

- 재현 조건: 이메일 인증 화면에 앞뒤 ASCII 공백이 포함된 유효 코드 ` AB7K9M2Q `를 붙여넣는다.
- 실제 결과: 브라우저가 `onChange`보다 먼저 `maxLength={8}`을 적용해 문자열을 8자로 자르고, 이후 공백을 제거하면 유효 코드 일부가 사라진다.
- 기대 결과: 앞뒤 ASCII 공백을 제거하고 영문을 대문자로 바꾼 결과 `AB7K9M2Q`가 서버로 제출돼야 한다.
- 영향 범위: 메일에서 코드를 공백과 함께 복사한 사용자의 가입 이메일 인증이 실패한다. Redis 제한의 중복 분기는 동작 영향은 없지만 경계 로직의 이해와 유지보수를 방해한다.

## 4. 근본 원인

프론트 입력의 HTML 길이 제한과 애플리케이션 정규화 순서가 서로 달랐다. `maxLength`는 브라우저가 입력 이벤트 전에 적용하지만, 공백 제거와 대문자 변환은 React `onChange`에서 수행돼 정규화 전에 원문이 손실됐다.

Redis Lua는 현재 횟수가 제한 이상인지 증가 전에 이미 판정하고 즉시 반환한다. 스크립트 실행은 원자적이므로 그 판정을 통과한 값은 최대 9이고 한 번 증가해도 최대 10이다. 따라서 증가 직후 `attempts > 10` 분기는 실행될 수 없었다.

Controller의 `@Valid` 제거는 의도된 검증 순서 변경이다. 형식이 잘못된 제출도 출처 제한 횟수에 포함해야 하므로 Application Service가 제한을 먼저 획득하고, 그 뒤 정확히 8자인 정규식으로 형식을 검증한다. 기존 200자 상한을 Controller에 복원하면 형식 오류가 제한을 우회하므로 적용하지 않았다.

## 5. 해결

- 변경 내용:
  - `VerifyEmail.tsx`의 정규화 전 `maxLength` 제한 제거
  - 공백 포함 8자 코드 붙여넣기 정규화 회귀 테스트 추가
  - Redis 제출 제한 Lua의 도달 불가능한 중복 초과 분기 제거
- 선택 이유: 서버 계약이 정한 정규화를 먼저 완료한 뒤 고정 길이 형식을 서버에서 판정하게 하며, Redis의 10회 허용·11번째 차단 동작은 유지하는 최소 변경이다.
- 변경 파일: `frontend/components/member/VerifyEmail.tsx`, `frontend/lib/member/email-verification-coordination.test.ts`, `src/main/java/com/masiton/member/infrastructure/redis/RedisMemberRateLimitStore.java`
- 고려한 대안: 붙여넣기 이벤트만 별도로 가로채 정규화한 뒤 8자로 제한할 수 있으나 키보드·자동완성·붙여넣기 경로마다 처리 순서가 달라질 수 있다. 서버가 정확한 8자 형식을 검증하므로 HTML 길이 제한을 제거하는 편이 단순하고 일관적이다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` (`frontend`) | 통과, 67건 | 공백 포함 붙여넣기가 8자 코드를 보존하고 기존 성공·오류 정책이 유지됨 |
| `npm.cmd run typecheck` (`frontend`) | 통과 | 제거된 상수 import 이후 타입 오류가 없음 |
| `.\gradlew.bat test --tests com.masiton.member.infrastructure.redis.RedisMemberRateLimitStoreIntegrationTest --tests com.masiton.member.application.MemberAuthenticationServiceTest --tests com.masiton.member.presentation.MemberAuthenticationControllerTest --no-daemon --console=plain` | 통과, 29건 | Redis 10회/11번째 경계와 Application 우선 제한·형식 검증이 유지됨 |
| `git diff --check` | 통과 | 공백·줄 끝 오류가 없음 |

## 7. 재발 방지

- API가 정규화를 허용하는 입력은 HTML 길이 제한이 정규화보다 먼저 원문을 훼손하지 않는지 대표 붙여넣기 값으로 검증한다.
- 원자적 Lua 제한 스크립트는 증가 전·후 경계 중 한 위치에서만 초과를 판정하고 실제 경계 통합 테스트로 보장한다.

## 8. 남은 사항

- 없음.
