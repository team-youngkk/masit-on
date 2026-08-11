---
related_documents:
  - README.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../08-planning/third-expansion-implementation-plan.md
  - ../08-planning/m2-provisioning-record.md
---

# PR #172 리뷰 트러블슈팅: AI Worker 운영·복구 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#172 AI Worker claim·retry·lease 복구 구현](https://github.com/team-youngkk/masit-on/pull/172) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-11 |
| 범위 | 운영 활성화 설정, quota hard stop, 기본·AI 스케줄러 격리, 예외·로그, 임시 입력 키 교체, Worker·PostgreSQL 회귀 테스트 |
| 주 문제 유형 | 애플리케이션·데이터베이스·배포·인프라 |
| 기존 기록 | [PR #170 AI 영상 추출 리뷰](pr-170-ai-video-extraction-review.md)를 확인해 Provider 오류 분류와 비신뢰 입력 경계를 재사용했다. Worker 운영·키 교체·lease 저장 경로는 별도 사건이라 이 문서에 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [저장된 키 ID로 복호화](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755667185) | 활성 키 교체 뒤 과거 키로 암호화된 작업 복구 | 애플리케이션 | 수정 필요 | 키 ID별 키링과 과거 키 configtree 렌더링 추가 | 키 교체·키 누락·암호문 변조 테스트 통과 |
| [운영 Worker 설정 전달](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755685121) | `app-run.sh`에서 활성화·quota 환경변수 전달 | 배포 | 수정 필요 | SSM 값 4개를 읽어 backend 컨테이너에 전달 | `AppRunScriptContractTest` 통과 |
| [키 교체 복구 중복 지적](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755685123) | `encryption_key_id`로 과거 키 선택 | 애플리케이션 | 수정 필요 | 첫 번째 키 교체 수정으로 함께 처리 | 운영 configtree 키링 바인딩 테스트 통과 |
| [quota hard stop 작업 상태](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755685127) | 대기 작업을 실패 상태로 전환하고 Provider 미호출 | 애플리케이션·데이터베이스 | 수정 필요 | `QUEUED` 작업을 `FAILED/QUOTA_HARD_STOP`으로 일괄 전환 | 서비스·PostgreSQL 테스트 통과 |
| [AI 전용 스케줄러](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689207) | 장시간 polling이 전역 스케줄러를 점유하지 않게 격리 | 인프라 | 수정 필요 | 전용 `ThreadPoolTaskScheduler`와 `@Scheduled(scheduler=...)` 적용 | 스케줄러 계약 테스트 통과 |
| [입력·인프라 오류 분리](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689210) | 예상하지 못한 Runtime 오류를 `INPUT`으로 종단 실패시키지 않음 | 애플리케이션 | 수정 필요 | 임시 입력 변조만 `INPUT`, 키·DB 오류는 lease 복구로 분리 | Worker 단위 테스트 통과 |
| [삼킨 예외 로그](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689213) | poll·execute·heartbeat 예외 원인 기록 | 애플리케이션 | 수정 필요 | 예외 객체와 안전한 job ID를 WARN에 포함 | 코드 대조와 AI 회귀 테스트 통과 |
| [임시 입력 cipher 테스트](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689218) | round-trip·키 경계·변조 검증 | 애플리케이션 | 수정 필요 | 정상·키 교체·키 누락·GCM 변조 4건 추가 | `AesGcmTemporaryInputCipherTest` 통과 |
| [Worker Store 쓰기 경로](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689226) | 복잡한 CTE와 attempt INSERT 정상 경로 검증 | 데이터베이스 | 수정 필요 | lease 소진·완료 실패·재시도 실패·quota 집계·무시도 실패 테스트 추가 | PostgreSQL 통합 테스트 통과 |
| [결과 처리기 fail-closed](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689232) | E3-T06 처리기 부재 시 claim·Provider 미호출 검증 | 애플리케이션 | 수정 필요 | `Optional.empty()` 회귀 테스트 추가 | Worker 단위 테스트 통과 |
| [quota 로그 억제](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755689237) | 5초마다 반복되는 warning·hard stop WARN 억제 | 애플리케이션 | 수정 필요 | 상태 진입 시 1회 기록하고 해제 시 플래그 초기화 | 반복 poll 로그 테스트 통과 |
| [기본·AI 스케줄러 실제 분리](https://github.com/team-youngkk/masit-on/pull/172#discussion_r3755886163) | AI 전용 빈으로 인한 Boot 기본 스케줄러 자동 구성 후퇴 방지 | 애플리케이션·인프라 | 수정 필요 | `taskScheduler`를 별도 보장하고 AI 전용 빈과의 공존을 실제 컨텍스트에서 검증 | 스케줄러 단위·통합 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `AIEXTRACT_TEMPORARY_INPUT_UNAVAILABLE`, `INPUT`, `QUOTA_HARD_STOP`; polling 실패 시에는 원인 없는 고정 WARN만 남았다.
- 발생 환경: `feature/t-158-ai-worker-recovery`의 운영 backend 컨테이너와 단일 EC2 내부 Worker
- 재현 조건: 운영 컨테이너 기동, quota 100% 도달, 활성 암호화 키 교체, Provider·DB 런타임 오류, 장시간 Provider 재시도, 결과 처리기 미등록
- 실제 결과: 운영 Worker 활성화 값이 컨테이너에 전달되지 않았고, quota 대기 작업은 `QUEUED`에 머물렀다. AI 전용 `TaskScheduler`만 등록한 1차 수정은 Boot 기본 스케줄러 자동 구성을 후퇴시켜 이름을 지정하지 않은 예약 작업까지 AI 단일 스레드에 합칠 수 있었다. 키·DB 오류가 `INPUT` 종단 실패로 섞이고 예외 원인이 로그에서 사라졌으며, 암호화와 Store 쓰기 정상 경로 일부는 테스트되지 않았다.
- 기대 결과: 운영 설정으로 Worker를 명시 활성화하고, quota·lease·키 교체·인프라 장애를 정규화된 상태와 격리된 실행 경계로 처리해야 한다. 보고한 정상·예외·경계는 자동화 테스트로 재현돼야 한다.
- 영향 범위: AI 작업 상태와 관리자 수동 등록 fallback, 임시 입력 복구·삭제, 다른 도메인의 보존·보안 스케줄러, 운영 장애 진단

## 4. 근본 원인

Worker 애플리케이션 설정을 추가하면서 운영 컨테이너의 SSM→호스트 환경→Docker 전달 경로와 전역 `@Scheduled` 실행 모델을 함께 추적하지 않았다. 특히 AI 전용 `TaskScheduler` 타입 빈을 추가하면 Spring Boot의 기본 `taskScheduler` 자동 구성이 조건부 후퇴한다는 컨텍스트 수준 상호작용을 리플렉션 테스트가 포착하지 못했다. quota pre-check도 Provider 미호출만 확인해 관리자에게 노출되는 영속 작업 상태를 갱신하지 않았다.

임시 입력은 `encryption_key_id`를 저장했지만 cipher는 현재 활성 키만 보유·선택했다. 또한 `execute()`의 포괄적인 `RuntimeException` catch가 암호문 변조 같은 입력 오류, 키 설정 누락, 결과 저장 DB 오류를 모두 `INPUT`으로 합쳤다. Store 통합 테스트가 경합·stale owner에 집중돼 성공 INSERT와 복잡한 CTE가 실제 PostgreSQL 제약을 통과하는지 확인하지 못했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub 미해결 스레드와 PR head 대조 | 11개 스레드 모두 현재 코드에서 재현 또는 검증 공백 확인 | 전부 `수정 필요`로 분류 |
| ADR-EXT-003·AI 데이터 계약·구현 계획 대조 | lease 복구, quota 실패 상태, 임시 입력 키 ID, 장애 격리를 요구 | 기존 계약 안에서 수정 |
| `app-run.sh`·`app-secrets-render.sh`·운영 unit 추적 | Worker 비비밀 설정 4개와 과거 키링 전달 경로 없음 | SSM 전달·prefix 렌더링 추가 |
| 기존 회원 Action Token 키링 확인 | 활성 키는 암호화, 저장된 키 ID는 복호화에 사용하는 패턴 존재 | AI 임시 입력에 같은 선택 규칙 적용 |
| PostgreSQL 통합 테스트 1차 실행 | 빈 YAML `keys: {}`가 Map 바인딩 실패 | 빈 항목 제거, 외부 configtree map만 사용 |
| PostgreSQL 통합 테스트 2차 실행 | 대기→실패 전환에서 `started_at` 누락으로 V4 상태 제약 위반 | Provider 미시도 상태를 `started_at=finished_at`, `attempt_count=0`으로 기록 |
| Spring Boot 실제 컨텍스트에서 `TaskScheduler` 타입 빈 조회 | AI 전용 빈만 두면 Boot 기본 자동 구성이 후퇴할 수 있음 | 이름이 `taskScheduler`인 기본 빈을 직접 보장하고 두 인스턴스의 공존을 통합 테스트로 고정 |

## 6. 최종 해결

- 변경 내용:
  - Worker 운영 활성화·provider/application quota·window 값을 SSM에서 읽어 컨테이너에 전달했다.
  - quota hard stop 시 대기 작업을 `FAILED/QUOTA_HARD_STOP`으로 바꾸고 warning·hard stop 로그는 상태 진입 시 한 번만 기록한다.
  - 이름 없는 기존 예약 작업용 `taskScheduler`와 AI polling용 `aiWorkerTaskScheduler`를 별도 빈으로 보장하고, heartbeat는 기존 전용 executor를 유지했다.
  - 임시 입력 키 ID별 키링과 configtree prefix 렌더링을 추가했다. 신규 암호화는 활성 키, 복호화는 저장 키 ID를 사용한다.
  - 변조 입력은 `INPUT` 종단 실패, 키·DB 등 인프라 오류는 로그 후 lease 만료 복구로 분리했다.
  - poll·execute·heartbeat WARN에 예외를 포함했다.
  - cipher·Worker fail-closed·로그 상태 전이·운영 스크립트·PostgreSQL 쓰기 경로 테스트를 보강했다.
- 선택 이유: API·DB 스키마와 Accepted 계약을 바꾸지 않고, 기존 lease·오류 문자열·Parameter Store·configtree 경계를 완성한다.
- 변경 파일: `deploy/scripts/app-run.sh`, `deploy/scripts/app-secrets-render.sh`, AI Worker application·port·persistence·scheduler 파일, 관련 단위·통합 테스트
- 고려한 대안: 전역 scheduler pool 확대는 다른 `@Scheduled` 추가 때 격리가 다시 깨져 채택하지 않았다. 모든 복호화 오류를 종단 실패시키는 방식은 운영 키 누락을 데이터 오류로 오분류해 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests "com.masiton.ai.infrastructure.persistence.AesGcmTemporaryInputCipherTest" --no-daemon --console=plain` | 통과 | round-trip, 키 교체, 과거 키 누락, GCM 변조 |
| `.\gradlew.bat test --tests "com.masiton.ai.infrastructure.persistence.JdbcAiExtractionWorkerStoreIntegrationTest" --no-daemon --console=plain` | 통과 | claim·lease와 Worker Store 쓰기·quota 정상 경로 |
| `.\gradlew.bat test --tests "com.masiton.ai.*" --tests "com.masiton.common.config.ProdSecretsConfigTreeTest" --tests "com.masiton.deployment.AppRunScriptContractTest" --no-daemon --console=plain` | 통과 | AI 전체 회귀, 운영 configtree 키링, 배포 환경 전달 |
| `.\gradlew.bat test --tests "com.masiton.ai.infrastructure.worker.AiExtractionWorkerSchedulerTest" --tests "com.masiton.ai.infrastructure.worker.AiExtractionWorkerSchedulerIntegrationTest" --rerun-tasks --no-daemon --console=plain` | 통과 | 실제 컨텍스트에 기본·AI 스케줄러가 별도 인스턴스로 함께 존재 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |
| `.\gradlew.bat build --no-daemon --console=plain` | 통과 | 8분 40초, 전체 빌드·테스트·정적 검사 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 운영 설정은 애플리케이션 속성뿐 아니라 SSM→실행 스크립트→컨테이너 전달을 계약 테스트로 고정했다. 키 교체, quota 상태, 처리기 부재, Store CTE 쓰기 경로를 회귀 테스트로 추가했다. 스케줄러 격리는 어노테이션 리플렉션뿐 아니라 실제 Spring Boot 컨텍스트의 빈 이름·인스턴스 분리까지 검증한다.
- 다음 확인: 운영 키 교체 시 미종결 `QUEUED/RUNNING` 작업이 참조하는 키 ID를 확인하고 모두 종료된 뒤 과거 키 Parameter를 폐기한다. 단일 EC2 부하·실제 quota 값과 함께 WS-15 운영자가 E3-T13 운영 게이트에서 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---:|---|---|---|---|
| 활성 키 교체 후 과거 임시 입력 복호화 성공 | 0/1 | 두 키 fixture 단위 테스트 | 1/1 | 로컬 회귀 테스트 기준 복구 성공 | WS-15 운영자, E3-T13 |
| quota 100% 대기 작업 상태 | `QUEUED` 유지 | Worker 서비스·PostgreSQL 통합 테스트 | `FAILED/QUOTA_HARD_STOP` | 수동 등록 fallback 판단 가능한 종단 상태 | WS-15 운영자, E3-T13 |
| 검증된 Store 정상 쓰기 경로 | claim 2종 중심 | PostgreSQL Testcontainers | lease 소진·실패 완료·재시도 기록·무시도 실패·quota 집계 포함 | 리뷰에서 지적된 미검증 SQL 경로 자동화 | PR #172 |
| 미해결 리뷰 스레드 | 12개 | GitHub review thread 재조회 | 0개 | 원인·변경·검증·기록 답글 후 12개 해결 | PR 작성자, PR #172 |

## 10. 남은 사항

- 미해결 리뷰 스레드는 없다.
- 기존 `CHANGES_REQUESTED` review 판정은 리뷰어 재검토가 필요하다. 프런트엔드 CI는 통과했고 백엔드 CI는 이 기록 갱신 시점에 실행 중이다.
