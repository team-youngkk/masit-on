---
related_documents:
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../06-architecture/dependency-rules.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #173 리뷰 트러블슈팅: AI 후보 자동 등록 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#173 AI 영상 후보 자동 등록](https://github.com/team-youngkk/masit-on/pull/173) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-11 |
| 범위 | AI 후보 검증·외부 참조 확인·대표 음식 카테고리 매핑·태그 정책·원자 커밋·리뷰 회귀 테스트 |
| 주 문제 유형 | 애플리케이션·데이터베이스 |
| 기존 기록 | [PR #172 AI Worker 운영·복구 경계](pr-172-ai-worker-key-rotation-review.md)를 확인해 AI 데이터 계약과 PostgreSQL 통합 테스트 기록 방식을 재사용했다. 후보 자동 등록의 검증·커밋 경계는 별도 문제이므로 이 문서에 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [#3756452664](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756452664) | 메뉴 표현과 대표 음식 카테고리 매핑 분리 | 애플리케이션 | 수정 필요 | Restaurant 경계에서 냉면·라멘 등 표현을 10개 대표 카테고리로 매핑하고 미지원 표현은 차단 | `ResolveVerifiedRestaurantReferenceServiceTest`의 냉면→한식 |
| [#3756452669](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756452669) | 주소 동일성의 부분 문자열 오인식 제거 | 애플리케이션 | 수정 필요 | 공백 제거 후 완전 일치만 허용하고 다른 지점 주소는 차단 | 월드컵로 1/10 회귀 테스트 |
| [#3756452673](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756452673) | 한글 라벨 신규 태그 자동 등록 경로 | 애플리케이션 | 수정 필요 | 타입 접두어·영문 코드 형식·raw/label 일치 정책으로 한글 라벨 허용 | `AiTagPolicyTest` |
| [#3756452679](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756452679) | PostgreSQL 원자성 통합 테스트 | 데이터베이스 | 수정 필요 | 정식 등록 후 Visit 단계 실패를 주입하는 Testcontainers 테스트 추가 | 로컬 Docker 데몬 미실행으로 미실행, CI 확인 필요 |
| [#3756459571](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459571) | 차단 경로의 AUTO_MERGE 태그 CHECK 위반 | 데이터베이스 | 수정 필요 | 차단 후보의 태그 리뷰는 항상 `AUTO_REJECT`, replacement는 null로 기록 | `AiExtractionResultCommitServiceTest` |
| [#3756459576](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459576) | resultCompleteness 원본값 저장 | 데이터베이스 | 수정 필요 | 허용 목록 외 값은 `PARTIAL`로 정규화해 작업 CHECK를 만족 | Processor 회귀 테스트 |
| [#3756459584](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459584) | ai.application의 타 도메인 Port·Entity 직접 참조 | 애플리케이션 | 수정 필요 | 외부 검증 조합을 orchestration 공개 Port로 이동 | `ArchitectureTest` 통과 |
| [#3756459592](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459592) | 태그 하나의 UNKNOWN 근거가 전체 후보를 거부하는 문제 | 애플리케이션 | 수정 필요 | 본문 후보는 확정하고 해당 태그만 AUTO_REJECT 처리 | Validator 회귀 테스트 |
| [#3756459597](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459597) | 등록 예외까지 CandidateBlocked로 변환하는 포괄 catch | 애플리케이션 | 수정 필요 | URI 변환과 CandidateBlocked만 차단으로 매핑하고 등록·인프라 예외는 재전파 | Processor 집중 테스트 및 코드 대조 |
| [#3756459604](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459604) | 장소명·주소 부분 문자열 동일성 | 애플리케이션 | 수정 필요 | 장소 검증을 Restaurant 경계의 정규화 완전 일치로 통일 | 장소 참조 해석기 회귀 테스트 |
| [#3756459611](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459611) | 사용하지 않는 ValidationResult accessor 제거 | 애플리케이션 | 수정 필요 | 미사용 accessor와 `validatePayload` alias 제거 | 전체 소스 참조 대조 및 컴파일 |
| [#3756459617](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459617) | 사용하지 않는 basicTags 계산 제거 | 애플리케이션 | 수정 필요 | 커밋 명령은 빈 태그로 만들고 권위 있는 `resolveTags` 결과만 사용 | Processor 집중 테스트 |
| [#3756459626](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756459626) | aliases의 미사용 rawLabel 인자 제거 | 애플리케이션 | 수정 필요 | 표시 라벨만 받도록 메서드 시그니처 단순화 | 컴파일 및 태그 정책 테스트 |
| [#3756488262](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488262) | 한글 신규 태그가 항상 거부되는 문제 | 애플리케이션 | 수정 필요 | 타입 접두어·코드 형식·raw/label 일치 정책으로 수정 | `AiTagPolicyTest` |
| [#3756488264](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488264) | 메뉴 표현을 food_category 이름으로 직접 조회 | 애플리케이션 | 수정 필요 | 대표 카테고리 매핑을 Restaurant application 책임으로 이동 | 냉면→한식 회귀 테스트 |
| [#3756488268](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488268) | tag만 누락돼도 전체 후보를 차단하는 문제 | 애플리케이션 | 수정 필요 | 선택 태그 누락은 본문 자동 확정을 막지 않도록 변경 | Validator 선택 태그 누락 테스트 |
| [#3756488271](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488271) | 선택 누락 상태의 차단 사유가 FOOD_CATEGORY_REQUIRED로 고정 | 애플리케이션 | 수정 필요 | 태그만 누락된 경우 차단하지 않고, 메뉴가 없을 때만 카테고리 사유를 사용 | Validator·Processor 회귀 테스트 |
| [#3756488276](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488276) | Kakao/YouTube 검증의 ai.application 직접 호출 | 애플리케이션 | 수정 필요 | Kakao·YouTube 순서를 orchestration 조합 서비스가 소유하도록 변경 | `ArchitectureTest` 및 Processor 테스트 |
| [#3756488281](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488281) | 태그 하나의 거부가 전체 AUTO_REJECTED로 승격 | 애플리케이션 | 수정 필요 | 본문 결정과 태그 리뷰 결정을 분리 | Validator·CommitService 회귀 테스트 |
| [#3756488284](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3756488284) | basicTags 계산의 중복·무효화 | 애플리케이션 | 수정 필요 | 중복 계산 제거, resolveTags 단일 경로 사용 | Processor 집중 테스트 |
| [#3757005280](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757005280) | 실제 방문 근거 검증 결과 없이 `true` 전달 | 애플리케이션 | 수정 필요 | 방문 후보·신뢰도·근거 구간을 orchestration에 전달하고 명시적 방문 문구와 TIMESTAMP를 통과한 결과만 등록 | `VerifyAiContentCandidateServiceTest`, Processor 정식 등록 미호출 회귀 |
| [#3757005283](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757005283) | 외부 검증 orchestration 직접 테스트 부족 | 애플리케이션 | 수정 필요 | 장소 short-circuit, YouTube 식별자·필수 메타데이터, 정상 조합, 방문 근거 차단 시나리오를 직접 검증 | `VerifyAiContentCandidateServiceTest` |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `AUTO_MERGE` replacement 누락, `result_completeness` CHECK 위반, `FOOD_CATEGORY_REQUIRED`, 외부 검증 불일치 오판정, 방문 근거 미확정 상태의 정식 등록.
- 발생 환경: `feature/t-159-ai-candidate-auto-registration`, Spring Boot 4.1.0, PostgreSQL V4 AI 스키마, PR #173 미해결 리뷰 상태.
- 재현 조건: 냉면 같은 메뉴 표현 입력, 주소가 다른 지점의 접두어인 경우, 한글 신규 태그, `missingFields=["tag"]`, 유효하지 않은 완결성 값, 정식 등록 중 예외, `visitEvidence`가 언급·추천·추정 문구이거나 `UNKNOWN` 근거인 경우.
- 실제 결과: 메뉴 표현이 카테고리 이름으로 직접 조회됐고, 장소 동일성에 부분 문자열이 사용됐다. 태그 한 건의 거부가 전체 후보를 거부했으며, 차단 경로에서 `AUTO_MERGE`가 저장될 수 있었다. 외부 검증과 등록 예외가 같은 application catch 경계에 섞였고, Processor가 방문 근거를 검증하지 않은 채 `true`를 전달했다.
- 기대 결과: 대표 카테고리와 외부 참조는 각 도메인 경계에서 검증하고, 태그 실패는 태그에 국한하며, 정식 등록·후속 작업 완료는 하나의 DB 트랜잭션으로 원자 처리해야 한다.
- 영향 범위: 잘못된 음식 카테고리 등록, 다른 지점 오인 등록, 태그·VisitTag 불일치, 작업 stuck 또는 재시도 불가, 도메인 의존성 규칙 위반.

## 4. 근본 원인

초기 구현이 AI application 서비스 안에서 Kakao 장소·지역·카테고리와 YouTube 검증을 직접 조합하면서, 메뉴 표현과 대표 카테고리를 같은 문자열로 취급했다. 장소 비교도 정규화된 완전 일치가 아니라 양방향 `contains`라 주소 접두어가 다른 지점으로 통과할 수 있었다. 태그 정책은 한글 라벨을 ASCII suffix로 변환하는 방식만 허용해 한글 신규 태그를 구조적으로 탈락시켰다.

또한 검증 결과의 본문 결정과 개별 태그 결정을 같은 reject 조건으로 묶었고, 차단 경로가 태그의 `AUTO_MERGE` 결정을 그대로 저장했다. Provider payload의 완결성 문자열을 검증 결과와 별도로 원본 저장했으며, 외부 검증부터 정식 등록 커밋까지의 예외 경계를 포괄적으로 잡아 등록 결함을 입력 차단으로 오분류할 수 있었다. 방문 근거 후보와 구간은 Processor에서 orchestration으로 전달되지 않았고, 검증 서비스에 직접 테스트가 없어 장소·YouTube 검증이 실제 계약대로 조합되는지 확인할 수 없었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #173 미해결 review thread 22건과 현재 head 대조 | 기존 20건에 방문 근거·orchestration 테스트 공백 2건이 추가된 상태로 확인 | 원인별 단일 수정으로 묶고 모든 원문 스레드에 개별 답글 작성 |
| AI 데이터 계약·대표 카테고리 seed·ADR-AI-001 대조 | 대표 카테고리 10종, 외부 검증 조합 경계, AI 작업 CHECK가 계약으로 확정 | 계약 변경 없이 application/orchestration 경계와 매핑 로직 보완 |
| `ArchitectureTest` 실행 | 도메인 간 persistence 직접 의존과 orchestration Entity 소유 금지 통과 | 외부 검증 조합을 orchestration Port로 이동 |
| `docker info` 확인 | Docker Desktop Linux engine pipe에 연결할 수 없음 | 로컬 실행은 실패로 기록하고 Docker가 제공되는 CI에서 재검증 |
| [백엔드 CI 실행](https://github.com/team-youngkk/masit-on/actions/runs/31477603456) | 970 tests, 0 failures, 2 skipped | Testcontainers 원자성 테스트 포함 전체 백엔드 빌드·테스트 통과 |
| `git diff --check` 실행 | 공백 오류 없음 | 커밋 전 정적 확인 완료 |

## 6. 최종 해결

- 변경 내용:
  - Restaurant application 공개 Port를 추가해 장소명·주소 완전 일치, Kakao 장소 필수 필드, 서울 구역, 메뉴 표현→대표 음식 카테고리 매핑을 Restaurant 경계로 이동했다.
  - orchestration 공개 Port가 Restaurant·Video 검증 순서와 최소 검증 결과 조합을 소유하도록 변경했다.
  - 태그 신규 등록 정책을 타입 접두어·ASCII 코드 형식·raw/label 일치 기준으로 바꾸고 한글 라벨 회귀 테스트를 추가했다.
  - 선택 태그 누락과 UNKNOWN 태그는 본문 자동 확정을 막지 않도록 결정 경계를 분리했다.
  - 차단 태그는 항상 `AUTO_REJECT`로 저장하고, 완결성 값은 `COMPLETE`/`PARTIAL` 허용 목록으로 정규화했다.
  - CandidateBlocked만 차단 상태로 변환하고 등록·인프라 Runtime 예외는 재시도 가능하도록 재전파했으며, 미사용 accessor·basicTags·rawLabel 인자를 제거했다.
  - `visitEvidence` 후보의 값·신뢰도·근거 구간을 orchestration 입력에 포함하고, 명시적 실제 방문 문구·유효한 TIMESTAMP를 확인한 `VerifiedContent`만 `visitEvidenceConfirmed=true`로 반환하도록 변경했다. 언급·추천·추정·UNKNOWN 근거는 정식 등록 전에 차단한다.
  - Provider 지시에도 실제 방문 주장과 단순 언급·추천·추정을 구분하도록 명시하고, orchestration 직접 테스트와 Processor의 방문 근거 미확정 정식 등록 차단 회귀를 추가했다.
  - 정식 등록 후 Visit 단계 실패 시 Snapshot·Restaurant·Creator·Video·Visit·VisitTag·attempt/job 완료가 함께 롤백되는 PostgreSQL Testcontainers 회귀 테스트를 추가했다.
- 선택 이유: 기존 API·DB 계약을 바꾸지 않고, 대표 카테고리·외부 참조·태그·원자성의 소유 경계를 분리해 리뷰 지적의 원인을 직접 제거했다.
- 변경 파일: `src/main/java/com/masiton/ai/application/`, `src/main/java/com/masiton/restaurant/application/`, `src/main/java/com/masiton/orchestration/application/`, 관련 `src/test/java/`, `docs/troubleshooting/README.md`, 이 기록 문서
- 고려한 대안: 한글 라벨을 기계적 ASCII transliteration에 강제하는 방식은 라벨 의미를 훼손할 수 있어 채택하지 않았다. 외부 검증을 AI application에 남긴 채 Port만 늘리는 방식은 orchestration 순서 소유 요구를 충족하지 못해 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests com.masiton.ai.application.AiCandidateValidatorTest --tests com.masiton.ai.application.AiTagPolicyTest --tests com.masiton.ai.application.AiExtractionResultCommitServiceTest --tests com.masiton.ai.application.AiExtractionResultProcessorServiceTest --tests com.masiton.restaurant.application.ResolveVerifiedRestaurantReferenceServiceTest --tests com.masiton.architecture.ArchitectureTest --no-daemon --console=plain` | 통과 | 후보 결정·태그 정책·차단 커밋·완결성 정규화·메뉴/주소 매핑·아키텍처 경계 |
| `.\gradlew.bat testClasses --no-daemon --console=plain` | 통과 | PostgreSQL Testcontainers 통합 테스트를 포함한 테스트 소스 컴파일 |
| `.\gradlew.bat test --tests com.masiton.ai.application.AiExtractionResultCommitServicePostgreSqlIntegrationTest --no-daemon --console=plain` | 환경상 실패 | Docker Desktop 데몬 부재로 Testcontainers initializationError; 동일 테스트는 백엔드 CI에서 통과 |
| `docker info --format '{{.ServerVersion}}'` | 실패 | `dockerDesktopLinuxEngine` named pipe에 연결할 수 없음 |
| [백엔드 CI 실행](https://github.com/team-youngkk/masit-on/actions/runs/31477603456) | 통과 | 970 tests, 0 failures, 2 skipped; PostgreSQL 원자성 회귀 포함 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |

| 추가 focused Gradle 테스트: `VerifyAiContentCandidateServiceTest`, `AiExtractionResultProcessorServiceTest`, `GeminiHttpVideoExtractionAdapterTest` | 통과 | 방문 근거 전달·게이트·외부 검증 조합·Provider 지시와 정식 등록 미호출 회귀 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 메뉴 표현→대표 카테고리, 접두어 주소, 한글 신규 태그, 선택 태그 누락, invalid completeness, 차단 태그 결정, 외부 예외 경계, 실제 방문 문구·근거 구간 게이트를 회귀 테스트로 고정했다. PostgreSQL 원자성은 Testcontainers 테스트로 실제 제약과 트랜잭션을 확인했다.
- 다음 확인: 없음. 백엔드 CI에서 `AiExtractionResultCommitServicePostgreSqlIntegrationTest`를 통과했고, 해당 스레드를 해결 처리했다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| PR 리뷰 미해결 스레드 | 20건 | PR #173 스레드 API, 2026-08-11 | 0건 | 20건 모두 원문 답글·해결 처리 완료 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드 | 2건 | PR #173 스레드 API, 2026-08-11 | 0건 | 방문 근거·orchestration 테스트 2건을 원문 답글·해결 처리 | PR 작성자, PR #173 |
| orchestration 직접 테스트 스위트 | 0개 | PR #173 추가 리뷰 시점 | 1개 | 장소·YouTube·방문 근거 게이트 시나리오를 직접 검증 | PR #173 |
| PostgreSQL 원자성 회귀 실행 | 0회 | Testcontainers 통합 테스트 | 1회 통과 | 백엔드 CI에서 970 tests·0 failures 확인 | CI run 31477603456 |
| 메뉴 표현의 대표 카테고리 매핑 | 직접 이름 조회 | 매핑 단위 테스트 | 냉면→한식 등 매핑 통과 | 표현과 저장 카테고리 책임 분리 | WS-04, PR #173 |

## 10. 남은 사항

- 로컬 Docker Desktop 데몬은 꺼져 있어 동일 테스트의 로컬 실행은 불가했지만, Docker가 제공되는 백엔드 CI에서 통과했다.
- 미해결 스레드는 없다. PR #173의 기존 20개와 추가 2개를 포함한 22개 리뷰 스레드에 원문 inline 답글을 남기고 모두 해결 처리했다.
