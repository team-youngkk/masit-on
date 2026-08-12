---
related_documents:
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
  - ./pr-122-map-viewport-independent-query-review.md
  - ../../src/test/java/com/masiton/ThirdExpansionIntegrationRegressionTest.java
  - ../../src/test/java/com/masiton/ai/application/AiExtractionCommitProjectionIntegrationTest.java
---

# PR #178 리뷰 트러블슈팅: 3차 확장 통합 회귀 테스트 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#178](https://github.com/team-youngkk/masit-on/pull/178) |
| 작성자 | 이우람 |
| 처리 일자 | 2026-08-12 |
| 범위 | E3-T11 Flyway·AI 확정 커밋·Workstream 교차 통합 회귀 테스트 리뷰 |
| 주 문제 유형 | 데이터베이스 / 테스트 |
| 기존 기록 | [PR #122 테스트 결과 최신화 누락](pr-122-map-viewport-independent-query-review.md), [PR #175 관리자 AI 검수 동시성·태그 감사 후속](pr-175-ai-admin-review-follow-up.md) 확인 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / `ThirdExpansionIntegrationRegressionTest.java` | numeric 컬럼에 문자열 fixture 바인딩 | 데이터베이스 | 수정 필요 | `BigDecimal` 바인딩으로 변경 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / `AiExtractionCommitProjectionIntegrationTest.java` | numeric 컬럼에 문자열 fixture 바인딩 | 데이터베이스 | 수정 필요 | `BigDecimal` 바인딩으로 변경 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / 두 통합 테스트의 `CASCADE` 정리 | V4 시드 태그가 삭제될 수 있음 | 데이터베이스 | 수정 필요 | 시드 보존 + FK 역순 명시 삭제로 변경 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / 자연어 검색 fixture | 태그 없는 맛집 비교 부족 | 테스트 | 수정 필요 | 태그 없는 맛집을 추가하고 `totalElements = 1` 검증 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / 코스 TIMEOUT 시나리오 | 기존 코스 실패 테스트와 중복 | 테스트 | 수정 필요 | 실패 후 AI 태그 자연어 검색을 확인하는 교차 검증으로 변경 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / AI 실행 job 정리 | 테스트 후 job·snapshot 잔존 가능 | 데이터베이스 | 수정 필요 | audit·snapshot·attempt·temporary input·job 정리 추가 | CI #421 및 Docker 로컬 회귀 묶음 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / SHA-256 helper 중복 | 테스트 유틸리티 공유 요청 | 테스트 | 수정 필요 | `IntegrationTestFixtures.sha256`로 통합 | CI #421 및 `compileTestJava` 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / course request JSON helper 중복 | 요청 JSON helper 공유 요청 | 테스트 | 수정 필요 | `IntegrationTestFixtures.courseRequestJson`으로 추출 | CI #421 및 `compileTestJava` 통과 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / 별도 AI 통합 테스트 클래스 | 기존 원자성 테스트와의 중복 검토 | 테스트 | 수정 불필요 | 기존 테스트는 실패 롤백, 신규 테스트는 성공 provenance와 공개 자연어 API 경로를 검증해 목적이 다름 | 기존 테스트와 신규 assertion 비교 |
| [PR #178](https://github.com/team-youngkk/masit-on/pull/178) / 검증 기록 최신화 | 후속 실행 결과와 문서·PR 본문 불일치 | 기타 | 수정 필요 | PR #122의 재발 방지 규칙을 현재 기록과 PR 본문에 반영 | CI #421·Docker 로컬 결과와 문서 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `column "latitude" is of type numeric but expression is of type character varying`
- 발생 환경: Windows, Java 21, `feature/t-164-integration-regression`, PostgreSQL Testcontainers; 이후 GitHub Actions Ubuntu 24.04 및 Docker 가동 로컬 환경에서 재검증
- 재현 조건: JDBC fixture가 `latitude`, `longitude`에 문자열을 바인딩하거나, 테스트 시작 시 `TRUNCATE ... CASCADE`를 실행하는 경우
- 실제 결과: fixture 삽입 전에 PostgreSQL 타입 오류가 발생하고, CASCADE 정리는 V4 SEED 태그까지 삭제할 수 있음
- 기대 결과: fixture가 실제 스키마 타입으로 삽입되고, 테스트 정리가 시드 데이터와 다른 테스트의 감사 상태를 보존해야 함
- 영향 범위: 통합 테스트 재현성, AI 태그 공개 검색 회귀 검증, 테스트 간 데이터 오염
- 추가 현상: 후속 커밋과 CI·Docker 실행으로 검증 상태가 바뀌었는데, PR 본문과 트러블슈팅 문서가 이전의 Docker 초기화 실패 상태를 유지했다.

## 4. 근본 원인

테스트 fixture의 JDBC 바인딩 타입이 실제 PostgreSQL `numeric` 계약과 달랐고, 여러 도메인 테이블을 한 번에 CASCADE 삭제해 V4 시드 데이터까지 정리 대상에 포함시켰다. 또한 코스 실패 검증은 기존 실패 테스트와 같은 공개 조회 경로를 반복해 교차 Workstream 회귀 신호가 약했다. 검증 기록은 구현·리뷰 반영 이후의 최신 실행 결과를 다시 대조하는 절차가 명시되지 않아, 실제 CI·Docker 결과보다 뒤처졌다. 이는 [PR #122](pr-122-map-viewport-independent-query-review.md)에서 확인된 “후속 커밋 후 PR 본문 테스트 결과를 갱신하지 않는” 유형과 같다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 관련 V4/V6/V7 migration의 FK·트리거 확인 | `tag_definition`은 snapshot을 참조하고, audit review는 append-only DELETE trigger를 가짐 | audit table은 `TRUNCATE`하고 나머지는 FK 순서대로 명시 삭제 |
| `./gradlew.bat compileTestJava --no-daemon --console=plain` | 통과 | 소스·fixture 타입 수정 완료 |
| [`CI #421`](https://github.com/team-youngkk/masit-on/actions/runs/31570268267) 백엔드 `./gradlew --no-daemon clean build` | 통과 | Flyway·AI 원자성·AI worker·코스 회귀를 포함한 전체 백엔드 빌드·테스트 통과 |
| Docker 가동 로컬 회귀 묶음 | 통과 | 신규 3개 통합 테스트와 수정된 Flyway·AI 원자성·AI worker·코스 회귀 테스트 통과를 리뷰에서 확인 |
| [PR #122 테스트 결과 최신화 기록](pr-122-map-viewport-independent-query-review.md)과 현재 문서 대조 | 반영 | 후속 커밋·CI·Docker 실행 결과를 문서와 PR 본문에 동기화하는 재발 방지 절차를 재사용 |

## 6. 최종 해결

- 변경 내용: numeric fixture를 `BigDecimal`로 변경하고, 시드 태그를 보존하는 정리 순서를 추가했다. 태그 없는 맛집 비교와 코스 실패 후 자연어 검색을 추가했다. 반복 SHA-256·코스 요청 JSON helper를 테스트 유틸리티로 추출했다.
- 문서 변경: [PR #122 테스트 결과 최신화 기록](pr-122-map-viewport-independent-query-review.md)의 원인·재발 방지 항목을 현재 PR 기록에 연결하고, CI·Docker 검증 결과를 후속 커밋 기준으로 대조하도록 보완했다.
- 선택 이유: 운영 코드와 스키마 계약을 바꾸지 않고 테스트 fixture와 검증 경계만 최소 수정하기 위해서다.
- 변경 파일: `src/test/java/com/masiton/ThirdExpansionIntegrationRegressionTest.java`, `src/test/java/com/masiton/ai/application/AiExtractionCommitProjectionIntegrationTest.java`, `src/test/java/com/masiton/test/IntegrationTestFixtures.java`, 관련 AI 테스트 2개
- 고려한 대안: 기존 공개 조회 3종을 반복하는 코스 실패 테스트는 제거하지 않고, AI 태그 검색을 추가해 두 Workstream의 독립성을 확인하도록 변경했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileTestJava --no-daemon --console=plain` | 통과 | 리뷰 반영 테스트 소스 컴파일 |
| [`CI #421`](https://github.com/team-youngkk/masit-on/actions/runs/31570268267) 백엔드 `./gradlew --no-daemon clean build` | 통과 | 백엔드 전체 자동화 테스트와 빌드 성공 |
| Docker 가동 로컬 회귀 묶음 | 통과 | 신규 3개 통합 테스트와 수정된 Flyway·AI 원자성·AI worker·코스 회귀 테스트 성공 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 통합 fixture는 JDBC 타입을 스키마와 맞추고, 테스트 정리는 시드·감사 데이터의 보존 정책을 명시한다. 자연어 결과는 태그 없는 비교 fixture와 결과 개수까지 검증한다.
- 재발 방지: 테스트 결과는 PR 작성 시점뿐 아니라 코드·문서 후속 커밋마다 실제 명령 출력과 CI 실행을 다시 확인해 갱신한다. 테스트 개수·통과 여부·미검증 항목은 추정치나 이전 실행 결과를 재사용하지 않고 최신 head 기준으로 문서와 PR 본문을 함께 대조한다. 이 절차는 [PR #122의 재발 방지 기록](pr-122-map-viewport-independent-query-review.md)을 표준으로 재사용한다.
- 다음 확인: 없음. PR head 기준 CI와 Docker 가동 로컬 회귀 묶음 검증이 완료되었다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 통합 테스트 fixture 타입·정리 오류 | 리뷰에서 2종 오류 지적 | CI #421 및 Docker 가동 로컬 회귀 묶음 | 전체 통과 | fixture 타입·정리 오류 재현 없이 성공 | PR #178 / 2026-08-12 |
| 검증 결과 문서·PR 본문 동기화 | 후속 실행 결과 반영 누락 | 각 후속 커밋 후 실제 출력·CI 결과와 문서 대조 | 현재 head 기준 일치 | PR #122 재발 방지 절차를 PR #178에 적용 | PR #178 / 2026-08-12 |

## 10. 남은 사항

- 남은 사항 없음. 현재 Codex 실행 환경에서는 Docker 재실행을 추가로 수행하지 못했지만, PR head의 CI 전체 빌드·테스트와 리뷰에서 확인된 Docker 가동 로컬 회귀 묶음은 통과했다.
