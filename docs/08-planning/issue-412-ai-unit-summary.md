---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-226-ai-auto-registration-contract-review.md
---

# #412 AI 등록 단위 집계

## 문제와 원인

6개 자동 등록과 4개 보류가 섞인 작업의 최상위 `AUTO_BLOCKED`는 처리할 예외가 남았다는 계약상 올바른 요약이다. 화면은 이 값만 보여 주므로 전부 실패로 오인할 수 있다. 실행 상태, 후보 판정 상태, 현재 등록 결과를 각각 표시한다. 기존 Enum, 판정 기준, 데이터 저장 구조는 변경하지 않는다.

## 변경 범위와 설계

상세의 기존 `registrationUnits`에서 네 상태별 개수와 현재 등록 개수를 계산한다. 현재 등록은 `reviewStatus`가 `AUTO_CONFIRMED` 또는 `MANUAL_OVERRIDE`이며 네 등록 결과 ID가 모두 존재하고 `manualOverrideType`이 `null`인 단위다. `manualOverrideType`은 수동 작업 여부가 아니라 종결 유형으로, 값은 `ROLLED_BACK`·`DISCARDED`·`null`뿐이다. 보충 등록과 카테고리 보정도 종결 유형은 `null`이다. `MANUAL_OVERRIDE` 자체를 성공으로 세지 않는다. 롤백·폐기는 등록 0건이며 카테고리 보정과 보충 등록은 ID가 유지되므로 등록에 포함한다. `0 < registered < total`이면 부분 등록, `registered === total && total > 0`이면 전체 등록, 0개이면 등록 단위 없음으로 표시한다. 원자 등록 계약에 맞지 않는 ID 일부만 존재하는 값은 성공으로 세지 않는다.

목록은 상세 API를 항목마다 추가 호출하지 않고 현재 페이지 ID를 묶어 한 번의 DB 집계로 처리한다. 소유자 합의 확보를 사용자에게 확인한 뒤 목록과 상세 GET의 집계 필드를 구현했다.

## API 제안 — 소유자 합의 확보

목록·상세 GET 응답에 `registrationSummary`를 추가한다. 기존 필드와 상태는 유지하며 접수·재시도 응답은 변경하지 않는다. 최신 Snapshot에 속한 현재 단위를 집계한다. Snapshot이나 단위가 없으면 모든 개수는 0, `partialRegistration`은 false다. 개수는 음이 아닌 정수이며 네 상태 개수의 합은 total이다.

| 필드 | 의미 |
|---|---|
| `total` | 등록 단위 개수 |
| `autoConfirmed` | AUTO_CONFIRMED 개수 |
| `autoBlocked` | AUTO_BLOCKED 개수 |
| `autoRejected` | AUTO_REJECTED 개수 |
| `manualOverride` | MANUAL_OVERRIDE 개수. 등록·롤백·폐기를 모두 포함 |
| `registered` | 현재 네 등록 결과 ID가 모두 있는 단위 개수. 롤백·폐기 제외 |
| `partialRegistration` | registered가 0보다 크고 total보다 작음 |

6개 확정·4개 보류는 total=10, autoConfirmed=6, autoBlocked=4, registered=6, partialRegistration=true다. 전부 등록은 registered=total>0, 전부 보류는 autoBlocked=total>0과 registered=0이다. 단위 0개는 후보 Snapshot 판정과 독립이다. 목록과 상세는 같은 집계 함수를 사용하고 페이지 조회는 단위별 상세 호출 대신 DB 집계로 처리한다. 필드 추가는 기존 소비자에게 가산적이지만 API 계약 변경이므로 소유자 합의 후 원문과 코드를 동기화한다. DB·Prompt·Provider Schema 변경은 없다.

## 의존성과 회귀 위험

#413은 여기서 만든 요약을 소비할 수 있다. #415·#420과 독립이며 등록 실행 서비스는 수정하지 않는다. 과거 #226 기록대로 MANUAL_OVERRIDE의 등록·롤백·폐기 의미를 구분하고 작업 최상위 상태만으로 성공을 판단하지 않는다. #244·#259의 등록 원자성·감사 이력은 조회만 하며 변경하지 않는다.

## 검증 계획과 결과

독립 리뷰에서 P1/P2 지적 없이 대상 Gradle·diff 검사를 재실행해 통과했다. SQL과 Java 집계는 최신 Snapshot·4종 ID·롤백/폐기 제외의 같은 기준을 사용한다. 6/4와 수동 종결 혼합을 한 PostgreSQL fixture에서 모두 비교하는 검증은 추가 가능 항목이다.

고정 등록 단위로 6/4 혼합, 전체 등록, 전체 보류, 0개, 거부 혼합, 수동 등록·롤백·폐기, 일부 ID만 존재하는 방어 경계를 검증한다. 프론트 관련 테스트와 TypeScript 컴파일을 실행한다. backend 조회·응답 계약 테스트와 새 집계 단위 테스트, PostgreSQL 목록·상세 동등성 검증을 실행한다. 실제 Kakao·YouTube·Gemini 호출, 운영 재처리·등록·폐기·배포는 수행하지 않는다.

기존 API만 소비하는 상세 집계와 공통 헬퍼를 구현했다. `node --test lib/admin/ai-video-extractions-coordination.test.ts lib/admin/ai-video-extractions.test.ts` 38건 통과, 기존 루트 의존성을 읽기 전용 Junction으로 참조한 TypeScript `tsc --noEmit --project frontend/tsconfig.json` 통과. 초기 컴파일은 격리 디렉터리의 node_modules 부재 때문에 실패했으며 의존성 참조 후 통과했다. 시스템 Node는 24.19.0으로 계약 24.18.0과 달라 해당 버전에서의 재실행은 미검증이다. 사용자가 소유자 합의 확보를 명시하여 API 필드 추가를 승인했다. 목록은 현재 페이지(최대 50개) ID를 묶어 최신 Snapshot의 단위를 한 번의 DB 집계로 조회한다. 독립 리뷰는 주 에이전트가 진행한다.

백엔드 compileJava·compileTestJava와 AdminAiExtractionQueryServiceTest·AdminAiVideoExtractionControllerTest·AdminAiVideoExtractionControllerApiTest 및 ArchUnit 통과. RegistrationSummaryTest 3건과 ArchUnit도 통과. PostgreSQL JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest 17건과 ArchUnit 11건 통과. 등록 완료·0개·전체 보류의 목록 SQL 집계와 상세 단위 집계 동등성을 확인했다. 6/4 혼합·수동·거부·롤백·폐기 조합은 Java와 프론트 고정 fixture에서 확인했으며 DB 전체 조합은 미검증이다.
