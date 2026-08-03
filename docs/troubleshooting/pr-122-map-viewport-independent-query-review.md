---
related_documents:
  - README.md
  - ../07-adr/integration/map-001-map-bounds-search.md
  - ../05-specs/api/discovery/map-discovery-api.md
  - ../08-planning/expansion-1-task-breakdown.md
  - ../08-planning/second-expansion-baseline-review.md
  - ../06-architecture/technology-policy.md
---

# PR #122 리뷰 트러블슈팅: 지도 뷰포트 비종속 조회 문서·테스트 반영

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#122 지도 뷰포트 비종속 조회로 전환한다](https://github.com/team-youngkk/masit-on/pull/122) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-03 |
| 범위 | 이슈 #118(E1-T11) 구현에 달린 리뷰 스레드 3건 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [지도 이동 비재조회 회귀 자동화](https://github.com/team-youngkk/masit-on/pull/122#discussion_r3703319415) | Kakao SDK·fetch를 stub한 컴포넌트 테스트로 idle 전후 호출 0건·선택 유지를 고정 | 결정 필요 | 미해결 유지. 대신 `MapScreen`/`page.tsx`가 중복 정의하던 Query Key를 `buildMapPointsQueryKey` 하나로 합치고 bounds 미포함·양쪽 동일 키를 테스트로 고정 | 저장소 전체에 DOM 렌더링 테스트(jsdom/RTL 등)가 없고 관련 ADR도 이를 다루지 않음을 확인 |
| [ADR-MAP-001 109행 문구 정정](https://github.com/team-youngkk/masit-on/pull/122#discussion_r3703327503) | 같은 ADR의 "제거해야 한다" 문구를 완료 상태로 정정 | 수정 필요 | 109행을 "제거했다"로 변경 | 이번 PR이 실제로 해당 Controller·Command·Query·Query Key·테스트를 제거했음을 diff로 확인 |
| [E2 기준선 문서의 E1-T11 판정 정정](https://github.com/team-youngkk/masit-on/pull/122#discussion_r3703327604) | `second-expansion-baseline-review.md`의 판정 근거를 현재 단계에 맞춰 구분 | 수정 필요 | 판정 문구를 "구현 PR 검증 중" 상태로 정정하되 게이트 판정(미충족)은 유지 | PR이 아직 `develop`에 병합되지 않았고 브라우저 회귀·`E1-T10` 재검증이 남아 있음을 확인 |

## 3. 문제 현상

### 3.1 지도 이동 비재조회 회귀

- 재현 조건: 이 PR이 `KakaoMapView.tsx`의 Kakao `idle` 리스너와 `onBoundsChange` 파이프라인을 완전히 제거했다. 이 상태에서 향후 누군가 지도 이동 시 재조회를 다시 연결해도, 현재 테스트(`map-points-query.test.ts`)는 순수 함수 `buildMapPointsSearchParams`/`buildMapPointsQueryKey`만 검증하므로 실제 컴포넌트 동작(이벤트 발생 → fetch 호출 횟수 → 선택 상태)의 회귀를 잡지 못한다.
- 실제 결과: 이 회귀를 자동으로 잡는 테스트가 없다.
- 기대 결과(리뷰 요청): Kakao SDK와 fetch를 stub한 컴포넌트 테스트로 idle 이벤트 전후 API 호출 횟수 0건 증가와 마커·목록·선택 유지를 고정한다.
- 영향 범위: 프론트엔드 테스트 커버리지. 런타임 동작이나 API·DB 계약에는 영향이 없다.

### 3.2 ADR-MAP-001 109행 내부 모순

- 재현 조건: `docs/07-adr/integration/map-001-map-bounds-search.md`를 처음부터 읽는다.
- 실제 결과: 32행은 "이 결정에 맞춘 구현은 E1-T11에서 진행했다"고 완료를 서술하지만, 같은 문서 109행은 "기존 bounds Controller·Command·Query·프론트 Query Key·테스트를 후속 구현에서 제거해야 한다"고 아직 할 일로 서술해 같은 문서 안에서 두 문장이 동시에 참일 수 없었다.
- 기대 결과: 문서 내부에서 구현 완료 여부에 대한 서술이 일관돼야 한다.
- 영향 범위: 문서 정확성. 코드 동작에는 영향이 없다.

### 3.3 E2 기준선 문서의 판정 근거 불일치

- 재현 조건: `docs/08-planning/second-expansion-baseline-review.md`의 `E1-T11` 판정 행을 읽는다.
- 실제 결과: "현재 Controller·Command·SQL·TanStack Query queryKey와 지도 idle 재조회는 아직 bounds 계약을 사용한다"고 서술했는데, 이 PR이 이미 그 코드를 제거했으므로 전제가 사실과 달랐다.
- 기대 결과: 판정 근거가 실제 코드 상태(구현 PR 제출, 병합 대기)를 반영해야 한다. 다만 병합·브라우저 회귀·`E1-T10` 재검증 전까지 게이트 판정 자체(미충족)는 바뀌지 않는다.
- 영향 범위: 2차 확장 착수 게이트 판단 근거 문서. 실제 게이트 판정 결과는 유지된다.

## 4. 근본 원인

3.1은 근본 원인이 아니라 테스트 도구 공백이다. 저장소 전체(`frontend/**/*.test.ts`)를 확인한 결과 모든 프론트엔드 테스트는 `node:test` 기반 순수 함수·coordination 모듈 테스트이며, jsdom·React Testing Library·react-test-renderer 등 DOM 렌더링 테스트 도구가 어디에도 없다. [ADR-WEB-001](../07-adr/platform/web-001-frontend-platform.md)은 Next.js·TypeScript 런타임 버전만 고정하고, [ADR-TEST-001](../07-adr/quality/test-001-automation-strategy.md)의 적용 범위(9절)는 JUnit·Testcontainers·WireMock 기반 백엔드 테스트로 한정돼 프론트엔드 컴포넌트 렌더링 테스트를 다루지 않는다. 리뷰가 요청한 수준(컴포넌트를 실제로 마운트해 Kakao SDK 이벤트와 React Query 호출을 관찰)은 이런 도구 없이는 구현할 수 없으므로, 도구 도입 여부를 이 PR 안에서 임의로 결정할 수 없다.

3.2·3.3은 같은 근본 원인을 공유한다. 이 PR이 리뷰 시점 이후로도 코드를 계속 수정하면서, 최초 구현 커밋에서 "아직 미구현" 상태로 남겨둔 문서 문구를 코드 변경과 같은 시점에 전부 갱신하지 않았다. `map-discovery-api.md`, `map-discovery.md`, `expansion-1-task-breakdown.md`의 동일한 문구는 리뷰 전 커밋에서 이미 갱신했지만, 같은 ADR 파일의 다른 절(109행)과 별도 문서(`second-expansion-baseline-review.md`)에 있는 동일 취지의 문구는 갱신 대상에서 빠졌다.

## 5. 해결

- 변경 내용:
  - `docs/07-adr/integration/map-001-map-bounds-search.md:109` — "제거해야 한다" → "[E1-T11](../../08-planning/expansion-1-task-breakdown.md)에서 제거했다"로 정정.
  - `docs/08-planning/second-expansion-baseline-review.md`의 `E1-T11` 행 — 판정을 "미충족" → "미충족(구현 PR 검증 중)"으로 세분화하고, 근거를 "구현 PR이 제출됐으나 아직 `develop`에 병합되지 않았고 브라우저 회귀와 `E1-T10` 재검증이 남아 있다"로 정정. 게이트 판정 자체는 바꾸지 않았다.
  - `frontend/lib/map/map-points-query.ts` — `buildMapPointsQueryKey(filters)`를 추가해 `MapScreen.tsx`(client `useQuery`)와 `frontend/app/map/page.tsx`(server `prefetchQuery`)가 동일한 배열을 각자 손으로 작성하던 중복을 제거했다.
  - `frontend/components/map/MapScreen.tsx`, `frontend/app/map/page.tsx` — `buildMapPointsQueryKey`를 사용하도록 변경.
  - `frontend/lib/map/map-points-query.test.ts` — `buildMapPointsQueryKey`가 `south`/`west`/`north`/`east`를 포함하지 않고, 필터가 없어도 항상 같은 길이의 키를 반환함을 검증하는 테스트 2건 추가.
- 선택 이유: 문서 정정 2건은 코드와 문서가 같은 사실을 다르게 서술하던 내부 모순을 없애는 최소 수정이다. Query Key 공유 함수는 리뷰가 지적한 "idle 리스너가 재도입돼도 잡지 못한다"는 위험 중, 새 테스트 도구 없이도 즉시 검증 가능한 부분(Query Key가 필터에서만 파생되고 bounds가 절대 나타나지 않는다는 것)을 다뤄 회귀 위험을 줄인다.
- 변경 파일: `docs/07-adr/integration/map-001-map-bounds-search.md`, `docs/08-planning/second-expansion-baseline-review.md`, `frontend/lib/map/map-points-query.ts`, `frontend/lib/map/map-points-query.test.ts`, `frontend/components/map/MapScreen.tsx`, `frontend/app/map/page.tsx`
- 고려한 대안: `jsdom` + `@testing-library/react`(또는 `react-test-renderer`)를 도입해 리뷰가 요청한 전체 시나리오(Kakao SDK 이벤트 발생 → fetch 호출 횟수·선택 상태 관찰)를 그대로 구현하는 방법을 검토했다. 이 저장소의 프론트엔드 의존성은 [ADR-WEB-001](../07-adr/platform/web-001-frontend-platform.md)이 정확한 버전으로 고정하고, [기술 정책](../06-architecture/technology-policy.md)이 임의 추가를 금지하므로, 이 PR 범위에서 새 라이브러리를 추가하지 않고 소유자 결정으로 넘겼다. 컴포넌트 소스 문자열에서 `'idle'` 리터럴을 grep하는 방식도 검토했으나, 실제 동작을 검증하지 않고 우회하기 쉬운 취약한 검증이라 채택하지 않았다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `find frontend -iname "*.test.ts" -o -iname "*.test.tsx"` | 확인 완료 | 저장소 전체 프론트엔드 테스트 14개 파일 모두 `node:test` 기반 순수 함수 테스트이며 DOM 렌더링 테스트가 없음 |
| `grep -rn "jsdom\|testing-library\|react-test-renderer" frontend/package.json docs/07-adr` | 확인 완료 | 어떤 ADR도 프론트엔드 컴포넌트 렌더링 테스트 도구를 다루지 않음 |
| `node node_modules/typescript/bin/tsc --noEmit` (frontend) | 통과 | 타입 오류 0건 |
| `node --test <14개 테스트 파일>` | 통과 | 68건 통과(기존 66건 + `buildMapPointsQueryKey` 신규 2건), 0건 실패 |

## 7. 재발 방지

- Query Key를 두 파일에서 각자 작성하지 않고 `buildMapPointsQueryKey` 한 곳에서만 정의하도록 바꿔, 앞으로 한쪽만 수정되어 hydration이 조용히 무시되는 회귀를 원천적으로 막았다.
- 문서 갱신 시 같은 개념을 서술하는 모든 절·모든 문서를 함께 grep해 확인하는 점검을 이번 기록에 남긴다. 다음 PR에서 유사한 "구현 완료 후 문서 갱신" 작업을 할 때 이 문서의 3.2·3.3 사례를 참고한다.

## 8. 남은 사항

- 지도 이동 시 실제 API 재호출 0건·마커·선택 유지에 대한 컴포넌트 수준 자동 회귀 테스트는 DOM 렌더링 테스트 도구 도입 여부에 대한 팀 결정이 필요해 미해결로 남긴다. 도입하기로 하면 별도 ADR과 함께 WS-07 소유자(양성훈)·테스트 전략 소유자(박진영)의 합의가 필요하다.
- 브라우저에서 실제 Kakao SDK로 지도 이동을 확인하는 수동/E2E 검증과 `E1-T10` 지도 회귀는 이 PR 병합 후 별도로 실행해야 한다(PR #122 본문의 "검증하지 못한 항목" 참고).
