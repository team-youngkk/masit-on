---
related_documents:
  - README.md
  - ../04-product/prd/participation/user-submission-report.md
  - ../05-specs/api/participation/submission-report-api.md
  - ../05-specs/api/common/second-expansion-contract.md
  - ../02-analysis/second-expansion-domain-boundaries.md
  - ../06-architecture/module-boundaries.md
  - ../06-architecture/package-structure.md
  - ../06-architecture/transaction-boundaries.md
---

# PR #134 리뷰 트러블슈팅: 사용자 제보·신고 접수

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#134 사용자 제보·신고 접수](https://github.com/team-youngkk/masit-on/pull/134) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-05 |
| 범위 | 트랜잭션 원자성, 상세 404, 멱등 본문 해시, 회원 화면 페이지·대상 표시, 교차 도메인 조회 경계, 악성 HTML 입력 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | `docs/troubleshooting`에서 제보·신고, 멱등 해시, 대상 Projection, HTML 입력과 같은 기존 기록을 검색했으나 직접 재사용할 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [CUD 트랜잭션과 동시성 원자성](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3712294863) | 접수 public 메서드에 트랜잭션 경계를 두고 회원 잠금부터 저장까지 원자화 | 데이터베이스 | 수정 필요 | 두 생성 메서드에 `@Transactional`을 추가하고, 동시성 통합 테스트가 멱등 서비스 바깥에서 Application Port를 직접 호출하게 바꿨다. | 관련 단위 테스트 통과, PostgreSQL 통합 테스트는 로컬 Docker 미실행으로 미실행 상태이며 CI에서 재확인 |
| [상세 식별자 404 계약](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3712294869) | 잘못된 상세 경로 식별자도 자원별 404 반환 | 애플리케이션 | 수정 필요 | 생성 신고 대상의 잘못된 식별자는 400을 유지하고, 회원 상세 경로만 `SUBMISSION_NOT_FOUND`·`REPORT_NOT_FOUND` 404로 분리했다. | `ParticipationControllerApiTest` 통과 |
| [재귀 canonicalize](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3712294875) | 중첩 Map 키도 안정적으로 정렬 | 애플리케이션 | 수정 필요 | Map은 모든 깊이에서 키 정렬, List는 순서 보존, 원시값은 타입 보존하는 재귀 canonicalize로 통합했다. | 중첩 키 순서가 다른 본문의 해시 동일 테스트 통과 |
| [JSON null 타입 보존](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3717041911) | JSON `null`과 문자열 `"null"`을 다른 본문으로 해시 | 애플리케이션 | 수정 필요 | `String.valueOf` 변환을 제거하고 null을 JSON null로 직렬화해 SHA-256 입력 타입을 보존했다. | 두 본문의 요청 해시 불일치 테스트 통과 |
| [회원 목록 페이지 이동](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3717041916) | 첫 20건 이후 이전·다음 이동과 필터 변경 시 1페이지 초기화 | 애플리케이션 | 수정 필요 | 응답 페이지 메타데이터를 상태로 유지하고 이전·다음 동작을 추가했다. 종류·상태 변경 시 페이지를 1로 되돌린다. | 프론트 상태 전이 테스트, 전체 82건과 typecheck 통과 |
| [대상·신고 유형 표시](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3717041918) | 제보 후보와 신고 대상 ID·유형을 목록·상세에서 식별 | 애플리케이션 | 수정 필요 | 목록에 대상 요약을, 상세에 제보 후보 필드 또는 신고 대상 식별자·신고 유형을 표시했다. | 제보·신고 요약·상세 변환 테스트 통과 |
| [교차 도메인 조회 경계](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3717064368) | Participation persistence의 타 도메인 테이블·공개 판정 제거 | 애플리케이션 | 수정 필요 | `ParticipationTargetReader` Port를 추가하고 다중 도메인 읽기 SQL을 `orchestration.infrastructure.query` Adapter로 이동했다. | ArchitectureTest 10건 통과 |
| [블록리스트 우회 입력](https://github.com/team-youngkk/masit-on/pull/134#discussion_r3717064371) | 이벤트 핸들러·SVG·data URI 등 우회 가능한 HTML 입력 차단 | 애플리케이션 | 수정 필요 | 텍스트 계약에 맞춰 `<`·`>` 자체와 제어 문자를 거부하는 허용 경계로 변경했다. | `img onerror`, `svg onload`, 기존 script·제어 문자 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 기능 오류 코드는 `INVALID_IDENTIFIER`, `IDEMPOTENCY_KEY_REUSED`, `DAILY_REQUEST_LIMIT_EXCEEDED` 등이며 컴파일 오류는 없었다.
- 발생 환경: `feature/t-112-user-submission-report`, Java 21·Spring Boot 4.1.0, Next.js 16.2.11·TypeScript 7.0.2
- 재현 조건: 트랜잭션 밖 직접 동시 접수, 잘못된 상세 ID, 같은 멱등 키의 null 타입 변경, 21건 이상 목록, 같은 유형의 복수 요청, 이벤트 핸들러 HTML 입력, Participation persistence의 raw SQL 확인
- 실제 결과: 잠금 범위가 서비스 계약에 없고, 상세가 400이 될 수 있으며, 서로 다른 JSON이 같은 해시가 됐다. 회원 화면은 첫 페이지와 대상 유형만 표시했고, 교차 도메인 공개 판정과 입력 블록리스트가 Participation 내부에 있었다.
- 기대 결과: Application public 메서드 단위 원자성, 자원별 404, 타입·중첩 순서를 보존한 멱등 해시, 모든 페이지와 대상 정보 접근, orchestration 조회 경계, 우회하기 어려운 텍스트 입력 정책
- 영향 범위: 제보·신고 접수 정합성, 멱등 재시도, 회원 조회 완결성, 도메인 결합도, 저장 입력 안전성

## 4. 근본 원인

접수 동작이 공통 멱등 서비스의 외부 트랜잭션에 우연히 의존해 Application Service 자체의 트랜잭션 계약이 빠졌다. Controller는 외부 식별자와 내부 UUID 파싱을 같은 400 변환으로 처리했고, 멱등 해시 전처리에서 `String.valueOf`와 최상위 `TreeMap`만 사용해 JSON 타입과 중첩 순서를 잃었다.

프론트는 목록 API의 기본 인자만 사용하고 페이지 응답 메타데이터와 대상별 응답 필드를 화면 상태에 연결하지 않았다. 대상 존재 검사는 기능 구현 편의를 위해 Participation 저장소에 합쳐져 다중 도메인 읽기 Projection의 책임 위치를 벗어났다. 입력 검사는 알려진 스크립트 문자열 두 개만 찾는 블록리스트여서 다른 HTML 실행 지점을 포괄하지 못했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API·공통 계약과 Controller 오류 변환 대조 | 없는 요청·타 회원 요청은 기능별 404로 확정 | 상세 경로 파싱만 기능별 404로 분리 |
| 멱등 canonical body의 실제 Java 값 대조 | JSON null과 문자열 `null`이 모두 같은 문자열이 됨 | JSON 원시 타입을 유지하고 Map을 재귀 정렬 |
| 프론트 목록 호출과 응답 타입 대조 | API는 페이지 객체와 대상 필드를 주지만 화면이 사용하지 않음 | 페이지 상태·이동과 대상 표현을 연결 |
| 도메인 경계·패키지 구조와 raw SQL 위치 대조 | 다중 도메인 Projection은 orchestration 인프라 책임 | Participation 소유 Port와 orchestration Adapter로 분리 |
| 기존 script 정규식에 `img onerror`, `svg onload` 입력 | 정규식을 통과함 | 계약이 HTML 텍스트 처리이므로 꺾쇠 자체를 거부 |
| PostgreSQL 통합 테스트 실행 | Docker 환경을 찾지 못해 Testcontainers 초기화 실패 | 원격 CI에서 Docker 기반 통합 테스트 재검증 |

## 6. 최종 해결

- 변경 내용: CUD 트랜잭션, 자원별 상세 404, 타입 보존 재귀 멱등 해시를 적용했다.
- 변경 내용: 회원 목록 페이지 이동과 대상별 목록·상세 표현을 추가했다.
- 변경 내용: 대상 공개 조회를 `ParticipationTargetReader`와 orchestration Query Adapter로 분리하고 HTML 꺾쇠·제어 문자를 거부했다.
- 선택 이유: 확정된 API·아키텍처 계약을 바꾸지 않고 각 책임 경계에서 최소 수정할 수 있다.
- 변경 파일: `ParticipationService`, `ParticipationController`, `ParticipationTargetReader`, `ParticipationTargetQueryAdapter`, `JdbcParticipationStore`, `ParticipationRequestScreen`, 참여 기능 단위·API·통합 테스트, 이 문서와 인덱스
- 고려한 대안: 각 원본 도메인 Port를 순차 호출하면 Visit 공개 조건 조합이 분산되므로, 문서가 허용하는 orchestration 읽기 Projection을 채택했다. HTML 이스케이프 저장은 소비자마다 중복 디코딩 위험이 있어 입력 거부 계약을 선택했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `gradlew.bat test --tests ParticipationServiceTest --tests ParticipationControllerApiTest --tests ArchitectureTest --rerun-tasks --no-daemon` | 통과, 24건 | 입력·404·멱등 해시·서비스 흐름과 아키텍처 경계 |
| `npm.cmd test` | 통과, 82건 | 페이지 상태 초기화, 대상 표현과 기존 프론트 회귀 |
| `npm.cmd run typecheck` | 통과 | TypeScript 정적 타입 |
| `gradlew.bat test --tests ParticipationPostgreSqlIntegrationTest --rerun-tasks --no-daemon` | 미실행 | Docker Desktop 미실행으로 Testcontainers 초기화 실패, 테스트 본문 진입 전 종료 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: JSON null 타입·중첩 Map 순서, HTML 이벤트 벡터, 잘못된 상세 식별자, 페이지 필터 초기화, 대상 표시 회귀 테스트를 추가했다.
- 재발 방지: 동시성 통합 테스트가 공통 멱등 트랜잭션에 기대지 않고 `ParticipationUseCase`를 직접 동시 호출한다.
- 다음 확인: PR #134 GitHub Actions에서 PostgreSQL Testcontainers 동시 접수 시나리오와 전체 빌드를 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 멱등 null 타입 구분 | JSON null과 문자열 `null` 해시 동일 | Controller API 회귀 테스트 | 해시 불일치 | 계약 위반 차단 | 김인안, PR #134 검증 시점 |
| 20건 초과 목록 접근 | 1페이지만 접근 가능 | 프론트 페이지 상태 전이 테스트 | 이전·다음 이동 가능 | 조회 흐름 완결 | 김인안, PR #134 검증 시점 |
| 직접 동시 접수 상한 | 기존 테스트가 공통 멱등 트랜잭션 안에서만 측정 | 6개 직접 동시 호출 후 저장 수 확인 | CI 확인 예정 | 로컬 Docker 제약으로 비교 대기 | 김인안, PR #134 CI |

## 10. 남은 사항

- 로컬 Docker Desktop이 실행되지 않아 PostgreSQL 통합 테스트는 GitHub Actions 결과로 보완한다.
