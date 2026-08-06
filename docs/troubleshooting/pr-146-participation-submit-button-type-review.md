---
related_documents:
  - README.md
  - ../04-product/prd/participation/user-submission-report.md
  - pr-134-participation-request-review.md
---

# PR #146 리뷰 트러블슈팅: 제보·신고 접수 버튼 type 수정 PR 본문 정정

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#146 제보·신고 접수 버튼이 폼을 제출하지 않는 문제를 고친다](https://github.com/team-youngkk/masit-on/pull/146) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-06 |
| 범위 | PR 본문의 grep 결론 서술 오류, 테스트 근거와 미검증 항목 구분 |
| 주 문제 유형 | 기타 (코드 변경 없음, PR 본문 서술 정확성) |
| 기존 기록 | [pr-134-participation-request-review.md](pr-134-participation-request-review.md)에서 같은 화면(`ParticipationRequestScreen`)의 이전 리뷰 기록을 확인했으나, 이번 두 스레드와 같은 본문 서술·테스트 커버리지 문제는 없어 재사용하지 않았다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [grep 결론 정정](https://github.com/team-youngkk/masit-on/pull/146#discussion_r3725259218) | "다른 폼은 onClick이라 네이티브 제출에 의존하지 않는다"는 서술을 사실로 정정 | 기타 | 수정 필요 | PR 본문 "변경 목적" 마지막 문단을 "onSubmit 폼 11곳 전부 type=\"submit\" 명시, 이 파일이 유일한 누락"으로 교체 | `git grep onSubmit`으로 11곳 확인, 각 파일에서 `type="submit"` 명시 확인, `Button.tsx:15` 기본값 `type="button"` 확인 |
| [테스트 근거 분리](https://github.com/team-youngkk/masit-on/pull/146#discussion_r3725259223) | `PASS npm test 121건`을 결함 검증 근거에서 분리하고 자동 회귀 가드 부재를 명시 | 기타 | 수정 필요 | "테스트 결과"에 회귀 없음만 증명한다는 문구 추가, "검증하지 못한 항목"에 렌더링 테스트 부재 명시 | `frontend/package.json`의 `test` 스크립트가 `node --test`로 로직 모듈 22개만 실행, `jsdom`·testing-library 부재, `ParticipationRequestScreen` 렌더 테스트 없음 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 코드 결함이 아니라 PR 본문 서술과 실제 코드베이스 상태의 불일치.
- 발생 환경: PR #146, `fix/participation-submit-button-type` 브랜치, Next.js 16.2.11·TypeScript 7.0.2.
- 재현 조건: PR 본문의 두 문단(변경 목적 3문단, 테스트 결과·검증하지 못한 항목)을 실제 코드·설정과 대조.
- 실제 결과: (1) 다른 11개 `onSubmit` 폼이 전부 `type="submit"`을 명시함에도 본문은 "onClick이라 의존하지 않는다"고 반대로 서술. (2) `npm test`는 렌더링 테스트 하네스가 없어 이 결함을 검증하지 못하는데, 본문은 결함 수정 근거와 나란히 배치.
- 기대 결과: 본문이 실제 불변식(공용 `Button` 기본값이 `type="button"`이라 모든 제출 버튼이 이를 명시로 덮어써야 함)과 실제 테스트 커버리지 한계를 정확히 기록.
- 영향 범위: 문서 정확성. 다른 폼의 `type="submit"`을 후속 작업자가 불필요한 중복으로 오판해 제거하면 같은 결함이 재발할 수 있고, 테스트 근거를 그대로 신뢰하면 회귀를 CI가 잡지 못한다는 사실을 놓칠 수 있다.

## 4. 근본 원인

PR 작성 시 grep 결과를 "onClick 핸들러를 가진 폼은 네이티브 제출에 의존하지 않는다"는 관찰까지만 확인하고, 그 폼들이 `onSubmit`을 쓰는 폼과 겹치지 않는 별개 집합인지 검증하지 않아 결론이 뒤집혔다. 실제로는 `onSubmit`을 쓰는 폼 11곳 전부가 `type="submit"`을 명시하고, `onClick` 버튼은 폼 밖이거나 폼 안의 보조 버튼(`type="button"` 명시)이었다. 테스트 근거는 `npm test`가 통과했다는 사실만으로 "회귀 없음"과 "결함 검증"을 구분하지 않고 나열해, 실제로는 로직 모듈만 실행하는 하네스의 범위를 넘어선 것처럼 읽혔다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `git grep -n "onSubmit" frontend/**/*.tsx` (PR 브랜치) | `ParticipationRequestScreen.tsx` 포함 12곳, 나머지 11곳 | 11곳 각각의 제출 버튼 `type` 속성 확인으로 이어감 |
| 11곳 각 파일에서 `type="submit"`·`<Button` 검색 | 전부 명시적으로 `type="submit"` | 리뷰어 지적이 사실과 일치, 본문 문단 교체 |
| `frontend/components/ui/Button.tsx` 확인 | `type = 'button'` 기본값 | "명시가 필수"라는 결론의 근거로 사용 |
| `frontend/package.json`의 `test` 스크립트와 devDependencies 확인 | `node --test`로 명시된 22개 로직 테스트만 실행, `jsdom`·testing-library 없음 | 렌더링 테스트 부재를 "검증하지 못한 항목"에 명시 |
| `git grep -l "ParticipationRequestScreen"` | 화면·라우트·API 클라이언트 파일만 매칭, 테스트 파일 없음 | 이 화면에 렌더 테스트가 없다는 근거로 확정 |

## 6. 최종 해결

- 변경 내용: PR #146 본문 "변경 목적" 마지막 문단을 그렙 결과와 일치하도록 교체했다.
- 변경 내용: "테스트 결과"의 `npm test` 항목에 회귀 없음만 증명한다는 문구를 추가하고, "검증하지 못한 항목"에 렌더링 테스트 부재와 범위 밖 사유를 추가했다.
- 선택 이유: 코드·API·DB 변경이 필요 없는 P3 문서 지적이라 본문만 정정하는 것이 요청 범위와 일치한다.
- 변경 파일: 없음(코드 변경 없음). PR #146 본문(GitHub PR description)만 수정.
- 고려한 대안: 없음. 리뷰어 제안 문구가 검증 결과와 정확히 일치해 그대로 채택했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git grep -n "onSubmit" -- 'frontend/**/*.tsx'` (PR 브랜치, 12곳) | 통과 | `onSubmit` 폼 수가 본문 수정 문구의 "11곳"(자기 자신 제외)과 일치 |
| 11개 파일 각각의 제출 버튼 `type` 속성 수동 대조 | 통과 | 전부 `type="submit"` 명시 확인 |
| `frontend/package.json` scripts.test·devDependencies 확인 | 통과 | `node --test` 로직 모듈 22개, 렌더링 하네스 없음 확인 |
| `gh pr edit 146 --body-file` | 통과 | PR 본문에 두 정정 사항 반영, GitHub에서 확인 가능 |

이 PR은 코드 변경이 없는 문서 정정이므로 `tsc --noEmit`·`npm test` 등 코드 검증은 재실행하지 않았다(원 PR의 코드 변경분은 이번 리뷰 대상이 아니었고 그대로 유지).

## 8. 재발 방지 및 다음 확인

- 재발 방지: 없음. 이번 정정은 PR 본문 서술 수정이며 코드·테스트 변경이 아니라 추가할 회귀 테스트가 없다.
- 다음 확인: 없음. 두 스레드 모두 본문 수정과 리뷰어 확인 가능한 diff(PR 본문)로 처리를 마쳤다.

## 9. 도입 전후 비교 지표

해당 없음. 코드·성능·오류율에 영향이 없는 PR 본문 서술 정정이라 비교할 정량 지표가 없다.

## 10. 남은 사항

없음. 두 스레드 모두 답글을 달고 해결(resolve) 처리했다.
