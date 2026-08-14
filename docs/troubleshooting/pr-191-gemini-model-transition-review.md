---
related_documents:
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/migration-plan.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../08-planning/third-expansion-ai-evaluation-result.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
  - ./pr-177-ai-evaluation-review.md
  - ./pr-178-third-expansion-integration-review.md
---

# PR #191 리뷰 트러블슈팅 기록: Gemini 모델 전환과 계약 검증

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#191](https://github.com/team-youngkk/masit-on/pull/191) |
| 작성자 | 이우람 |
| 처리 일자 | 2026-08-14 |
| 범위 | `gemini-3-flash-preview` 기존 이력 보존과 `gemini-3.5-flash-lite` 신규 작업 전환에 대한 마이그레이션·평가 게이트·문서 리뷰 반영 |
| 주요 문제 유형 | 데이터베이스 / 애플리케이션 / 기타 |
| 기존 기록 | [PR #177 AI 평가 리뷰](./pr-177-ai-evaluation-review.md), [PR #178 3차 확장 통합 리뷰](./pr-178-third-expansion-integration-review.md) |

## 2. 리뷰 스레드 처리 결과

| 리뷰 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [migration syntax 1](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780328141), [migration syntax 2](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780330253) | V4 마지막 `ALTER TABLE`의 닫는 괄호·세미콜론 누락 | 데이터베이스 | 수정 필요 | 잘못된 V4 후행 SQL 제거, 완전한 V8 SQL로 이동 | `V8__allow_gemini_3_5_flash_lite_model_version.sql`, Flyway 통합 테스트 |
| [migration history 1](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780328144), [migration history 2](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780330257) | 이미 적용된 V4를 수정하면 checksum 불일치가 발생하므로 새 migration을 사용해야 함 | 데이터베이스 | 수정 필요 | V4 원복, V8 추가, 최신 적용 버전을 8로 검증 | `FlywayMigrationIntegrationTest`, `Expansion3FlywayMigrationIntegrationTest` |
| [golden evaluation](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780330261) | Preview 데이터셋의 모델 버전을 런타임 상수로 덮어쓰면 평가 계약이 사라짐 | 애플리케이션 | 수정 필요 | 기존 데이터셋을 역사적 Preview 기록으로 명시하고, 런타임 모델과 불일치 시 활성화가 `HOLD`인지 별도 게이트로 검증 | `AiExtractionGoldenEvaluationTest` |
| [data contract](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780330264) | `model_version` 계약에 기존 Preview 허용 범위가 드러나지 않음 | 데이터베이스 | 수정 필요 | 신규 Lite와 기존 Preview 허용을 함께 문서화 | `third-expansion-ai-video-data-contract.md` |
| [PRD wording](https://github.com/team-youngkk/masit-on/pull/191#discussion_r3780330268) | Preview 전제의 모델 종료 문구가 남아 있음 | 기타 | 수정 필요 | Preview를 제거하고 모델 종료·quota·장애 정책으로 일반화 | `ai-video-information-extraction.md` |

## 3. 문제 현상과 발생 조건

- 문제 메시지: Flyway가 이미 적용된 V4 변경을 checksum 불일치로 판단할 수 있고, V4의 미완성 SQL은 migration 실행을 중단시킬 수 있다.
- 발생 환경: Java 21, PostgreSQL 17.10 Testcontainers, Flyway 최신 스키마 적용.
- 재현 조건: V4가 이미 적용된 데이터베이스에 변경된 V4를 다시 실행하거나, 빈 데이터베이스에서 V4 후행 `ALTER TABLE`을 실행한다.
- 실제 결과: 모델 제약 변경이 기존 migration 파일에 섞여 적용 이력의 불변성 및 SQL 완결성을 훼손한다. Preview 골든 데이터셋을 현재 런타임 모델과 동일한 평가로 오인할 수 있다.
- 영향 범위: Flyway 적용·JPA 기동 검증, AI 작업의 모델 버전 제약, 평가 릴리스 게이트, 관련 계약·PRD 문서.

## 4. 근본 원인

모델 전환을 기존 V4 DDL에 후행 SQL로 추가하면서 이미 적용된 migration 불변성 규칙과 SQL 종료 구문을 함께 놓쳤다. 또한 기존 Preview 데이터셋의 `runtimeContract`를 현재 모델 상수와 연결하지 않은 채 단순히 기대값만 현재 모델로 바꾸면, 역사적 평가 자산과 운영 모델의 불일치를 검출할 수 없게 된다.

## 5. 확인 및 시도

| 확인 항목 또는 시도 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| V4 원복 및 V8 신규 migration 작성 | 통과 | 기존 migration checksum을 보존하면서 모델 제약만 갱신할 수 있음 |
| 빈 DB 최신 migration 버전·모델 CHECK 조회 | 통과 | V1~V8이 적용되고 Preview·Lite 두 값이 허용됨 |
| 기존 Preview·신규 Lite·미지원 모델 INSERT 검증 | 통과 | 두 계약 값만 허용되고 미지원 값은 거부됨 |
| 기존 Preview 골든 평가와 런타임 계약 불일치 게이트 추가 | 통과 | 현재 런타임 모델과의 불일치에서 `productionActivationAllowed=false`, `HOLD`를 유지함 |
| PRD·데이터 계약·migration 계획의 모델 전환 문구 대조 | 통과 | Preview 전제와 단일 모델 표기를 제거하고 이력 보존 정책을 일치시킴 |

## 6. 최종 해결

- 변경 내용: V4의 미완성·변경 SQL을 제거하고 V8에서 모델 CHECK 제약을 갱신했다. 기존 Preview 작업은 보존하고 신규 Lite 작업을 허용한다.
- 선택 이유: 이미 적용된 Flyway migration을 수정하지 않는 저장소 규칙과 리뷰 요청을 동시에 만족하며, 운영 데이터 이력의 모델 버전도 보존할 수 있다.
- 변경 파일: `V8__allow_gemini_3_5_flash_lite_model_version.sql`, Flyway 통합 테스트 2종, `AiExtractionGoldenEvaluationTest`, 데이터 계약·migration 계획·AI 추출 PRD.
- 고려한 대안: 새 v1.1.0 골든 데이터셋을 만들 수 있으나 이번 PR은 모델 전환과 기존 자산 보존 범위이므로, 기존 데이터셋을 역사적 Preview 기록으로 고정하고 활성화 보류 게이트를 추가했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests "com.masiton.ai.application.AiExtractionGoldenEvaluationTest" --no-daemon --console=plain` | 통과 | 8개 골든 평가·게이트 테스트 통과 |
| `./gradlew.bat test --tests "com.masiton.Expansion3FlywayMigrationIntegrationTest" --no-daemon --console=plain` | 통과 | V1~V8 전진 적용, 기존 Preview·신규 Lite 허용, 미지원 모델 거부 검증 |
| `./gradlew.bat test --tests "com.masiton.FlywayMigrationIntegrationTest" --no-daemon --console=plain` | 통과 | 전체 migration history에 V8과 기준 데이터 적용 검증 |
| `./gradlew.bat clean build --no-daemon --console=plain` | 시간 초과 | 5분 제한 내 완료되지 않아 성공으로 판정하지 않음. 관련 타깃 테스트는 모두 통과 |

## 8. 재발 방지와 다음 확인

- 재발 방지: 적용된 Flyway 파일은 수정하지 않고 항상 새 버전 migration으로 계약 변경을 추가한다. 모델 전환 시 기존 평가 자산의 모델 버전과 런타임 계약 불일치를 별도 릴리스 게이트로 검증한다.
- 다음 확인: 실제 Gemini quota·응답 품질·운영 billing 조건은 테스트 환경에서 검증하지 않으며, 모델 활성화 전 운영 점검에서 별도로 확인한다.

## 9. 투입 전후 비교 지표

| 지표 | 투입 전 기준값 | 측정 방법·기간 | 배포 후 측정값 | 비교 결과 | 담당자/확인 시점/이슈 |
|---|---|---|---|---|---|
| Flyway 적용 성공 여부 | V4 수정안에 syntax·checksum 위험 존재 | Testcontainers 빈 DB와 V3→V4 전진 적용 | 측정 예정 | V8 분리와 전체 빌드 후 확인 | PR #191 / 병합 전 |
| AI 모델 전환 운영 품질·quota | 운영 측정값 없음 | 실제 Gemini 호출·quota 모니터링 | 측정 예정 | 이번 PR 범위 밖 | WS-15 / 운영 활성화 전 별도 확인 |

## 10. 남은 사항

- 전체 `clean build`는 로컬 5분 제한 내 완료되지 않았다. 병합 전 CI 전체 빌드 결과를 추가 확인한다.
- 실제 Gemini 운영 quota·응답 품질은 이 PR의 코드 검증 대상이 아니며, 활성화 전 별도 운영 점검이 필요하다.
