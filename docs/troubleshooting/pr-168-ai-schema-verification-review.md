---
related_documents:
  - README.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/index-strategy.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/data/data-004-flyway.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - pr-131-expansion-foundation-review.md
  - pr-142-public-curation-review.md
---

# PR #168 리뷰 트러블슈팅: AI V4 인덱스 검증 회귀와 테스트 형식

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#168 AI V4 물리 스키마 검증을 강화한다](https://github.com/team-youngkk/masit-on/pull/168) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-11 |
| 범위 | 두 리뷰어가 확인한 V4 인덱스 검증 회귀, 신규 테스트 Given-When-Then 구분, `HashMap` import 일관성 |
| 주 문제 유형 | 데이터베이스, 애플리케이션 |
| 기존 기록 | [PR #131 기록](pr-131-expansion-foundation-review.md)의 DB 계약 직접 검증 원칙과 [PR #142 기록](pr-142-public-curation-review.md)의 완전 수식명 import 정리 사례를 적용했다. 같은 V4 인덱스 검증 누락 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [V4 인덱스 6개 검증 복원](https://github.com/team-youngkk/masit-on/pull/168#discussion_r3754758299) | V4가 생성하는 Worker claim·검수·태그 조회 인덱스 6개를 명시적으로 검증 | 데이터베이스 | 수정 필요 | 공통 스키마 검증에 여섯 인덱스 이름의 정확한 집합 단언을 추가 | `Expansion3FlywayMigrationIntegrationTest` 6건 통과 |
| [V4 인덱스 검증 누락 재확인](https://github.com/team-youngkk/masit-on/pull/168#discussion_r3754773313) | 인안님이 같은 V4 인덱스 6개 검증 공백을 독립적으로 확인하고 복원을 요청 | 데이터베이스 | 수정 필요 | 앞선 스레드와 같은 근본 원인으로 분류하고 공통 스키마 검증의 정확한 이름 집합 단언으로 함께 해결 | `Expansion3FlywayMigrationIntegrationTest` 6건 통과 |
| [Given-When-Then 구분](https://github.com/team-youngkk/masit-on/pull/168#discussion_r3754758302) | 신규 빈 DB 테스트의 본문 구조를 프로젝트 테스트 규칙에 맞춤 | 애플리케이션 | 수정 필요 | 준비·실행·검증 구간 주석 추가 | 컴파일과 대상 테스트 통과 |
| [`HashMap` import 정리](https://github.com/team-youngkk/masit-on/pull/168#discussion_r3754758309) | 단일 완전 수식명을 일반 import로 통일 | 애플리케이션 | 수정 필요 | `java.util.HashMap` import 후 `new HashMap<>()` 사용 | 컴파일과 대상 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 테스트가 통과하면서 V4 인덱스 누락·개명을 감지하지 못하는 검증 공백이었다.
- 발생 환경: Windows, Java 21, PostgreSQL 17.10 Testcontainers, `feature/t-156-ai-extraction-schema`
- 재현 조건: 변경 전 `assertAiSchemaAndContracts`와 두 마이그레이션 경로에서 조회하는 인덱스 이름을 V4·V5 DDL과 대조한다.
- 실제 결과: V5 인덱스 3개만 검증하고 V4 인덱스 6개는 저장소 테스트 어디에서도 참조하지 않았다. 신규 테스트에는 Given-When-Then 구분이 없고 `HashMap`만 완전 수식명을 사용했다.
- 기대 결과: V4 공통 스키마 검증이 V4 인덱스 6개를 항상 확인하고, 신규 테스트와 import가 프로젝트 형식을 따른다.
- 영향 범위: Worker claim, 관리자 검수, 태그 조회 인덱스의 마이그레이션 회귀 감지와 테스트 코드 유지보수성

## 4. 근본 원인

V3→V4와 빈 DB→V5 검증을 분리하면서 기존 9개 인덱스 집계에서 V5 인덱스 3개만 각 테스트로 옮겼고, V4 인덱스 6개를 공통 검증으로 이전하지 않았다. 스키마 helper가 PK·UK·FK·CHECK·컬럼을 넓게 검증하므로 물리 계약 전체를 포괄한다고 잘못 판단한 것이 원인이다.

두 형식 문제는 큰 카탈로그 검증 helper를 한 번에 추가하면서 신규 테스트 본문 구조와 import 정리를 기존 파일 스타일에 대조하지 않은 데서 발생했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| V4·V5 DDL과 변경 전후 인덱스 단언 대조 | 기존 9개 중 V4 6개 단언이 사라지고 V5 3개만 남음 | 리뷰 지적 재현, V4 공통 helper에 정확한 이름 집합 추가 |
| `src/test` 전체에서 V4 인덱스 이름 검색 | 다른 검증 없음 | 현재 테스트에서 누락을 복원해야 함 |
| 데이터 계약과 인덱스 전략 확인 | 여섯 인덱스의 조회 목적과 이름이 현재 Accepted 계약에 존재 | 계약 변경 없이 테스트만 보강 |
| PR #131·#142 트러블슈팅 확인 | DB 직접 검증과 일반 import 통일 원칙이 현재 계약과 일치 | 기존 해결 원칙 재사용 |
| 인안님 요청 변경 리뷰와 최신 미해결 스레드 재조회 | 기존 P2와 같은 V4 인덱스 검증 공백을 독립적으로 지적 | 동일 근본 원인으로 묶어 수정·검증 후 두 스레드에 각각 결과 회신 |

## 6. 최종 해결

- 변경 내용: `assertAiSchemaAndContracts`가 V4 인덱스 6개의 이름을 `containsExactlyInAnyOrder`로 검증하게 했다.
- 변경 내용: 빈 DB 테스트에 `// given`, `// when`, `// then` 구분을 추가했다.
- 변경 내용: `java.util.HashMap`을 import하고 완전 수식명을 제거했다.
- 선택 이유: V4와 V5의 책임 분리를 유지하면서 두 마이그레이션 경로가 같은 V4 공통 계약을 재사용하는 최소 변경이다.
- 변경 파일: `src/test/java/com/masiton/Expansion3FlywayMigrationIntegrationTest.java`, `docs/troubleshooting/README.md`, 이 문서
- 고려한 대안: 인덱스 개수만 다시 9개로 합치면 V4·V5 책임이 섞이고 잘못된 이름을 구분하기 어려워 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests com.masiton.Expansion3FlywayMigrationIntegrationTest --rerun-tasks --no-daemon --console=plain` | 통과, 6건 | V3→V4와 빈 DB→V5, V4 인덱스 6개, V5 인덱스 3개와 기존 제약 회귀 |
| `git diff --check` | 통과 | 공백·줄 끝 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 버전별 마이그레이션 검증을 분리할 때 공통 helper가 이전 버전의 테이블·제약·인덱스 계약을 모두 보존하는지 이름 집합으로 확인한다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| V4 인덱스 이름 회귀 검증 | 0/6개 | 대상 Testcontainers 테스트에서 `pg_indexes` 이름 집합 대조 | PR 수정 후 6/6개 | 누락·개명 시 테스트 실패 | 박진영, PR #168 리뷰 반영 시점 |
| V4 인덱스 관련 미해결 리뷰 | 2건 | 최신 PR 리뷰 스레드 조회 | PR 수정 후 0건 | 두 리뷰어의 동일 회귀 지적을 한 수정으로 해결 | 박진영, PR #168 리뷰 반영 시점 |
| 신규 코드 형식 지적 | 2건 | 미해결 리뷰 스레드 수 | PR 수정 후 0건 | Given-When-Then·import 형식 통일 | 박진영, PR #168 리뷰 반영 시점 |

## 10. 남은 사항

- 없음.
