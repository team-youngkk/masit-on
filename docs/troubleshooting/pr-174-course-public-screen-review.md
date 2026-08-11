---
related_documents:
  - ../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../04-product/wireframes/third-expansion-wireframes.md
  - ../05-specs/api/discovery/restaurant-course-recommendation-api.md
  - ../05-specs/api/common/error-contract.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../../frontend/app/course/CourseScreen.tsx
  - ../../src/main/java/com/masiton/restaurant/application/course/RestaurantCourseRecommendationService.java
---

# PR #174 리뷰 트러블슈팅: 코스 공개 진입점·실패 식별·검색 상태 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#174 코스 실패 경계·공개 화면·60건 Fixture 평가](https://github.com/team-youngkk/masit-on/pull/174) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-11 |
| 범위 | 공개 코스 진입점, 422/502 실패 상세, 검색 페이지 추가 조회, 안내 문구, 요청 취소 경계 |
| 주 문제 유형 | 애플리케이션 / API 계약 / UI 상태 / 접근성 |
| 기존 기록 | [PR #171 코스 경로 연동·quota 경계 리뷰](pr-171-course-route-review.md) |

## 2. 리뷰 요청 처리 결과

| 리뷰 요청 | 문제 요약 | 판단 | 처리 결과 | 근거 |
|---|---|---|---|---|
| 공개 코스 화면 진입점 | `/course`를 직접 입력해야 하고 공개 헤더에 링크가 없음 | 수정 필요 | `SiteHeader`에 코스 링크를 추가하고 링크 계약 테스트를 작성 | `frontend/components/layout/SiteHeader.tsx`, `frontend/lib/course/course-navigation.test.ts` |
| 실패 맛집 식별 | 502 응답의 `details.selectedRestaurants`를 프론트 상태가 버림 | 수정 필요 | 실패 화면에서 입력 순서와 이름을 표시하고 상태 회귀 테스트를 추가 | `frontend/lib/course/course-screen-state.ts`, `frontend/lib/course/course-screen-state.test.ts` |
| 422 실패 맛집 식별 | 비공개·좌표 오류가 `details` 없이 반환됨 | 수정 필요 | 문제가 된 맛집의 ID·이름·입력 순서를 `details.selectedRestaurants`로 반환 | `RestaurantCourseSelectionDetails`, 코스 서비스/API 테스트 |
| 최대 선택 안내 중복 | 5개 선택 시 계산 버튼에도 최대 선택 안내가 연결됨 | 수정 필요 | 계산 버튼은 최소 개수 안내만 `aria-describedby`로 연결하고 최대 안내는 검색 패널에 유지 | `frontend/app/course/CourseScreen.tsx` |
| 검색 결과 20개 제한 | 첫 페이지만 조회해 21번째 이후 후보를 선택할 수 없음 | 수정 필요 | `page.hasNext`를 보존하고 `더 보기`로 다음 페이지를 이어 붙임 | `frontend/lib/course/course-search-api.ts`, `CourseScreen` |
| AbortSignal 미연결 | API의 `signal` 인자가 실제 호출되지 않아 취소 분기가 죽어 있음 | 수정 필요 | 검색·경로 요청에 `AbortController`를 연결하고 컴포넌트 종료·선택 변경 시 취소 | `CourseScreen`, `course-route-api.test.ts` |
| 승인 리뷰의 비차단 제안 | API 목록/422 식별 정보와 AbortSignal 연결성에 대한 후속 제안 | 반영 | 422 식별 정보와 실제 요청 취소를 이번 PR에 반영 | 본 문서의 변경·검증 항목 |

## 3. 문제 현상과 발생 조건

- `/course` 라우트와 화면은 존재하지만 `SiteHeader`의 공개 메뉴에서 접근할 수 없었다.
- 경로 계산 502 응답은 서버가 `selectedRestaurants`를 내려도 프론트 타입과 분류 결과에 전달되지 않았다.
- 비공개·좌표 오류는 서버가 첫 번째 문제 맛집을 알고도 클라이언트에는 코드와 메시지만 내려 문제 대상을 확인할 수 없었다.
- 검색 API가 `page=1&size=20`만 요청하고 `page.hasNext`를 무시해 20건을 넘는 후보가 화면에 도달하지 않았다.
- 화면에서 `signal`을 전달하지 않아 새 검색, 선택 변경, 화면 이탈 뒤 이전 요청을 취소할 수 없었다.

## 4. 근본 원인

프론트 코스 화면과 API 연동을 최초 성공·실패 응답 중심으로 구현하면서, 공개 내비게이션과 페이지 메타데이터를 화면 계약에 포함하지 않았다. 또한 백엔드의 실패 상세 DTO가 502 경로에만 연결되어 422 입력 검증 경계에는 같은 식별 정보가 없었고, 요청 취소용 API 인자만 선언된 채 호출부 수명주기와 연결되지 않았다.

## 5. 확인 및 검증

| 검증 항목 | 결과 | 확인 내용 |
|---|---|---|
| `npm.cmd test` | 통과 | 프론트 173건, 0 실패. 코스 상태·검색 페이지·헤더 진입점·요청 signal 회귀 포함 |
| `npm.cmd run typecheck` | 통과 | `CourseScreen`, 검색 결과 page 메타데이터, 오류 상세 타입 확인 |
| `./gradlew.bat test --tests ...RestaurantCourseRecommendationServiceTest --tests ...RestaurantCourseRouteApiTest --tests ...CourseRouteFailureIsolationApiTest --no-daemon` | 통과 | 백엔드 대상 테스트와 422 상세·502 입력 순서·기존 공개 API 격리 확인 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 6. 최종 해결

- 백엔드 422 오류에 문제 맛집의 안전한 식별 정보만 담는 `RestaurantCourseSelectionDetails`를 추가했다. 좌표·내부 상태·외부 응답은 포함하지 않는다.
- 프론트 `CourseErrorBody`와 `CourseRouteOutcome`이 `selectedRestaurants`를 보존하고, 오류 화면에서 입력 순서 목록을 표시한다.
- 검색 응답의 `page.hasNext`를 읽어 다음 페이지를 추가하고, 검색·경로 요청을 `AbortController`로 취소한다.
- 계산 버튼의 설명 연결은 `BELOW_MINIMUM`에만 적용해 최대 선택 안내 중복을 제거했다.
- 공개 헤더에 `/course` 링크를 추가하고, API 계약 문서에 422 오류 상세 예시를 기록했다.

## 7. 예방 조치 및 다음 확인

- 오류 화면 변경 시 HTTP 상태별 `details` 전달 여부와 프론트 분류 결과를 함께 테스트한다.
- 목록 API를 화면에서 사용할 때 페이지 메타데이터를 버리지 않고 추가 조회 또는 명시적 상한 안내를 제공한다.
- `AbortSignal`을 선언한 API는 호출부 테스트에서 실제 `RequestInit.signal` 전달을 검증한다.
- 브라우저별 반응형·키보드 인수 검증은 기존 E3-T12 범위에서 계속 수행한다.

## 8. 미해결 항목

- 없음. 실제 Kakao Mobility 운영 quota와 브라우저 인수 검증은 이번 리뷰 수정과 별도의 운영·E3-T12 검증 범위다.
