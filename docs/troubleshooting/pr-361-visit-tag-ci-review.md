---
related_documents:
  - README.md
  - ../05-specs/api/admin/restaurant-visit-tags-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../08-planning/restaurant-visit-tag-editing.md
  - pr-168-ai-schema-verification-review.md
---

# PR #361 리뷰 트러블슈팅: 방문 태그 fixture와 최신 Flyway 기대값

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#361 맛집 상세 관리자 방문 태그 조회·수정](https://github.com/team-youngkk/masit-on/pull/361) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-09-06 |
| 범위 | 관리자 방문 태그 통합 테스트 fixture와 V9 추가 후 기존 최신 마이그레이션 단언 |
| 주 문제 유형 | 데이터베이스, 애플리케이션 |
| 기존 기록 | [PR #168 AI V4 인덱스 검증 회귀](pr-168-ai-schema-verification-review.md)의 마이그레이션 검증 동기화 원칙을 재사용했다. 전화번호 fixture와 같은 기존 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [변경 요청 리뷰](https://github.com/team-youngkk/masit-on/pull/361#pullrequestreview-5124275277) | 신규 통합 테스트 fixture의 빈 전화번호를 계약에 맞게 수정 | 데이터베이스 | 수정 필요 | `02-1234-5678`로 수정 | CI 8건이 같은 CHECK 위반으로 실패한 XML과 V1 제약을 대조 |
| [백엔드 CI](https://github.com/team-youngkk/masit-on/actions/runs/34010902833/job/101426432821) | V9 추가 뒤 최신 마이그레이션 목록 기대값이 V8에 머묾 | 애플리케이션 | 수정 필요 | 기존 빈 DB 테스트 기대값에 V9 추가 | CI XML에서 실제 1~9와 기대 1~8 불일치를 확인 |
| [백엔드 CI](https://github.com/team-youngkk/masit-on/actions/runs/34010902833/job/101426432821) | Kakao Mobility WireMock 테스트 29건이 모두 EOF로 실패 | 인프라 | 수정 불필요 | 첫 실행의 독립적인 WireMock 서버 시작 실패로 분류하고 후속 CI에서 비재현 확인 | 29건 모두 0.272초 안에 같은 HTTP header EOF로 실패했고 [후속 전체 백엔드 CI](https://github.com/team-youngkk/masit-on/actions/runs/34023577158/job/101460483329)가 코드 변경 없이 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `violates check constraint "ck_restaurant__phone_number"`, 최신 Flyway 실제 버전 `1~9`와 기대 버전 `1~8` 불일치.
- 발생 환경: GitHub Actions Ubuntu 24.04, JDK 21.0.12, PostgreSQL 17.10 Testcontainers, PR #361 최초 커밋 `b9c37d68`.
- 재현 조건: `VisitTagIntegrationTest.fixture()`가 `phone_number=''`인 Restaurant를 INSERT하거나, V9가 포함된 빈 DB에서 기존 확장 3 마이그레이션 검증을 실행한다.
- 실제 결과: 방문 태그 통합 테스트 8건이 fixture 생성 단계에서 종료되고, 기존 Flyway 테스트 1건이 V9를 예상하지 못해 실패했다.
- 기대 결과: fixture가 V1 물리 제약을 만족하고 모든 최신 마이그레이션 검증이 V9까지 확인해야 한다.
- 영향 범위: PR 백엔드 CI와 신규 VisitTag API·트랜잭션·감사 테스트의 실행 가능성.

## 4. 근본 원인

신규 fixture를 작성하면서 `phone_number`가 NOT NULL이고 7~20자의 허용 문자 형식을 가져야 한다는 V1 제약을 반영하지 않았다. 또한 신규 마이그레이션 전용 테스트와 공통 `FlywayMigrationIntegrationTest`는 V9로 갱신했지만, 별도 스키마를 생성하는 `Expansion3FlywayMigrationIntegrationTest`의 최신 버전 목록 단언을 함께 갱신하지 않았다.

WireMock 29건은 테스트 본문 진입 전 서버 응답이 없는 동일 EOF이고, PR이 해당 코드·fixture·설정을 변경하지 않았다. 후속 전체 CI가 해당 모듈 변경 없이 통과해 일시적인 테스트 서버 기동 실패였음을 확인했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 실패 job의 테스트 XML 다운로드·분류 | 38건 중 VisitTag 8, Flyway 1, WireMock EOF 29 | 이번 변경의 확정 원인 9건을 우선 수정 |
| V1 Restaurant CHECK와 fixture SQL 대조 | 빈 문자열이 길이 제약을 위반 | 유효한 로컬 테스트 전화번호로 수정 |
| Flyway 이력과 세 개의 마이그레이션 테스트 대조 | Expansion3 테스트 한 곳만 V8 기대 | V9를 기대 목록에 추가 |
| PR 변경 파일과 WireMock 실패 모듈 대조 | 겹치는 코드·설정 없음 | 후속 전체 CI에서 재현 여부 확인 |
| 로컬 Docker Desktop 기동 | `sailor-ingest.sock` 접근 오류로 엔진 시작 실패 | 로컬 Testcontainers 대신 GitHub Actions Docker 환경 사용 |

## 6. 최종 해결

- 변경 내용: 방문 태그 Restaurant fixture에 계약을 만족하는 전화번호를 사용하고, 확장 3 빈 DB 검증이 V9까지 단언하게 했다.
- 선택 이유: 제품 코드나 계약을 바꾸지 않고 잘못된 테스트 입력과 누락된 기대값만 고치는 최소 변경이다.
- 변경 파일: `src/test/java/com/masiton/visit/VisitTagIntegrationTest.java`, `src/test/java/com/masiton/Expansion3FlywayMigrationIntegrationTest.java`, `docs/troubleshooting/README.md`, 이 문서.
- 고려한 대안: 전화번호 컬럼이나 CHECK를 완화하는 변경은 기능 계약 변경이며 fixture 오류 해결 범위를 벗어나 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat compileTestJava --no-daemon --console=plain` | 통과 | 수정된 테스트 소스 컴파일 |
| `git diff --check` | 통과 | 공백·패치 형식 |
| [GitHub Actions 최종 전체 백엔드 CI](https://github.com/team-youngkk/masit-on/actions/runs/34023794013/job/101461069960) | 통과 | VisitTag 통합 10건, V9 migration, 기존 Flyway와 WireMock을 포함한 전체 `clean build` |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 통합 fixture는 실제 V1 CHECK를 만족하는 값을 사용하고, 신규 Flyway 파일 추가 시 최신 버전을 단언하는 모든 테스트를 함께 갱신한다.
- 다음 확인: 없음. PR #361 후속 전체 백엔드 CI에서 WireMock EOF가 재현되지 않았다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 이번 PR 원인의 백엔드 실패 | 9건 | PR #361 CI 테스트 XML | 후속 CI 0건 | fixture·최신 버전 단언 수정 후 해소 | 양성훈, PR #361 후속 CI |
| 방문 태그 통합 테스트 실행 | 10건 중 8건 fixture 실패, 권한 2건 통과 | `VisitTagIntegrationTest` | 10건 전체 통과 | 실제 태그 조회·수정·감사 경계 실행 | 양성훈, PR #361 후속 CI |
| WireMock EOF | 29건 | 같은 전체 백엔드 job 재실행 | 0건 | 일시 테스트 서버 기동 실패로 확인 | 양성훈, PR #361 후속 CI |

## 10. 남은 사항

- 변경 요청은 인라인 스레드가 아닌 리뷰 본문으로 작성되어 GitHub API로 개별 resolve할 대상은 없었다. 수정 커밋과 후속 CI 통과 뒤 받은 승인은 해결 기록 추가 커밋으로 오래된 승인이 되어 자동 해제되었으며, 최신 커밋 기준 재검토를 요청한다.
