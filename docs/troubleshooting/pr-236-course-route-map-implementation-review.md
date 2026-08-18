---
related_documents:
  - ../08-planning/issue-231-course-route-map.md
  - ../05-specs/api/discovery/restaurant-course-recommendation-api.md
  - ../01-requirements/business-rules.md
  - pr-232-course-route-map-contract-review.md
---

# PR #236 리뷰 트러블슈팅: 코스 지도 구현의 형상 Schema·만료 선택·좌표 검증 결함

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#236 [E3] 맛집 추천 코스 지도 표시를 구현한다](https://github.com/team-youngkk/masit-on/pull/236) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-18 |
| 범위 | 미해결 인라인 리뷰 3건(`w00lam` 2건, `jinyp01` 1건) |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #232 코스 경로 형상·실패 계약 정합화](pr-232-course-route-map-contract-review.md)를 확인했다. PR #232는 `summary=false` 전환과 구간 실패·형상 누락 분리를 문서 단계에서 정합화한 기록이라 이번 코드 단계의 세 결함과는 원인이 다르지만, "형상 없음은 오류가 아니다"라는 계약을 정확히 어디까지 적용해야 하는지의 경계가 다시 문제였다는 점에서 같은 계열이다. 별도 문서로 남기고 서로 링크한다.

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [`CourseScreen.tsx:627`](https://github.com/team-youngkk/masit-on/pull/236#discussion_r3802680327) | 만료된 결과에서도 텍스트 목록 클릭으로 선택이 다시 설정됨 | 애플리케이션 | 수정 필요 | 목록 버튼에 `disabled={expired}` 추가, 클릭 핸들러도 `expired`일 때 no-op으로 가드 | `frontend/lib/course/course-route-api.test.ts`·`npm run build`로 회귀 없음 확인. 컴포넌트 자체는 이 저장소에 렌더링 테스트 인프라가 없어(§8) 코드 검토로 확인 |
| [`KakaoMobilityCourseRouteAdapter.java:195`](https://github.com/team-youngkk/masit-on/pull/236#discussion_r3802680353) | `roads`가 배열이 아닌 타입이어도 형상 없음으로 조용히 처리됨 | 애플리케이션 | 수정 필요 | `roads.isMissingNode()`/`isNull()`만 형상 없음으로 처리하고, 존재하지만 배열이 아니면 `SCHEMA`로 분류하도록 분기 추가 | `KakaoMobilityCourseRouteAdapterWireMockIntegrationTest`에 `roads`가 object인 새 케이스 추가, 전체 스위트 통과 |
| [`course-route-api.ts:63`](https://github.com/team-youngkk/masit-on/pull/236#discussion_r3802773305) | `path` 배열 원소·좌표 범위·`shapeStatus`/`path` 불변식을 검증하지 않음 | 애플리케이션 | 수정 필요 | `isValidCourseCoordinate`에 유한성·WGS84 범위 검사를 추가하고 `segments[].path`의 모든 원소에 적용, `shapeStatus === MISSING`이면 `path`가 비어 있어야 하는 불변식도 검증 | `course-route-api.test.ts`에 5개 케이스 추가, 13→17개 전체 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 세 건 모두 잘못된 입력·상태에서 조용히 잘못된 결과를 만드는 결함이다.
- 발생 환경: `feature/ws-16-course-route-map`, PR #236 최초 커밋(`368c3c6`/`3820156`).
- 재현 조건:
  1. 코스 결과가 만료된 뒤 사용자가 텍스트 순서 목록의 항목을 클릭한다.
  2. Kakao Mobility 응답의 특정 구간에서 `roads` 필드가 배열이 아닌 타입(object·문자열 등)으로 온다.
  3. 코스 API 응답의 `segments[].path`에 `null`이나 좌표 범위를 벗어난 값이 섞이거나, `shapeStatus`가 `MISSING`인데 `path`가 비어 있지 않다.
- 실제 결과:
  1. `selectedRestaurantId`가 다시 설정돼 만료 화면에서도 마커·목록 강조가 살아난다.
  2. 제공자 응답 Schema 위반이 `shapeStatus: MISSING`인 정상 성공 코스로 둔갑해 위험을 감춘다.
  3. 잘못된 좌표가 `CourseRouteMap`의 `point.latitude`/`longitude` 연산에 그대로 전달돼 지도 effect 전체가 오류 상태로 끝나고, 형상이 있는 다른 구간까지 표시되지 않는다.
- 기대 결과: 만료 시 선택 조작 완전 차단, 배열이 아닌 `roads`는 `SCHEMA` 실패, 계약을 벗어난 `path` 원소·상태 불일치는 클라이언트가 거부하고 안전한 오류로 처리.
- 영향 범위: `/course` 결과 화면 사용자 경험(만료 후 조작 가능성), 백엔드 API가 제공자 Schema 변경·데이터 손상을 감지하는 능력, 프론트엔드가 서버 계약 위반에 견고한지 여부. 운영 데이터 변경은 없다.

## 4. 근본 원인

세 건 모두 "형상 없음(MISSING)은 정상"이라는 새 계약과 "그 밖의 경계 조건은 여전히 엄격해야 한다"는 기존 원칙 사이의 경계를 코드가 정확히 긋지 못한 것이 공통 원인이다.

1. `CourseScreen.tsx`는 만료 시 거리·시간·전체 합계 표시는 숨겼지만, 그 상태를 만드는 상호작용 자체(목록 클릭)를 막지 않았다. `CourseRouteMap`은 만료 시 지도 위에 잠금 오버레이를 그려 조작을 막았지만, 텍스트 목록은 지도와 별도 컴포넌트라 같은 잠금이 적용되지 않았다 — 두 컴포넌트가 같은 `expired` prop을 받으면서도 그 의미(선택 자체를 막는다)를 한쪽에서만 구현했다.
2. `toPath`의 `!roads.isArray()` 검사는 "필드가 아예 없음(`MissingNode`)"과 "필드는 있지만 타입이 틀림"을 구분하지 않았다. `MissingNode.isArray()`도 `false`이므로 두 경우가 우연히 같은 코드 경로로 합쳐졌다.
3. `course-route-api.ts`의 클라이언트 계약 검증은 배열인지 아닌지, 최상위 필드 타입만 확인하고 배열 원소의 형태나 필드 간 불변식은 검사하지 않았다. 백엔드가 계약대로 응답한다고 가정한 것인데, 이 검증기의 목적 자체가 "백엔드가 계약을 어겼을 때도 프론트가 죽지 않게 한다"는 방어선이므로 원소 검증 부재가 방어선의 구멍이었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `CourseScreen.tsx`의 `CourseResult` 함수와 만료 처리 `useEffect` 확인 | 목록 버튼 `onClick`이 `expired`를 참조하지 않음을 확인 | `disabled` 속성과 핸들러 가드 추가로 확정 |
| `CourseRouteMap.tsx`의 `expiredOverlay` 확인 | 지도만 잠그고 텍스트 목록에는 적용되지 않는 구조임을 확인 | 두 컴포넌트가 각자 `expired`를 받는 현재 구조를 유지하되, 목록 쪽 처리를 보완하는 것으로 충분하다고 판단(리팩터링 범위 확대 안 함) |
| `KakaoMobilityCourseRouteAdapter.toPath`의 `JsonNode.path("roads")` 반환값을 Jackson 문서로 확인 | 필드 없음은 `MissingNode`, 있지만 타입이 다르면 해당 타입의 `JsonNode`(둘 다 `isArray()=false`)를 반환 | `isMissingNode()`/`isNull()`로 "진짜 없음"만 형상 없음으로 좁히고 나머지는 `SCHEMA`로 분리 |
| 기존 WireMock 테스트에 `roads`를 object로 주는 케이스가 있는지 검색 | 없음(빈 배열·누락·홀수 길이·범위 초과만 존재) | 새 회귀 테스트 추가 |
| `course-route-api.ts`의 `isValidCourseSegmentItem`/`isValidCourseCoordinate` 원문 확인 | `path`는 배열 여부만, 좌표는 `typeof === 'number'`만 확인 | 좌표 유한성·범위 검사를 공유 헬퍼에 추가하고 `path.every(...)`로 원소까지 적용, `MISSING`/`path.length` 불변식 추가 |

## 6. 최종 해결

- 변경 내용:
  - `frontend/app/course/CourseScreen.tsx`: 목록 항목 버튼에 `disabled={expired}`를 추가하고 `onClick` 핸들러를 `expired`일 때 `onSelectRestaurant`를 호출하지 않도록 가드했다. `frontend/app/course/course.module.css`에 `.resultItemButton:disabled` 스타일을 추가했다.
  - `src/main/java/com/masiton/restaurant/infrastructure/external/config/KakaoMobilityCourseRouteAdapter.java`: `toPath`가 `roads.isMissingNode()`/`isNull()`인 경우만 형상 없음(빈 목록)으로 처리하고, 그 밖에 배열이 아닌 타입이면 `CourseRouteProviderException(SCHEMA)`를 던지도록 분기를 추가했다.
  - `frontend/lib/course/course-route-api.ts`: `isValidCourseCoordinate`에 `Number.isFinite`와 WGS84 범위(위도 -90~90, 경도 -180~180) 검사를 추가했다. `isValidCourseSegmentItem`이 `segment.path.every(isValidCourseCoordinate)`로 배열 원소까지 검증하고, `shapeStatus === 'MISSING'`이면 `path.length === 0`이어야 한다는 불변식도 함께 확인하도록 재작성했다.
- 선택 이유: 세 건 모두 기존 계약(BR-COURSE-005, API 문서의 `shapeStatus`/`path` 정의)을 바꾸지 않고, 그 계약이 이미 요구하는 경계를 코드가 놓친 지점만 좁혀서 고쳤다. 새 상태값·새 필드·API 계약 변경은 도입하지 않았다.
- 변경 파일:
  - `frontend/app/course/CourseScreen.tsx`
  - `frontend/app/course/course.module.css`
  - `src/main/java/com/masiton/restaurant/infrastructure/external/config/KakaoMobilityCourseRouteAdapter.java`
  - `src/test/java/com/masiton/restaurant/infrastructure/external/config/KakaoMobilityCourseRouteAdapterWireMockIntegrationTest.java`
  - `frontend/lib/course/course-route-api.ts`
  - `frontend/lib/course/course-route-api.test.ts`
- 고려한 대안:
  - 만료 잠금을 `CourseRouteMap`과 텍스트 목록이 공유하는 상위 컴포넌트로 옮기는 리팩터링도 가능했지만, 두 컴포넌트에 각각 `disabled`/오버레이를 적용하는 최소 변경으로 같은 결과를 얻을 수 있어 범위를 넓히지 않았다.
  - `roads` 타입 오류를 `SCHEMA` 대신 `PARTIAL`로 분류하는 방안도 검토했으나, `PARTIAL`은 "일부 구간 거리·시간 계산 실패"를 뜻하는 기존 의미(BR-COURSE-004)와 겹쳐 형상 전용 Schema 위반과 혼동을 만들므로 기존 `SCHEMA` 분류를 그대로 썼다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `node --test lib/course/course-route-api.test.ts` | 통과 | 13→17개 테스트, 신규 5건(만료 무관 항목 제외, path 원소 검증 4건 + 기존 유지) 포함 전체 통과 |
| `npm run build`(frontend) | 통과 | 263개 테스트, `tsc --noEmit`, `next build` 모두 통과, `/course` 라우트 정상 생성 |
| `./gradlew test --tests KakaoMobilityCourseRouteAdapterWireMockIntegrationTest` | 통과 | 신규 "roads가 배열이 아닌 타입" 케이스 포함 전체 통과 |
| `./gradlew clean build`(전체) | 통과 | Testcontainers 기반 WireMock 통합 테스트 포함 전체 회귀 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 세 결함 모두에 회귀 테스트를 추가했다(WireMock `roads` 타입 오류 케이스, 프론트엔드 `path` 원소·불변식 케이스 5건). `CourseScreen.tsx`의 만료-선택 가드는 이 저장소에 컴포넌트 렌더링 테스트 인프라(jsdom/React Testing Library 등)가 없어 자동화 회귀 테스트로 고정하지 못했다 — 코드 검토와 수동 확인으로만 검증했다.
- 다음 확인: `/course`·`/map` 등 프론트엔드 상호작용 컴포넌트에 자동화 렌더링 테스트가 필요한지는 이번 PR 범위를 넘는 별도 판단이 필요하다. 담당자·시점 미정.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 해당 없음 — 런타임 지표가 아니라 입력 검증·상태 가드 결함 수정 | 해당 없음 | 리뷰 전후 테스트 통과 대조 | 해당 없음 | 정량 비교 대상 아님 | inan0226, PR #236 리뷰 시점 |

## 10. 남은 사항

- 없음. 이번 라운드의 미해결 스레드 3건을 모두 수정·검증·답글·해결 처리했다.
