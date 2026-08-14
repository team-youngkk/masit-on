---
related_documents:
  - README.md
  - ../05-specs/api/discovery/map-discovery-api.md
  - ../07-adr/integration/map-001-map-bounds-search.md
  - pr-122-map-viewport-independent-query-review.md
  - pr-176-natural-language-review.md
---

# PR #212 리뷰 트러블슈팅: 지도 429 query별 대기와 타이머 상한

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#212 지도 429 재조회 대기 경계를 보완한다](https://github.com/team-youngkk/masit-on/pull/212) |
| 작성자 | 김인안 (`@jinyp01`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 미해결 P2 인라인 리뷰 2건의 재현과 수정 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #122](pr-122-map-viewport-independent-query-review.md)의 공유 Query Key와 순수 함수 테스트 방식을 재사용하고, [PR #176](pr-176-natural-language-review.md)의 응답 시각 기준 Retry-After 계산을 유지했다. 두 기록과 충돌하는 항목은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [필터 변경 시 이전 query의 rate limit 상태 초기화](https://github.com/team-youngkk/masit-on/pull/212#discussion_r3783005597) | A의 429 대기가 B 조회를 막지 않고 A 복귀 시에는 남은 대기를 유지 | 애플리케이션 | 수정 필요 | 단일 시각 state를 Query Key별 state로 교체 | A→B는 미차단, B→A는 기존 대기 유지, key별 만료 제거 테스트 통과 |
| [과도한 숫자형 Retry-After fallback](https://github.com/team-youngkk/masit-on/pull/212#discussion_r3783030013) | 곱셈 overflow와 브라우저 타이머 상한 초과를 fallback 처리 | 애플리케이션 | 수정 필요 | ms 변환 결과의 유한성과 `2_147_483_647ms` 상한을 검증 | 상한값·상한 초과·`1e308` 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 429 이후 조회가 잘못 차단되거나 타이머가 즉시 실행되는 상태 오류다.
- 발생 환경: Next.js 16.2.11, React 19.2.0, TanStack Query 5.101.4의 `/map` 클라이언트 화면
- 재현 조건: 필터 A가 429인 상태에서 필터 B로 전환하거나, `Retry-After: 2147483.648` 또는 `1e308`을 수신한다.
- 실제 결과: A의 대기 시각이 B에도 적용됐다. 과도한 숫자 헤더는 브라우저 타이머 상한을 넘거나 ms 변환에서 `Infinity`가 됐다.
- 기대 결과: 429 대기는 해당 Query Key에만 적용되고, 지원 가능한 타이머 범위를 벗어난 헤더는 응답 시각 기준 1초 fallback을 사용해야 한다.
- 영향 범위: 지도 필터 변경 후 조회 가능 여부, 지도·자연어 검색의 429 복구 타이머

## 4. 근본 원인

첫 번째 문제는 서버 상태가 Query Key별로 구분되는데도 `MapScreen`이 429 대기를 `number | null` 단일 지역 state로 보관한 것이 원인이다. Query Key가 바뀌어도 이 값의 소유 대상을 식별할 정보가 없어 이전 필터의 대기가 새 필터에 전파됐다.

두 번째 문제는 헤더 문자열을 `number`로 바꾼 직후의 유한성만 확인하고, `seconds * 1000` 결과와 브라우저 `setTimeout`의 32-bit 지연 상한을 검증하지 않은 것이 원인이다. 따라서 숫자 형태를 유지한 손상값은 기존 fallback 조건을 우회했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API-MAP-001 5절과 `MapScreen` state 비교 | 필터 변경은 새 조회를 허용해야 하나 단일 대기 시각이 모든 key를 차단 | Query Key별 state 채택 |
| PR #122의 `buildMapPointsQueryKey` 재사용 가능성 확인 | 서버 prefetch와 클라이언트가 이미 같은 key를 사용 | 별도 식별 규칙 없이 JSON 직렬화한 공유 key 사용 |
| 신규 target 테스트를 수정 전 실행 | 12건 중 4건 실패 | 리뷰 조건 재현 확인 후 구현 진행 |
| `setTimeout` 최대 지연 경계 확인 | `2_147_483_647ms`를 넘으면 안전한 단일 타이머로 표현할 수 없음 | 초→ms 변환 후 유한성·상한 검사 추가 |

## 6. 최종 해결

- 변경 내용: 지도 429 대기를 Query Key별 record로 관리하고 현재 key의 대기만 `enabled`에 반영했다. 만료 시에도 해당 key만 제거한다. Retry-After는 ms 변환 결과가 유한하고 `2_147_483_647ms` 이하일 때만 사용하며 나머지는 1초 fallback으로 처리한다.
- 선택 이유: API·ADR·Query Key 계약을 바꾸지 않고 두 실패 조건을 가장 가까운 순수 상태·파싱 함수에서 차단할 수 있다.
- 변경 파일: `frontend/components/map/MapScreen.tsx`, `frontend/lib/map/rate-limit-state.ts`, `frontend/lib/map/rate-limit-state.test.ts`, `frontend/lib/map/retry-after.ts`, `frontend/lib/map/retry-after.test.ts`, `docs/troubleshooting/README.md`, `docs/troubleshooting/pr-212-map-rate-limit-review.md`
- 고려한 대안: 필터 변경 때 모든 대기를 지우는 방식은 A로 복귀했을 때 같은 Query Key의 429 대기까지 잃으므로 제외했다. 긴 지연을 여러 타이머로 분할하는 방식은 서버의 초당 제한 계약에 비해 과도하며 손상 헤더를 신뢰하게 되므로 제외했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `node --test lib/map/rate-limit-state.test.ts lib/map/retry-after.test.ts` | 통과 | 12건 통과, Query Key 전환·복귀·key별 만료와 타이머 상한·overflow 검증 |
| `npm.cmd run typecheck` | 통과 | TypeScript 오류 0건 |
| `npm.cmd test` | 통과 | 자연어 검색 pretest 10건과 프론트 전체 217건 통과 |
| `npm.cmd run build` | 통과 | 전체 테스트·타입 검사와 Next.js production build 성공 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Query Key별 상태 전이 3건과 브라우저 타이머 상한·overflow 2건을 순수 함수 회귀 테스트로 추가했다.
- 다음 확인: 실제 브라우저 SSR 429 Network 요청 0건 수동 검증은 PR 본문의 기존 미검증 항목으로 유지한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 신규 회귀 테스트 5건 | 기존 구현에서 4건 실패, 1건 통과 | target `node --test`를 리뷰 반영 전후 각 1회 실행 | 수정 후 5건 모두 통과 | 확인된 두 P2 재현 조건 해소 | 김인안 / PR #212 리뷰 반영 시점 |
| 운영 오류율·대기 시간 | 해당 없음. 배포 전 정확성 수정이며 운영 계측 변경이 아님 | 해당 없음 | 해당 없음 | 비교 불가 | 해당 없음 |

## 10. 남은 사항

- 코드 수정 관련 미해결 사항 없음. 리뷰 스레드 해결 상태는 GitHub 원문에서 추적한다.
