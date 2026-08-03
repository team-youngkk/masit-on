---
related_documents:
  - README.md
  - ../05-specs/api/common/filtering-contract.md
  - ../05-specs/api/discovery/map-discovery-api.md
  - ../07-adr/platform/web-002-data-state.md
---

# PR #99 리뷰 트러블슈팅: 반복 지도 필터 판단

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#99 지도 이동 시 맛집 탐색 조건 유지](https://github.com/team-youngkk/masit-on/pull/99) |
| 작성자 | 김인안 (`inan0226`) |
| 처리 일자 | 2026-08-03 |
| 범위 | 반복된 공개 탐색 쿼리를 지도 링크가 첫 값으로 유지하는 동작 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [반복 필터를 첫 값으로 정규화하지 말라는 요청](https://github.com/team-youngkk/masit-on/pull/99#discussion_r3701163892) | 반복 쿼리는 현재 화면에서 미적용되므로 지도 링크에서도 제거 | 수정 불필요 | 현재 동작 유지 | `toSingleValue`와 지도 링크 생성기 모두 첫 값을 사용함을 직접 실행으로 확인 |

## 3. 문제 현상

- 재현 조건: `/restaurants?creatorId=a&creatorId=b`에서 상단 지도 메뉴의 목적지를 계산한다.
- 리뷰에서 예상한 결과: 현재 맛집 화면에는 유튜버 조건이 적용되지 않지만 지도 링크는 `/map?creatorId=a`가 되어 이동 후 조건이 갑자기 활성화된다.
- 실제 결과: 맛집 화면은 `toSingleValue(['a', 'b'])`의 결과인 `a`를 사용하고 지도 링크도 같은 첫 값으로 `/map?creatorId=a`를 만든다.
- 기대 결과: 맛집 화면에 적용된 공개 탐색 조건과 지도 링크의 조건이 일치한다.
- 영향 범위: 맛집 목록과 지도 사이의 URL 검색 상태 일관성이다. API·DB·인증 동작은 변경하지 않는다.

## 4. 근본 원인

리뷰는 `toSingleValue`가 배열을 `undefined`로 처리한다고 전제했지만, 실제 구현은 배열의 첫 원소를 반환한다. `URLSearchParams.get()`도 반복 쿼리의 첫 값을 반환하므로 이번 PR의 링크 생성기는 기존 맛집·지도 페이지와 같은 의미를 유지한다.

반복값을 `400 INVALID_FIELD_VALUE`로 거부한다는 API 계약과 프론트엔드의 기존 첫 값 정규화는 서로 다르지만, 이는 PR #99에서 새로 생긴 문제가 아니다. 리뷰 제안대로 링크 생성기만 `getAll(key).length === 1`로 바꾸면 현재 맛집 화면에는 `a`가 적용된 상태에서 지도 이동 후 조건이 제거되어 오히려 화면 간 일관성이 깨진다.

## 5. 해결

- 변경 내용: 링크 생성 코드는 변경하지 않고 리뷰 판단과 재현 근거를 문서화했다.
- 선택 이유: 현재 맛집·지도 페이지의 단일 값 해석과 지도 링크의 해석을 동일하게 유지하기 위해서다.
- 변경 파일: `docs/troubleshooting/README.md`, `docs/troubleshooting/pr-99-repeated-map-filter.md`
- 고려한 대안: 링크에서만 반복값을 제거하는 방법은 화면 간 필터가 달라져 채택하지 않았다. 반복값을 API 계약대로 거부하도록 전체 프론트 경계를 바꾸는 작업은 기존 공통 동작과 오류 표현을 함께 결정해야 하므로 이번 결함 수정 범위에 포함하지 않았다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `node --input-type=module -e "..."` | 통과 | `toSingleValue(['a','b'])`와 지도 링크가 모두 첫 값 `a`를 사용 |
| `npm.cmd --prefix frontend test` | 통과 | 프론트엔드 테스트 51건 통과 |
| `npm.cmd --prefix frontend run typecheck` | 통과 | TypeScript 오류 없음 |

## 7. 재발 방지

- 리뷰 요청을 구현하기 전에 기존 `toSingleValue` 동작과 링크 생성 결과를 같은 입력으로 직접 대조했다.
- 반복 쿼리의 최종 제품 동작을 변경하려면 공통 필터 계약, 맛집 목록, 지도 초기 상태와 오류 화면을 함께 검토한다.

## 8. 남은 사항

- API 계약은 반복 필터를 `400`으로 거부하지만 현재 프론트엔드는 첫 값으로 정규화한다. PR #99의 회귀가 아니므로 이 PR에서는 동작을 확대 변경하지 않는다.
