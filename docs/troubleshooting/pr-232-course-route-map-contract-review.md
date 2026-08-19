---
related_documents:
  - ../08-planning/issue-231-course-route-map.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../05-specs/api/discovery/restaurant-course-recommendation-api.md
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - pr-171-course-route-review.md
---

# PR #232 리뷰 트러블슈팅: 코스 경로 형상·실패 계약 정합화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#232 맛집 추천 코스 지도 표시 계약 초안](https://github.com/team-youngkk/masit-on/pull/232) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-18 |
| 범위 | `summary` 응답 모드에 따른 형상 취득 조건과 구간 계산 실패·형상 누락 상태 분리 리뷰 2건 |
| 주 문제 유형 | 기타 — 문서·계약 정합성 |
| 기존 기록 | [PR #171 코스 경로 외부 연동·quota 경계](pr-171-course-route-review.md)를 확인했다. 당시 `summary=true`를 거리·시간 요약 계약으로 고정한 결과를 재사용하되, 지도 형상에는 상세 응답이 필요하다는 차이를 이번 기록에 추가한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [3801814872](https://github.com/team-youngkk/masit-on/pull/232#discussion_r3801814872) | `summary=true`를 전제로 한 1회 형상 취득 권고를 조건부로 정정 | 기타 — 문서·계약 정합성 | 수정 필요 | `summary=true`에서는 형상 취득 불가, `summary=false` 단일 호출을 Fixture·응답 크기·비용·로그 검증 뒤 선택하도록 변경 | Kakao 공식 계약, 현재 Adapter URI 대조 |
| [3801814878](https://github.com/team-youngkk/masit-on/pull/232#discussion_r3801814878) | 형상 누락과 구간 경로 실패를 기존 실패 계약에 맞게 분리 | 기타 — 문서·계약 정합성 | 수정 필요 | `D-231-03A` 구간 실패와 `D-231-03B` 형상만 누락을 분리하고 상태·API 갱신·재조회 결정에 연결 | `FR-COURSE-003`, `BR-COURSE-004`, 코스 API 오류 계약 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 결정표가 현재 구현과 확정 계약을 다르게 해석하게 만드는 문서 결함이다.
- 발생 환경: `docs/issue-231-course-route-map`, PR #232 최초 커밋 `403f481`, 현재 Adapter의 `summary=true` 호출.
- 재현 조건: `D-231-01`의 “기존 1회 응답 정규화”를 현재 요청 옵션 유지로 해석하거나, `D-231-03`의 “지도만 저하”를 일부 구간 계산 실패에도 적용한다.
- 실제 결과: `roads[].vertexes`가 없는 응답에서 형상을 만들거나 별도 호출을 추가할 수 있고, 부분 성공 거리·시간을 완전한 코스로 노출할 수 있다.
- 기대 결과: `summary=false` 전환 조건과 기존 호출 1회 상한을 함께 명시하고, 구간 계산 실패는 기존 실패 계약을 유지하며 형상만 누락된 경우만 별도 정책으로 다룬다.
- 영향 범위: 이슈 #231 후속 API·Adapter·프런트엔드 구현 판단. 현재 운영 코드와 데이터에는 변경이 없다.

## 4. 근본 원인

초안이 “외부 호출 횟수 1회”와 “현재 `summary=true` 요청 옵션”을 분리하지 않고 기존 응답 정규화로 묶었다. 이 때문에 같은 호출을 `summary=false`로 바꿔야 실제 경로점을 받을 수 있다는 제공자 조건이 누락됐다. 또한 기존 외부 실패 계약과 새 지도 표시 저하 정책을 하나의 `D-231-03` 선택지로 합쳐, 거리·시간 계산 실패와 거리·시간은 완전하지만 형상만 없는 상태가 구분되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| Kakao Mobility 자동차 길찾기 공식 계약의 `summary`·`roads`·`vertexes` 확인 | `summary=true`는 `roads`를 제외하고 `summary=false`만 `roads[].vertexes`를 제공 | `D-231-01`을 조건부 전환으로 수정 |
| `KakaoMobilityCourseRouteAdapter.requestUri()` 확인 | 현재 요청은 `summary=true`, 호출은 1회이며 정규화 대상은 거리·시간뿐 | 기존 구현 기준선은 맞지만 현재 옵션으로 형상 취득 불가 |
| `FR-COURSE-003`·`BR-COURSE-004`·코스 API 6절 대조 | 일부 구간 실패는 선택 목록과 실패 범주만 반환하고 정상 거리·시간을 추정하지 않음 | 구간 실패를 새 결정 대상에서 제외하고 기존 계약으로 고정 |
| PR #171 트러블슈팅 확인 | `summary=true`는 당시 요약 응답 계약을 명시하기 위해 추가됨 | 기존 해결을 되돌리는 것이 아니라 지도 형상 범위에서 전환 결정을 새로 요구 |

## 6. 최종 해결

- 변경 내용: `D-231-01`에 `summary=true` 형상 취득 불가와 `summary=false` 단일 호출 검증 조건을 명시했다. `D-231-03A`는 기존 부분 구간 실패 계약, `D-231-03B`는 거리·시간 정상·형상만 누락된 새 결정으로 나눴다. 상태표·API 초안·문서 갱신 순서·Adapter 검증·착수 조건도 같은 구분으로 연결했다.
- 선택 이유: 확정된 호출 1회·부분 실패 계약을 바꾸지 않고, 지도 형상 도입에만 필요한 제공자 옵션과 새 상태 결정을 분리할 수 있다.
- 변경 파일: `docs/08-planning/issue-231-course-route-map.md`, `docs/troubleshooting/pr-232-course-route-map-contract-review.md`, `docs/troubleshooting/README.md`.
- 고려한 대안: 별도 Mobility 호출은 현재 코스당 1회 상한과 충돌하고, 직선 대체는 실제 자동차 경로로 오인되므로 초안 권고에서 제외했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 공백 오류와 충돌 표식 없음 |
| 문서 `related_documents` 대상 존재 검사 | 통과 | 새 기록과 이슈 초안의 내부 문서 링크 존재 |
| Kakao 공식 계약·Adapter·요구사항·API·ADR 정적 대조 | 통과 | `summary` 모드, 호출 1회, 구간 실패 경계가 문서에 분리됨 |
| UTF-8 strict decode와 대체 문자 검사 | 통과 | 수정 문서의 한글 인코딩 정상 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 제공자 응답 옵션과 내부 호출 상한을 결정표에서 별도 조건으로 기록하고, 외부 실패와 표시 저하를 상태표·API 갱신 순서까지 연결했다.
- 다음 확인: `D-231-01` 승인 전 WS-16 담당자가 `summary=false` WireMock Fixture와 최대 응답 크기를 확인한다. 추적 이슈는 #231이다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 해당 없음 — 런타임 변경이 아닌 문서 의미 정정 | 해당 없음 | 리뷰 전후 계약 대조 | 해당 없음 | 정량 비교 대상 아님 | jinyp01, PR #232 리뷰 시점 |

## 10. 남은 사항

- `D-231-01`, `D-231-03B`, `D-231-06`의 최종 제품·API 선택은 이 Draft PR 리뷰에서 합의해야 한다.
- `summary=false` 실제 Fixture, 최대 응답 크기·비용·로그 경계는 구현 착수 조건으로 남는다.
