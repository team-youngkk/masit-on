---
related_documents:
  - README.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/discovery/popular-restaurants.md
  - ../05-specs/api/discovery/popular-restaurant-api.md
  - ../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
  - ../08-planning/second-expansion-test-matrix.md
---

# PR #214 리뷰 트러블슈팅: 인기 맛집 쿼리 측정과 스케줄러 격리

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#214 인기 맛집 쿼리 수 측정을 커넥션 풀 워밍업과 분리](https://github.com/team-youngkk/masit-on/pull/214) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-14 |
| 범위 | 인기 맛집 쿼리 수 회귀 테스트의 백그라운드 스케줄러 간섭 제거 |
| 주 문제 유형 | 애플리케이션·인프라(테스트 격리) |
| 기존 기록 | 없음 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [스케줄러 쿼리 격리](https://github.com/team-youngkk/masit-on/pull/214#discussion_r3783215082), [동일 요청 보강](https://github.com/team-youngkk/masit-on/pull/214#discussion_r3783226388) | 쿼리 측정 구간에서 `MemberSessionRevocationRecoveryService`의 백그라운드 SQL을 배제 | 애플리케이션·인프라 | 수정 필요 | 테스트 컨텍스트에서 복구 스케줄러를 `@MockitoBean`으로 대체 | `PopularRestaurantQueryCountApiTest` 단독 실행 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 소규모 시나리오의 쿼리 수가 인기 맛집 집계 SQL 외에 추가로 증가해 대규모 시나리오와 assertion이 어긋남.
- 발생 환경: `PopularRestaurantQueryCountApiTest`의 `@SpringBootTest` 컨텍스트와 static 전역 쿼리 카운터.
- 재현 조건: `reset()` 이후 `MemberSessionRevocationRecoveryService`가 실행되면 복구 작업의 `UPDATE ... RETURNING`과 후속 조회가 측정 구간에 섞인다.
- 기대 결과: 카운터는 두 공개 인기 맛집 조회 요청의 SQL만 세어 데이터 규모에 따른 쿼리 수 불변성을 검증해야 한다.
- 영향 범위: 인기 맛집 쿼리 수 회귀 테스트의 안정성. 운영 코드와 공개 API 계약에는 영향이 없다.

## 4. 근본 원인

`QueryCountingDataSourceConfiguration`의 카운터는 테스트 프로세스 전체에서 공유되는 static 값이다. 테스트 컨텍스트는 예약 작업을 활성화하므로, 워밍업 호출만으로는 `reset()` 이후 백그라운드 작업의 실행 완료를 보장하지 않는다. 그 결과 요청 SQL과 회원 세션 복구 SQL이 같은 측정값에 합산되는 타이밍 의존성이 생겼다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #214의 미해결 리뷰 스레드와 현재 테스트 코드 대조 | 두 리뷰가 같은 P1 원인을 지적하고, 스레드는 미해결·미 outdated 상태였다 | 하나의 수정으로 함께 처리 |
| `QueryCountingDataSourceConfiguration` 및 복구 서비스 확인 | static 전역 카운터와 `@Scheduled` 복구 메서드를 확인 | 측정 테스트 컨텍스트에서 복구 서비스를 mock 처리 |
| 운영 코드·API·ADR 대조 | 인기 집계는 요청 시점 단일 SQL이라는 계약이고 스케줄러 집계는 별도 기능임 | 운영 코드나 계약은 변경하지 않음 |
| 테스트 수정 후 단독 실행 | 통과 | 측정 테스트의 회귀 조건 확인 |

## 6. 최종 해결

- 변경 내용: `PopularRestaurantQueryCountApiTest`에 `@MockitoBean MemberSessionRevocationRecoveryService`를 추가해 테스트 컨텍스트의 복구 예약 실행을 대체했다.
- 선택 이유: 리뷰가 지적한 간섭 원인만 제거하고, 운영 스케줄러·쿼리 카운터 구현·API 계약은 변경하지 않는다.
- 변경 파일:
  - `src/test/java/com/masiton/orchestration/infrastructure/query/PopularRestaurantQueryCountApiTest.java`
  - `docs/troubleshooting/pr-214-popular-restaurant-query-count-review.md`
  - `docs/troubleshooting/README.md`
- 고려한 대안: 워밍업 호출을 추가하는 방식은 스케줄러와 동기화하지 않아 타이밍 의존성을 남기므로 채택하지 않았다. static 카운터를 요청·스레드 범위로 재설계하는 방식은 공유 fixture 전체의 범위를 넓히므로 이번 리뷰 수정에서는 선택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.orchestration.infrastructure.query.PopularRestaurantQueryCountApiTest` | 통과 | 소규모·대규모 인기 맛집 조회 쿼리 수 비교 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 쿼리 수를 측정하는 Spring 통합 테스트는 측정 대상이 아닌 예약 작업을 테스트 컨텍스트에서 mock 처리해 카운터 범위를 고정한다.
- 다음 확인: PR CI에서 백엔드 테스트가 통과하고 리뷰어가 동일 스레드를 재검토하는지 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 수정 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 인기 맛집 쿼리 수 테스트의 백그라운드 SQL 간섭 | 스케줄러 실행 시 측정값에 혼입 가능 | PR 리뷰 재현 조건 및 단독 통합 테스트 | 복구 스케줄러 mock 처리 후 단독 테스트 통과 | 측정 대상 요청 외 복구 SQL 경로를 테스트 컨텍스트에서 배제 | PR #214, 2026-08-14 |

## 10. 남은 사항

- 코드 수정과 단독 검증은 완료했다.
- PR 스레드 답글 및 해결 상태는 GitHub 반영 결과를 확인해야 한다.
