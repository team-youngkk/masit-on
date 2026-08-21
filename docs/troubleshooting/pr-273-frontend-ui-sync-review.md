---
related_documents:
  - ../../04-product/prd/account/member-authentication.md
  - ../../05-specs/api/account/member-authentication-api.md
  - README.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #273 리뷰 트러블슈팅: 화면 동기화 후 이메일 재발송·상태 배지·원본 자산 정리

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#273 맛잇온 화면과 인증 흐름을 동기화한다](https://github.com/team-youngkk/masit-on/pull/273) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-21 |
| 범위 | 미해결 인라인 리뷰 4건(`jinyp01` 2건, `tjdgns0618` 2건) |
| 주 문제 유형 | 애플리케이션 — 인증 재발송·관리자 상태 표현·프런트 자산 구성 |
| 기존 기록 | [PR #100 이메일 인증 후속 흐름](pr-100-email-verification-review.md), [PR #124 가입 이메일 인증 코드](pr-124-email-verification-code-review.md)를 확인했다. 접수 이메일 보존과 장애별 Token 처리 기록은 있었지만, 인증 페이지 진입 직후 재발송 폼 노출과 원본 PNG 정리는 이번 PR에서 새로 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거·검증 |
|---|---|---|---|---|---|
| [가입 직후 재발송 경로 — jinyp01](https://github.com/team-youngkk/masit-on/pull/273#discussion_r3828165538) | `showResend`가 `false`로 시작해 메일 미수신 사용자가 재발송을 시작할 수 없음 | 애플리케이션 | 수정 필요 | 인증 페이지에서 재발송 폼을 기본 노출하고, 429·503·네트워크 오류 뒤에는 기존 계약대로 기본 다음 행동에서 숨김 | 프런트 테스트 288건, 타입 검사, 프로덕션 빌드 |
| [사용하지 않는 원본 PNG — jinyp01](https://github.com/team-youngkk/masit-on/pull/273#discussion_r3828165549) | 런타임에서 참조하지 않는 PNG 30개가 약 56.75MiB를 차지함 | 기타 (저장소 자산 구성) | 수정 필요 | `frontend/assets/restaurant-placeholders/food-scenes-final` 추적 PNG 30개 제거. 현재 작업 트리의 별도 미추적 `public/images` 폴더는 보존 | 삭제 대상 30개·59,505,711 bytes 확인, `git diff --check` |
| [가입 직후 재발송 경로 — tjdgns0618](https://github.com/team-youngkk/masit-on/pull/273#discussion_r3828170156) | 가입 접수 후 인증 페이지에서 재발송 폼이 사라짐 | 애플리케이션 | 수정 필요 | 위 스레드와 동일한 원인에 대해 `VerifyEmail`의 초기 노출 상태를 수정 | 동일 변경 및 프런트 검증 |
| [자동 확정·수동 보정 상태 톤 — tjdgns0618](https://github.com/team-youngkk/masit-on/pull/273#discussion_r3828170167) | 목록의 `reviewTone`이 `AUTO_CONFIRMED`·`MANUAL_OVERRIDE`를 `neutral`로 표시함 | 애플리케이션 | 수정 필요 | 두 상태를 상세 화면과 같은 `success` 톤으로 매핑 | 프런트 테스트 288건, 타입 검사, 프로덕션 빌드 |

## 3. 문제 현상과 발생 조건

- 회원가입 접수 성공 후 `/verify-email`로 이동하면 `showResend`가 `false`라서 인증 코드 제출이 400으로 끝나기 전에는 재발송 폼이 렌더링되지 않았다.
- 메일 지연·미수신 사용자는 임의의 코드를 먼저 제출해야 하므로, 인증 코드가 없는 상태에서 가입 완료 경로가 막혔다.
- `AiVideoExtractionList`의 목록 `reviewTone`은 `AUTO_BLOCKED`와 `AUTO_REJECTED`만 별도 처리하고 성공 상태를 기본 `neutral`로 돌려 상세 화면과 표현이 달랐다.
- `frontend/assets/restaurant-placeholders/food-scenes-final`의 PNG 30개는 `frontend/lib/restaurant-placeholder-image.ts`가 생성하는 런타임 경로에서 사용되지 않았다.

## 4. 근본 원인

인증 페이지의 재발송 표시 상태를 인증 코드 제출 결과(`shouldOfferResend`)에만 연결하고 초기 상태를 `false`로 둔 것이 두 이메일 리뷰의 공통 원인이다. 이는 400 확정 실패 뒤 재발송을 보여 주는 기존 정책은 보존했지만, 가입 접수 직후의 독립적인 복구 경로를 제거했다.

목록 화면은 상세 화면의 상태 톤 매핑을 공유하지 않고 별도로 작성하면서 성공 상태 두 값을 누락했다. 원본 PNG는 WebP 변환 결과와 별개로 저장소에 포함됐지만 애플리케이션 참조 경로가 없어 런타임 기여가 없었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #273의 작성자·미해결 인라인 스레드·최신 댓글 확인 | 작성자는 `w00lam`, 미해결 스레드 4건 확인 | 4건 모두 현재 코드에서 재현 가능하므로 수정 대상으로 분류 |
| `VerifyEmail.tsx`와 이메일 인증 조정 함수 대조 | 초기 `showResend=false`, 400에서만 `true`; 429·503·네트워크 오류는 `false` | 초기 표시만 `true`로 바꾸고 일시 오류 후 숨김 정책은 유지 |
| 회원 인증 PRD·API 계약과 기존 PR #100·#124 기록 대조 | 접수 이메일 재발송 보존, 재발송 제한, 일시 오류 시 기본 다음 행동 비노출 확인 | API·서버 계약 변경 없이 화면 상태만 수정 |
| 목록·상세 `reviewTone` 대조 | 상세는 `AUTO_CONFIRMED`·`MANUAL_OVERRIDE`를 `success`로 처리하고 목록은 누락 | 목록 매핑을 상세와 일치시킴 |
| `frontend/assets/restaurant-placeholders/food-scenes-final` 참조 및 Git tree 확인 | 코드 참조 없음, 추적 PNG 30개·59,505,711 bytes(56.75MiB) | 해당 원본만 제거하고 미추적 `public/images` 자산은 변경하지 않음 |

## 6. 최종 해결

- `frontend/components/member/VerifyEmail.tsx`의 `showResend` 초기값을 `true`로 바꿔 인증 페이지 진입 직후 재발송 폼을 노출한다. 인증 제출 시작 시 상태를 초기화하고, 429·503·네트워크 오류 결과에서는 재발송을 기본 다음 행동으로 다시 숨기는 기존 조정 로직은 유지했다.
- `frontend/components/admin/AiVideoExtractionList.tsx`에서 `AUTO_CONFIRMED`와 `MANUAL_OVERRIDE`를 `success` 톤으로 매핑해 상세 화면과 목록 화면의 상태 표현을 일치시켰다.
- 런타임에서 참조되지 않는 `frontend/assets/restaurant-placeholders/food-scenes-final` PNG 30개를 제거했다. WebP 제공 경로와 현재 작업 트리의 별도 미추적 자산은 유지했다.

변경은 API·DB 계약이나 새 의존성을 추가하지 않는 최소 범위로 선택했다. 재발송 제한은 서버 응답과 기존 조정 함수가 계속 담당하므로 클라이언트에서 제한을 복제하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` (`frontend`) | 통과, 288건 | 기존 인증 오류별 재발송 노출·Token 보존 정책과 관리자·이미지 관련 회귀 포함 |
| `npm.cmd run typecheck` (`frontend`) | 통과 | TypeScript 오류 없음 |
| `npm.cmd run build` (`frontend`) | 통과 | Next.js 프로덕션 빌드와 31개 라우트 생성 확인 |
| `git diff --check` | 통과 | 코드·문서·삭제 패치의 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 인증 페이지의 초기 복구 경로와 인증 제출 후 오류별 다음 행동을 함께 확인하는 브라우저 검증이 필요하다. 이번 저장소의 순수 조정 테스트는 400·429·503·네트워크 오류 분기를 계속 고정한다.
- 목록과 상세에 같은 상태 집합을 표시하는 화면은 상태별 톤 매핑을 대조한다.
- 생성·변환 원본을 저장소에 포함할 때 런타임 참조 여부와 Git 용량을 PR 검토 시 확인한다.
- 실제 메일 전달·429/503 서버 응답과 브라우저 렌더링은 외부 서비스·브라우저 환경이 필요하므로 로컬 프런트 테스트 범위 밖이며, CI/브라우저 검증에서 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 |
|---|---|---|---|---|
| 인증 페이지 진입 직후 재발송 폼 노출 | `showResend=false`, 코드 400 전에는 미노출 | `VerifyEmail.tsx` 초기 상태와 400·429·503 분기 대조 | 초기 `true`, 일시 오류 뒤 `false` | 가입 직후 재발송 경로를 복구하고 일시 오류 정책은 유지 |
| 목록 성공 상태 톤 매핑 | `AUTO_CONFIRMED`·`MANUAL_OVERRIDE` 0건 | `reviewTone` 조건 검색 | 2개 상태를 `success`로 매핑 | 상세 화면과 상태 표현 일치 |
| 저장소 미사용 원본 PNG | 30개, 59,505,711 bytes | `git ls-tree`·`git cat-file`로 HEAD 기준 측정 | 0개 | PR에서 런타임 미사용 원본 제거 |
| 프런트 회귀 테스트 | 288건 기준 | `npm.cmd test` | 288건 통과 | 기존 기능 회귀 없음 |

성능 지표는 이번 변경의 목적이 아니므로 별도로 측정하지 않았다.

## 10. 남은 사항

코드와 문서를 원격 PR 브랜치에 반영한 뒤 네 개의 인라인 스레드에 각각 원인·변경·검증·이 기록 링크를 답글로 남기고 해결 처리한다. 원격 CI와 실제 메일 발송은 이 로컬 수정 기록에 포함하지 않는다.

