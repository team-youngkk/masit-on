---
related_documents:
  - ../04-product/prd/participation/user-submission-report.md
  - ../05-specs/api/participation/submission-report-api.md
  - ../../frontend/app/me/requests/new/page.tsx
  - ../../frontend/components/participation/ParticipationRequestScreen.tsx
  - ../../frontend/lib/api.ts
  - ../../frontend/lib/member/participation-entry.ts
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #296 리뷰 트러블슈팅: 신고 대상 컨텍스트와 fallback 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #296](https://github.com/team-youngkk/masit-on/pull/296) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-23 |
| 범위 | 맛집 상세 신고 컨텍스트, 비맛집 신고 경로 보존, 상세 조회 실패 상태 분리 |
| 주 문제 유형 | 애플리케이션 / 기타(계약·화면 흐름) |
| 기존 기록 | 관련 트러블슈팅 기록에서 동일 증상은 확인되지 않았다. 사용자 제보·신고 API 계약과 PRD를 기준으로 판단했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [targetLabel 신뢰 경계](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3837794498) | URL의 라벨을 그대로 표시하지 말고 `targetId` 기준 서버 조회 결과를 사용 | 애플리케이션 | 이미 해결 | 서버 조회 결과의 ID·이름만 `initialTargetLabel`로 전달하고, 검증 성공한 경우에만 읽기 전용 요약을 표시하도록 유지·보강 |
| [비맛집 신고 경로 보존 1](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3837814610) | `CREATOR`·`VIDEO`·`VISIT_RELATIONSHIP` 신고를 제보 화면으로 바꾸지 않음 | 애플리케이션 | 수정 필요 | 쿼리의 `kind`, `targetType`, `targetId`를 정규화하되 신고 흐름은 그대로 유지 |
| [비맛집 신고 경로 보존 2](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3837833133) | 기존 계약 대상의 신고 입력을 유지 | 애플리케이션 | 수정 필요 | 맛집 상세 신고만 서버 컨텍스트 조회하고 그 외 신고는 일반 신고 입력으로 처리 |
| [비맛집 신고 대상 고정](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3838249484) | 대상 유형을 항상 `RESTAURANT`으로 고정하지 않음 | 애플리케이션 | 수정 필요 | 유효한 `targetType`을 `ParticipationRequestScreen`까지 전달 |
| [비맛집 신고 흐름 1](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3838256014) | `/api/me/reports`로 이어지는 기존 신고 입력 복구 | 애플리케이션 | 수정 필요 | `kind=report`를 보존해 신고 payload와 API 경로가 유지되도록 수정 |
| [비맛집 신고 흐름 2](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3838284720) | 계약된 나머지 대상 유형의 신고 입력을 복구 | 애플리케이션 | 수정 필요 | `CREATOR`·`VIDEO`·`VISIT_RELATIONSHIP`에 대한 대상 식별자 입력과 신고 유형 선택을 유지 |
| [조회 실패 fallback 분리](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3838285334) | 5xx·네트워크 오류를 제보 fallback으로 바꾸지 않음 | 애플리케이션 | 수정 필요 | 404/400만 일반 신고 입력으로 fallback하고, 그 밖의 오류는 traceId를 포함한 오류·재시도 상태로 표시 |
| [오류 상태 렌더링 재리뷰](https://github.com/team-youngkk/masit-on/pull/296#discussion_r3838611838) | 전달된 `initialLoadError`를 실제 오류·재시도 UI에 사용하고 신고 입력을 차단 | 애플리케이션 | 수정 필요 | `ParticipationRequestScreen`에서 `StatePanel` 오류 상태와 재시도 링크를 렌더링 |

위 표의 스레드 링크는 GitHub에 게시될 최종 PR에서 확인할 수 있도록 원문 위치를 남긴다. 비맛집 신고 흐름과 조회 실패 fallback 요청은 서로 다른 인라인 코멘트였지만, 같은 진입 분기 변경 묶음으로 처리했다.

## 3. 문제 현상과 발생 조건

- `kind=report`와 `targetType=CREATOR|VIDEO|VISIT_RELATIONSHIP`인 기존 신고 URL이 `contextualReport=null` 경로에서 `submission`으로 바뀌고, 대상 유형도 `RESTAURANT`으로 고정되었다.
- 그 결과 사용자는 기존 신고 URL로 들어와도 제보 폼을 보고, 신고 API 대신 제보 API로 전송할 수 있었다.
- 맛집 상세 신고 컨텍스트 조회의 모든 예외가 `null`로 처리되어, 상세 API의 5xx나 네트워크 장애도 정상적인 제보 화면으로 오인될 수 있었다.
- URL의 `targetLabel`은 신뢰할 수 없으므로 서버 조회 전에는 표시용 이름으로 사용할 수 없다.
- 조회 실패 정보를 prop으로 전달했지만 화면에서 소비하지 않으면, 오류 상태를 구분해도 실제 신고 입력이 계속 노출될 수 있다.

## 4. 근본 원인

`new/page.tsx`가 맛집 상세에서 진입한 신고와 일반 신고 URL을 하나의 `contextualReport` truthy 여부로만 분기했다. 서버 조회 결과가 없으면 원래 요청의 `kind`와 `targetType`을 복원하지 않고 제보 기본값으로 재구성했으며, 조회 예외의 종류도 구분하지 않았다. 또한 화면은 `initialTargetId`가 있으면 서버 검증 여부와 관계없이 읽기 전용 요약을 표시할 수 있는 구조였고, 첫 수정에서는 `initialLoadError`를 prop으로만 전달해 렌더링하지 않는 누락이 남았다.

## 5. 확인 및 시도

| 확인 항목 | 결과 | 판단 |
|---|---|---|
| 승인된 API 계약의 신고 대상 유형 확인 | `RESTAURANT`, `CREATOR`, `VIDEO`, `VISIT_RELATIONSHIP` 모두 신고 대상이며 신고 접수 경로는 `/api/me/reports` | 비맛집 신고 흐름을 제보로 전환하면 계약 위반 |
| `getRestaurantDetail` 예외 클래스 확인 | 404 `RestaurantNotFoundError`, 400 `RestaurantIdentifierInvalidError`, 제공자·기타 실패 `RestaurantDetailUnavailableError`로 구분됨 | 404/400만 입력 fallback, 나머지는 오류 상태로 분리 |
| 화면의 대상 요약 조건 확인 | URL 식별자만으로 요약을 표시하면 서버 검증 전 신뢰 경계가 없음 | `initialTargetVerified`를 별도 prop으로 추가 |
| 기존 프런트 테스트 실행 | 기존 테스트와 새 쿼리 정규화 회귀 테스트가 모두 통과 | 계약 대상 보존과 기본값 정규화를 고정 |

## 6. 최종 해결

- `frontend/lib/member/participation-entry.ts`에서 `kind`, `targetType`, `targetId`를 정규화하고, 서버 컨텍스트 조회 대상은 `report + RESTAURANT + targetId`로 한정했다.
- `frontend/app/me/requests/new/page.tsx`는 일반 신고의 `kind`·대상 유형·식별자를 보존한다. 맛집 조회가 성공한 경우에만 서버 반환 ID·이름을 화면에 전달한다.
- 404 또는 식별자 형식 오류는 대상 식별자를 직접 입력하는 일반 신고 흐름으로 fallback한다.
- 5xx·네트워크·분류되지 않은 오류는 제보로 전환하지 않고 `StatePanel`의 오류·재시도 상태로 표시하며, 서버가 제공한 `traceId`를 함께 보여준다.
- `ParticipationRequestScreen`은 검증 성공한 컨텍스트에서만 대상 요약을 읽기 전용으로 표시하고, 그 외 신고는 기존 식별자 입력을 유지한다. 신규 화면의 종류 탭 제거는 유지하되 URL로 직접 진입한 신고의 `report` 동작은 보존한다.
- 재리뷰에서 확인된 누락을 보완해 `initialLoadError`가 있으면 신고 입력을 렌더링하지 않고, traceId와 재시도 링크를 포함한 `StatePanel` 오류 상태를 표시한다.
- `frontend/package.json`의 테스트 목록에 새 정규화 회귀 테스트를 포함했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm test` | 통과 | 프런트 전체 305개 테스트와 새 `participation-entry` 테스트 |
| `npm run typecheck` | 통과 | Next.js·TypeScript 타입 검사 |
| `npm run build` | 통과 | 테스트, 타입 검사, Next.js 프로덕션 빌드 및 `/me/requests/new` 라우트 생성 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 신고 진입 쿼리 해석을 순수 함수로 고정하고 맛집 상세 신고만 컨텍스트 조회 대상으로 판별하는 회귀 테스트를 추가했다.
- 서버 검증 여부를 `initialTargetVerified`로 분리해 향후 표시용 query parameter를 신뢰 경계로 사용할 수 없게 했다.
- 운영 지표는 현재 없으므로 배포 후 잘못된 신고 대상 표시나 제보 API 오접수 수치를 추정하지 않는다. 필요 시 신고 접수 로그에서 `kind`, `targetType`, 실제 API 경로의 불일치를 별도 집계한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 확인 | 비교 결과 |
|---|---|---|---|---|
| 비맛집 신고 URL의 신고 흐름 보존 | 운영 수치 없음 | 대상 유형별 정규화 회귀 테스트 | 코드·테스트 기준 보존 확인 | 운영 수치는 해당 없음 |
| 상세 조회 장애의 제보 fallback 오분류 | 운영 수치 없음 | 예외 분류 코드와 오류 상태 렌더링 검증 | 5xx·네트워크 오류를 오류 상태로 분리 | 운영 수치는 해당 없음 |

## 10. 남은 사항

- PR 브랜치의 최신 커밋에서 `initialLoadError` 오류 상태 렌더링까지 반영하고, 전체 테스트·타입 검사·프로덕션 빌드를 다시 통과했다. 재리뷰 스레드에도 반영 내용과 검증 결과를 답글로 연결한다.
