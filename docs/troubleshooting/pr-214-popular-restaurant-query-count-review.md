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
| 범위 | 인기 맛집 쿼리 수 회귀 테스트의 백그라운드 스케줄러 간섭 제거와 PR 본문 정합성 보완 |
| 주 문제 유형 | 애플리케이션·인프라(테스트 격리) |
| 기존 기록 | 없음 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [스케줄러 쿼리 격리](https://github.com/team-youngkk/masit-on/pull/214#discussion_r3783215082), [동일 요청 보강](https://github.com/team-youngkk/masit-on/pull/214#discussion_r3783226388) | 쿼리 측정 구간에서 모든 DB 예약 작업의 백그라운드 SQL을 배제 | 애플리케이션·인프라 | 수정 필요 | 테스트 컨텍스트의 모든 `@Scheduled` DB 작업을 `@MockitoBean`으로 대체 | `PopularRestaurantQueryCountApiTest` 단독 실행 통과 |
| [PR 본문 정합성](https://github.com/team-youngkk/masit-on/pull/214#discussion_r3783445919) | 최신 원인·변경 파일·검증 상태에 맞게 PR 본문 갱신 | 문서·협업 | 수정 필요 | static 전역 카운터와 8개 DB 예약 작업 간섭을 기준으로 PR 본문 갱신 | PR 본문과 변경 파일·검증 결과 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 소규모 시나리오의 쿼리 수가 인기 맛집 집계 SQL 외에 추가로 증가해 대규모 시나리오와 assertion이 어긋남.
- 발생 환경: `PopularRestaurantQueryCountApiTest`의 `@SpringBootTest` 컨텍스트와 static 전역 쿼리 카운터.
- 재현 조건: `reset()` 이후 테스트 컨텍스트의 DB 예약 작업이 실행되면 해당 작업의 SQL이 측정 구간에 섞인다.
- 기대 결과: 카운터는 두 공개 인기 맛집 조회 요청의 SQL만 세어 데이터 규모에 따른 쿼리 수 불변성을 검증해야 한다.
- 영향 범위: 인기 맛집 쿼리 수 회귀 테스트의 안정성. 운영 코드와 공개 API 계약에는 영향이 없다.

## 4. 근본 원인

`QueryCountingDataSourceConfiguration`의 카운터는 테스트 프로세스 전체에서 공유되는 static 값이다. 테스트 컨텍스트는 여러 DB 예약 작업을 활성화하므로, 특정 복구 서비스 하나만 mock하거나 워밍업 호출만으로는 `reset()` 이후 백그라운드 작업의 실행 완료를 보장하지 않는다. 그 결과 요청 SQL과 예약 작업 SQL이 같은 측정값에 합산되는 타이밍 의존성이 생겼다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #214의 미해결 리뷰 스레드와 현재 테스트 코드·본문 대조 | 기존 P1 두 건과 후속 P2 한 건은 테스트 격리와 PR 본문 정합성을 각각 지적했다 | 코드와 문서를 함께 갱신 |
| `QueryCountingDataSourceConfiguration` 및 모든 `@Scheduled` DB 작업 확인 | static 전역 카운터와 즉시·지연·cron 예약 작업을 확인 | 측정 테스트 컨텍스트에서 모든 DB 예약 작업을 mock 처리 |
| 운영 코드·API·ADR 대조 | 인기 집계는 요청 시점 단일 SQL이라는 계약이고 스케줄러 집계는 별도 기능임 | 운영 코드나 계약은 변경하지 않음 |
| 테스트 수정 후 단독 실행 | 통과 | 측정 테스트의 회귀 조건 확인 |

## 6. 최종 해결

- 변경 내용: `PopularRestaurantQueryCountApiTest`에서 AI Worker·임시 입력 정리·idempotency 정리·회원 메일 outbox·회원 삭제·회원 세션 복구·최근 조회 정리·보존 정리의 모든 `@Scheduled` DB 작업을 `@MockitoBean`으로 대체했다.
- 선택 이유: 리뷰가 지적한 간섭 원인만 제거하고, 운영 스케줄러·쿼리 카운터 구현·API 계약은 변경하지 않는다.
- 변경 파일:
  - `src/test/java/com/masiton/orchestration/infrastructure/query/PopularRestaurantQueryCountApiTest.java`
  - `docs/troubleshooting/pr-214-popular-restaurant-query-count-review.md`
  - `docs/troubleshooting/README.md`
- 고려한 대안: 워밍업 호출만 추가하는 방식은 스케줄러와 동기화하지 않아 타이밍 의존성을 남기므로 단독 해결책으로 채택하지 않았다. static 카운터를 요청·스레드 범위로 재설계하는 방식은 공유 fixture 전체의 범위를 넓히므로 이번 리뷰 수정에서는 선택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.orchestration.infrastructure.query.PopularRestaurantQueryCountApiTest` | 통과 | 소규모·대규모 인기 맛집 조회 쿼리 수 비교 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 쿼리 수를 측정하는 Spring 통합 테스트는 현재 등록된 모든 DB 예약 작업을 테스트 컨텍스트에서 mock 처리해 카운터 범위를 고정한다.
- 다음 확인: PR CI에서 백엔드 테스트가 통과하고 리뷰어가 동일 스레드를 재검토하는지 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 수정 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 인기 맛집 쿼리 수 테스트의 백그라운드 SQL 간섭 | DB 예약 작업 실행 시 측정값에 혼입 가능 | PR 리뷰 재현 조건 및 단독 통합 테스트 | 모든 DB 예약 작업 mock 처리 후 단독 테스트 통과 | 측정 대상 요청 외 예약 SQL 경로를 테스트 컨텍스트에서 배제 | PR #214, 2026-08-14 |

## 10. 남은 사항

- 코드 수정과 단독 검증은 완료했다.
- 기존 스레드와 후속 P1 두 건·P2 두 건에 답글을 등록했고 모두 `isResolved: true` 상태를 확인했다.
- 최신 문서 갱신 커밋 push 후 GitHub Actions 백엔드 빌드·테스트 결과와 리뷰어의 재검토를 확인한다.
