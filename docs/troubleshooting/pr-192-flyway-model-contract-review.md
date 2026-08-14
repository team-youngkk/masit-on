---
related_documents:
  - ../05-specs/data/migration-plan.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/data/data-009-pre-release-migration-consolidation.md
  - ./pr-191-gemini-model-transition-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #192 리뷰 트러블슈팅 기록: V8 통합 범위와 최종 Gemini 모델 계약

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#192](https://github.com/team-youngkk/masit-on/pull/192) |
| 작성자 | 이우람 |
| 처리 일자 | 2026-08-14 |
| 범위 | 운영 전 Flyway V4~V8 통합, V5 제거, 최종 V4의 Gemini 모델 계약 정합성 |
| 주요 문제 유형 | 데이터베이스 / 문서 / 결정 반영 |
| 관련 기록 | [PR #191 Gemini 모델 전환 리뷰](./pr-191-gemini-model-transition-review.md) |

이번 리뷰의 핵심은 “운영 DB에 아직 적용되지 않았으므로 통합해도 된다”는 전제만으로 충분한지와, 통합 대상에 구 V8의 모델 변경까지 포함했는지가 문서에 드러나는지였다. 운영 RDS는 읽기 전용 조회로 `V1`~`V3`만 적용된 것을 확인했고, 개발·공유 DB도 `V1`~`V3`까지만 적용된 상태라는 전제에서 최종 V4를 확정했다.

## 2. 리뷰 스레드 처리 결과

| 리뷰 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [최종 V4의 Preview 호환성](https://github.com/team-youngkk/masit-on/pull/192#discussion_r3780807834) | 구 V8 통합으로 기존 Preview 허용을 제거하면 SQL 의미 보존 및 ADR-DATA-009 10.2 조건과 충돌할 수 있음 | 결정 반영 | Preview를 되돌리지 않고, V8의 모델 계약 변경을 최종 V4에 의도적으로 포함한다는 결정과 근거를 명시 | 운영 RDS `V1`~`V3` 조회, 개발·공유 DB `V1`~`V3` 전제, Lite 단일 CHECK 테스트 |
| [통합 범위·운영 증거 문서화](https://github.com/team-youngkk/masit-on/pull/192#discussion_r3780807841) | migration plan에 V8과 통합 근거·시점이 빠짐 | 수정 필요 | 구 V4~V8 범위, V8의 의도적 계약 변경, PR #191 병합 이후 운영 조회 시점과 결과를 추가 | migration plan 2.4, PR 본문, `git diff --check`, 통합 테스트 22건 |

첫 번째 스레드의 우려처럼 구 V4~V8의 SQL 의미를 모두 동일하게 보존한 것은 아니다. 구 V4~V7은 정규화 본문을 보존했지만, 구 V8의 Preview 허용은 현재 최종 계약인 `gemini-3.5-flash-lite` 단일 허용으로 의도적으로 변경했다. 따라서 이번 처리는 “운영 DB 미적용이면 모든 의미 변경이 자동으로 허용된다”는 판단이 아니라, 미적용 상태를 확인한 뒤 최종 모델 계약을 V4에 포함하기로 한 명시적 결정이다.

## 3. 문제 현상과 발생 조건

- 문제 메시지: 리뷰어가 develop의 V4~V8 결과와 최종 V4를 비교할 때 Preview 허용이 사라져 SQL 의미 보존 및 마이그레이션 통합 규칙 위반으로 해석할 수 있었다.
- 발생 조건: 구 V8의 모델 제약 변경을 통합 범위에 포함하면서, 운영·개발·공유 데이터베이스의 실제 적용 버전과 최종 모델 계약 변경 의도를 PR 본문과 migration plan에 충분히 적지 않은 경우.
- 영향 범위: Flyway 통합 판단, `model_version` 데이터 계약, 리뷰 승인 근거, 향후 운영 적용 전 확인 절차.

## 4. 근본 원인

PR #191의 모델 전환 리뷰 기록은 당시의 V8 분리 전략과 Preview·Lite 동시 허용을 설명하고 있었다. 이후 운영 전 마이그레이션 통합을 준비하면서 모든 대상 DB가 V3 이하라는 사실을 확인했고, V5를 제거하며 구 V8의 모델 계약도 최종 V4에 포함하기로 결정했다. 그러나 이 변경이 단순한 SQL 병합인지, 아니면 미적용 상태를 전제로 한 최종 계약 결정인지가 초기 PR 문서에 충분히 구분되지 않았다.

## 5. 확인 및 시도

| 확인 항목 또는 시도 방법 | 결과 | 판단 |
|---|---|---|
| 운영 RDS `flyway_schema_history` 읽기 전용 조회 | 2026-08-14 11:18 KST, PR #191 병합 이후 조회에서 `V1`~`V3`만 확인, `V4`~`V8` 없음 | 운영에는 통합 대상 migration이 적용되지 않음 |
| 개발·공유 데이터베이스 적용 버전 확인 | 사용자 확인 기준 `V1`~`V3` | 해당 전제를 PR과 문서에 명시하고 병합 전 재확인 대상으로 남김 |
| 최종 V4 모델 CHECK 검증 | Lite는 삽입 성공, Preview와 미지원 모델은 거부 | 최종 계약이 Lite 단일 허용임을 확인 |
| Flyway 통합 테스트 | V1→V4 전진 적용 및 회귀 시나리오 총 22건 통과 | 통합 SQL과 최종 모델 제약이 함께 동작함 |

## 6. 최종 해결

- V5 migration을 제거하고 최종 Flyway 이력을 V1→V4로 정리했다.
- 구 V4~V7의 누적 SQL은 최종 V4에 통합하고, 구 V8의 모델 제약은 Lite 단일 허용으로 최종 V4에 포함했다.
- migration plan에 구 V4~V8 전체 범위, V8의 의도적 계약 변경, PR #191 병합 이후 운영 조회 시점과 결과를 기록했다.
- PR 리뷰 스레드에는 원인, 결정 근거, 변경 파일, 테스트 결과와 이 기록 링크를 답변한 뒤 처리 상태를 정리한다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.Expansion3FlywayMigrationIntegrationTest --tests com.masiton.FlywayMigrationIntegrationTest --no-daemon --console=plain` | 통과 | 관련 통합 테스트 총 22건, V1→V4 적용 및 Lite 단일 CHECK 검증 |
| `git diff --check` | 통과 | 공백·문법 오류 없음 |
| 운영 RDS 읽기 전용 확인 | 통과 | `V1`~`V3`만 존재, `V4`~`V8` 미적용 |

## 8. 남은 사항과 재발 방지

- 실제 운영 배포나 운영 DB migration 적용은 수행하지 않았다.
- 개발·공유 DB의 `V1`~`V3` 상태는 사용자 확인을 근거로 기록했으며, 병합 전 팀의 적용 대상 DB에서 다시 확인해야 한다.
- 이미 운영에 적용된 migration은 수정하지 않는다. 이번처럼 운영 전 통합을 할 때도 SQL 동일성 여부와 별도로 계약 변경이 포함됐는지를 PR·migration plan에 분리해 기록한다.
- PR 병합에는 저장소 규칙에 따른 작성자 외 최소 2명 승인이 남아 있다.

## 9. 투입 전후 비교 지표

| 지표 | 투입 전 기준값 | 측정 방법·기간 | 배포 후 측정값 | 비교 결과 |
|---|---|---|---|---|
| 리뷰 스레드 처리 | V8 범위·계약 변경 근거 불명확 | PR #192 리뷰 반영 후 스레드 상태 확인 | 확인 예정 | 문서·댓글·스레드 상태로 확인 |
| 운영 migration 적용 버전 | V1~V3 확인 | PR #191 병합 이후 2026-08-14 11:18 KST 읽기 전용 조회 | 측정 예정 | V4~V8 미적용 상태에서 통합 진행 |
