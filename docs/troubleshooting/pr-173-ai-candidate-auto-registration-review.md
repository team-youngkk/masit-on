---
related_documents:
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../08-planning/third-expansion-implementation-plan.md
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
| 범위 | AI 후보 검증·외부 참조 확인·대표 음식 카테고리 매핑·태그 정책·원자 커밋·방문 근거 판정·리뷰 회귀 테스트 |
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
| [#3757225686](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757225686) | 부정·의문·가정 문구의 부분 문자열 오판정 | 애플리케이션 | 수정 필요 | 방문 후보를 정규화한 문장 전체 패턴과 부정·의문·가정 선행 차단으로 판정 | 부정·의문·가정 변형 회귀 테스트 |
| [#3757225692](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757225692) | 계약상 `TEXT_RANGE` 방문 근거 차단 | 애플리케이션 | 수정 필요 | Validator와 orchestration에서 유효한 `TEXT_RANGE`와 source hash를 허용 | Validator·orchestration TEXT_RANGE 회귀 테스트 |
| [#3757306474](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757306474) | 고정 허용 목록이 자연스러운 실제 방문 문구를 과도하게 차단 | 애플리케이션 | 수정 필요 | 정규화된 문장 전체가 실제 방문 동사로 끝나는지 확인하고 부정·의문·추정 맥락을 우선 차단 | `제가 직접 방문했습니다.`, `제가 이 식당에 다녀왔습니다`, `이곳을 방문했어요` 회귀 테스트 |
| [#3757426739](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757426739) | 실제 방문 주체가 영상 채널인지 확인 | 애플리케이션 | 수정 필요 | 1인칭 주체·장소 대상 맥락이 없는 문구와 제3자 주어를 차단하고 Provider 지침을 채널 관점으로 제한 | `친구가 방문했습니다`, `다른 사람이 다녀왔습니다`, `유명인이 직접 방문했습니다` 회귀 테스트 |
| [#3757607240](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757607240) | `SUBJECT_PARTICLE`의 다절 문장 과차단 | 애플리케이션 | 수정 필요 | 동사 앞 전체 문자열 검색을 제거하고 방문 직전 주어·검증된 맛집 대상만 확인 | `VerifyAiContentCandidateServiceTest` 다절 문장 경계 |
| [#3757610978](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757610978) | 방문 근거 대상이 검증된 맛집인지 확인 | 애플리케이션 | 수정 필요 | 검증된 Restaurant 이름을 방문 문구에 연결하고 제3자·일반 장소 문구를 차단 | `친구가 맛집을 방문했습니다`, `회사에 다녀왔습니다` 회귀 테스트 |
| [#3757641992](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757641992) | 동사 앞 `안`·`못` 부정문 차단 | 애플리케이션 | 수정 필요 | 방문 동사 직전 부정어 lookahead를 추가해 긍정 패턴보다 먼저 차단 | 6개 선행 부정어 회귀 사례 |
| [#3757642002](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3757642002) | 방문 검증 실패 사유의 감사 이력 보존 | 애플리케이션 | 수정 필요 | Optional 빈 결과 대신 `VerificationResult`에 실패 사유를 담아 `VISIT_EVIDENCE_REQUIRED`를 Snapshot에 전달 | orchestration·Processor 사유 전파 테스트 |
| `PRRT_kwDOTf2xKc6YN7TR` | `들렀다` 계열의 `안/못` 부정 방문 문구 차단 | 애플리케이션 | 수정 필요 | `들렀`를 선행 부정·차단 문맥에도 포함하고 `안 들렀습니다`·`못 들렀어요` 회귀 테스트 추가 | `VerifyAiContentCandidateServiceTest` |
| `PRRT_kwDOTf2xKc6YN7TV` | 영문 `i/we` 주어의 토큰 경계 보장 | 애플리케이션 | 수정 필요 | `i(?=\s|$)`·`we(?=\s|$)`로 접두어 오인식을 차단하고 `impostor`·`weird` 회귀 테스트 추가 | `VerifyAiContentCandidateServiceTest` |
| `PRRT_kwDOTf2xKc6YN7TZ` | 부정·대조된 맛집명을 방문 대상처럼 인정하는 문제 | 애플리케이션 | 수정 필요 | 검증된 맛집명 뒤의 `아닌/아니라/제외`와 앞뒤 대상 경계를 확인하고 부정·대조 문장 회귀 테스트 추가 | `VerifyAiContentCandidateServiceTest` |
| `PRRT_kwDOTf2xKc6YN7Tc` | 태그만 누락된 `PARTIAL` 결과의 자동 확정 | 애플리케이션 | 수정 필요 | `PARTIAL` 자체가 아닌 필수 필드·메뉴 누락만 본문을 차단하도록 변경하고 Validator·Processor 회귀 테스트 추가 | `AiCandidateValidatorTest`, `AiExtractionResultProcessorServiceTest` |
| [#3758551384](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3758551384) | 맛집명과 방문 동사의 동일 절·목적어 관계 확인 | 애플리케이션 | 수정 필요 | 방문 동사 직전 목적어 구간에 검증된 맛집명이 직접 연결된 경우만 확정하고 앞 절 언급은 차단 | [인라인 답글](https://github.com/team-youngkk/masit-on/pull/173#discussion_r3758673151)·해결 완료 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `AUTO_MERGE` replacement 누락, `result_completeness` CHECK 위반, `FOOD_CATEGORY_REQUIRED`, 외부 검증 불일치 오판정, 방문 근거 미확정 상태의 정식 등록, 계약상 `TEXT_RANGE`의 자동 차단.
- 발생 환경: `feature/t-159-ai-candidate-auto-registration`, Spring Boot 4.1.0, PostgreSQL V4 AI 스키마, PR #173 미해결 리뷰 상태.
- 재현 조건: 냉면 같은 메뉴 표현 입력, 주소가 다른 지점의 접두어인 경우, 한글 신규 태그, `missingFields=["tag"]`, 유효하지 않은 완결성 값, 정식 등록 중 예외, `visitEvidence`가 언급·추천·추정·부정·의문·가정 문구이거나 `UNKNOWN` 근거인 경우, 유효한 `TEXT_RANGE` 근거인 경우, 동사 앞 `안`·`못`·`들렀`, 영문 `impostor/weird` 접두어, 부정·대조된 맛집명, 검증된 맛집과 연결되지 않은 장소·제3자 방문 문구인 경우.
- 실제 결과: 메뉴 표현이 카테고리 이름으로 직접 조회됐고, 장소 동일성에 부분 문자열이 사용됐다. 태그 한 건의 거부가 전체 후보를 거부했으며, 차단 경로에서 `AUTO_MERGE`가 저장될 수 있었다. 외부 검증과 등록 예외가 같은 application catch 경계에 섞였고, Processor가 방문 근거를 검증하지 않은 채 `true`를 전달했다. 이후 추가된 방문 판정은 긍정 문구를 부분 문자열로 매칭해 부정·의문 문구를 통과시켰고, Validator와 orchestration이 계약상 `TEXT_RANGE`를 차단했다. 첫 보완의 고정 허용 목록은 반대로 `제가 직접 방문했습니다.` 같은 자연스러운 문장까지 차단했다. 후속 문장 패턴은 임의의 주어를 허용해 제3자의 방문을 현재 영상 채널의 방문으로 오인할 수 있었다. 최신 구현은 `들렀` 부정형·영문 접두어 오인식·부정된 맛집명 대상·태그 전용 `PARTIAL`을 별도로 차단하지 못했고, 맛집명이 앞 절에서만 언급되어도 최종 방문 동사의 대상처럼 처리했다.
- 기대 결과: 대표 카테고리와 외부 참조는 각 도메인 경계에서 검증하고, 태그 실패는 태그에 국한하며, 정식 등록·후속 작업 완료는 하나의 DB 트랜잭션으로 원자 처리해야 한다.
- 영향 범위: 잘못된 음식 카테고리 등록, 다른 지점 오인 등록, 태그·VisitTag 불일치, 작업 stuck 또는 재시도 불가, 도메인 의존성 규칙 위반, 부정문·타 장소 방문의 정식 관계 오등록, 방문 실패 사유 감사성 저하.

## 4. 근본 원인

초기 구현이 AI application 서비스 안에서 Kakao 장소·지역·카테고리와 YouTube 검증을 직접 조합하면서, 메뉴 표현과 대표 카테고리를 같은 문자열로 취급했다. 장소 비교도 정규화된 완전 일치가 아니라 양방향 `contains`라 주소 접두어가 다른 지점으로 통과할 수 있었다. 태그 정책은 한글 라벨을 ASCII suffix로 변환하는 방식만 허용해 한글 신규 태그를 구조적으로 탈락시켰다.

또한 검증 결과의 본문 결정과 개별 태그 결정을 같은 reject 조건으로 묶었고, 차단 경로가 태그의 `AUTO_MERGE` 결정을 그대로 저장했다. Provider payload의 완결성 문자열을 검증 결과와 별도로 원본 저장했으며, 외부 검증부터 정식 등록 커밋까지의 예외 경계를 포괄적으로 잡아 등록 결함을 입력 차단으로 오분류할 수 있었다. 방문 근거 후보와 구간은 Processor에서 orchestration으로 전달되지 않았고, 검증 서비스에 직접 테스트가 없어 장소·YouTube 검증이 실제 계약대로 조합되는지 확인할 수 없었다. 추가 구현은 긍정 방문 표현을 단순 `contains`로 검사해 부정·의문·가정 표현의 앞부분과 충돌했고, 방문 근거 유형을 TIMESTAMP로만 제한해 확정 데이터 계약과 구현 계획의 `TIMESTAMP/TEXT_RANGE` 허용 범위를 어겼다. 첫 수정은 고정된 긍정 문구 목록으로 좁혀 자연스러운 조사·어미 변형을 누락시켰고, 두 번째 수정은 정규식 접두부를 임의의 주어에 개방해 방문 주체를 현재 채널과 연결하지 못했다.

## 5. 확인 및 시도

최신 미해결 지적의 원인은 방문 동사 앞 전체 문자열에서 `SUBJECT_PARTICLE`를 검색해 다절 1인칭 문장을 과차단한 것, 위치 조사만 확인해 검증되지 않은 일반 대상을 허용한 것, 방문 동사 뒤 부정 패턴만 사용해 `안/못 방문`을 놓친 것, 그리고 `Optional.empty()` 하나로 외부 기준정보 불일치와 방문 근거 부족을 표현한 것이다. 이번 4건은 `들렀` 어간과 영문 주어 토큰 경계가 허술하고, 검증된 맛집명이 부정·대조된 대상인지 확인하지 않으며, `PARTIAL` 상태 자체를 선택 태그 누락과 필수값 누락에 동일하게 적용한 문제였다. 추가로 방문 동사 직전 목적어를 제한하지 않아 앞 절의 맛집명 언급이 실제 방문 증거로 오인될 수 있었다.

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #173 미해결 review thread 35건과 현재 head 대조 | 기존 30건에 후속 5건과 방문 대상 관계 지적 1건이 추가된 상태로 확인 | 원인별 단일 수정으로 묶고 원문 스레드에 개별 답글 작성·해결 |
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
  - `visitEvidence` 후보의 값·신뢰도·근거 구간을 orchestration 입력에 포함하고, 명시적 실제 방문 문구·유효한 TIMESTAMP/TEXT_RANGE를 확인한 `VerificationResult.verified`만 반환하도록 변경했다. 언급·추천·추정·UNKNOWN 근거는 정식 등록 전에 차단한다.
  - Provider 지시에도 실제 방문 주장과 단순 언급·추천·추정을 구분하도록 명시하고, orchestration 직접 테스트와 Processor의 방문 근거 미확정 정식 등록 차단 회귀를 추가했다.
  - 방문 근거 판정을 고정 문구 목록이 아닌 정규화된 문장 전체 패턴으로 바꾸고, 실제 방문 동사로 끝나는 자연스러운 조사·어미 변형을 허용했다. 부정·의문·추정·추천 맥락과 질문·감탄 부호는 긍정 패턴보다 먼저 차단해 `직접 방문하지 않았습니다`, `직접 방문했을까요?`를 통과시키지 않는다.
  - 방문 문구에 1인칭 주체 또는 명시적 장소 대상·직접 방문 맥락을 요구하고, 주어 조사가 붙은 제3자 문구를 차단했다. Provider 지침도 현재 YouTube 채널 제작자의 1인칭 또는 장소 대상이 있는 암묵적 1인칭 주장만 출력하도록 제한했다.
  - 방문 문구의 대상은 Kakao 검증을 통과한 Restaurant 이름과 연결하고, `친구가 맛집을 방문했습니다`·`회사에 다녀왔습니다`처럼 제3자·일반 대상을 자동 확정하지 않도록 제한했다.
  - `SUBJECT_PARTICLE`를 동사 앞 전체 구간에서 검색하지 않고, 검증된 맛집명 앞의 직접 주어와 방문 직전 주어만 확인해 다절 1인칭 문장을 과차단하지 않도록 했다.
  - 방문 동사 직전 `안`·`못`을 `들러`·`들렀`를 포함한 lookahead로 차단해 선행 부정문이 긍정 방문 패턴으로 통과하지 않도록 했다.
  - 영문 1인칭 주어 `i`·`we`에는 공백/문장 끝 토큰 경계를 적용해 `impostor`·`weird` 같은 접두어 오인식을 차단했다.
  - 검증된 맛집명 앞뒤의 `아닌`·`아니라`·`제외` 문맥과 조사·방문동사 경계를 확인해 다른 대상을 방문한 문장을 등록 근거로 사용하지 않도록 했다.
  - 방문 동사 직전 목적어 구간에서만 검증된 맛집명을 인정하고, 맛집명 뒤에는 조사와 제한된 방문 수식어만 허용해 `소개받고`, `언급한 뒤`, `아니고` 뒤의 다른 장소 방문을 차단했다. 같은 문장에 맛집명이 반복되면 방문 동사에 가장 가까운 이름을 기준으로 판정한다.
  - `PARTIAL`은 결과 상태로만 기록하고, 필수 필드·메뉴 누락일 때만 본문 자동 등록을 차단해 태그만 누락된 후보는 외부 검증·정식 등록까지 진행하도록 했다.
  - 외부 검증 결과를 `VerificationResult`로 바꿔 성공 콘텐츠와 실패 사유를 구분하고, 방문 근거 실패는 `VISIT_EVIDENCE_REQUIRED`로 Snapshot 감사 이력에 전달했다. 성공 콘텐츠의 항상 `true`였던 `visitEvidenceConfirmed` accessor와 Processor의 dead branch는 제거했다.
  - AI 후보 Validator와 orchestration 모두 유효한 `TEXT_RANGE`(`startOffset`·`endOffset`·`sourceHash`)를 방문 근거로 허용하고, 범위·source hash가 불완전한 근거는 차단했다.
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
| `.\gradlew.bat test --no-daemon --console=plain` | 환경상 실패 | 682 tests completed, 69 failures, 2 skipped; 실패 항목은 모두 Docker/Testcontainers 초기화 오류이며 focused·Architecture 테스트는 통과 |
| [백엔드 CI 실행](https://github.com/team-youngkk/masit-on/actions/runs/31477603456) | 통과 | 970 tests, 0 failures, 2 skipped; PostgreSQL 원자성 회귀 포함 |
| 이전 백엔드·프론트엔드 CI 실행 | 통과 | 커밋 `8d45b6a` 기준 백엔드 전체 빌드·테스트와 프론트엔드 타입 검사·프로덕션 빌드 성공; 추가 방문 근거·orchestration 테스트 포함 |
| [최신 백엔드·프론트엔드 CI 실행](https://github.com/team-youngkk/masit-on/actions/runs/31486331945) | 통과 | 커밋 `d3d2b62` 기준 자연어 방문 문구·1인칭/장소 대상 맥락·제3자 주어 차단과 TIMESTAMP/TEXT_RANGE 검증을 포함한 백엔드 전체 빌드·테스트 및 프론트엔드 타입 검사·프로덕션 빌드 성공 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |
| `.\gradlew.bat test --tests com.masiton.orchestration.application.VerifyAiContentCandidateServiceTest --tests com.masiton.ai.application.AiExtractionResultProcessorServiceTest --tests com.masiton.architecture.ArchitectureTest --no-daemon --console=plain` | 통과 | 현재 커밋의 방문 대상·선행 부정어·다절 문장·실패 사유 전파와 아키텍처 경계 |
| `gh workflow run ci.yml --ref feature/t-159-ai-candidate-auto-registration` | 환경상 미검증 | `workflow_dispatch`에서 백엔드 job을 skip하는 조건으로 실행되어 현재 커밋의 원격 전체 CI 결과는 생성되지 않음 |
| 추가 focused Gradle 테스트: `AiCandidateValidatorTest`, `VerifyAiContentCandidateServiceTest`, `AiExtractionResultProcessorServiceTest`, `GeminiHttpVideoExtractionAdapterTest` | 통과 | 부정·의문·가정 문구 차단, TIMESTAMP/TEXT_RANGE 근거 검증, 방문 근거 전달과 정식 등록 미호출 회귀 |
| 추가 `VerifyAiContentCandidateServiceTest` 자연어 변형 시나리오 | 통과 | 조사·어미가 포함된 실제 방문 주장 3종과 부정·의문·추정 문구의 판정 |
| 추가 `VerifyAiContentCandidateServiceTest` 방문 주체 시나리오 | 통과 | 주체·장소 맥락 없는 `방문함`과 친구·다른 사람·유명인 제3자 주어의 차단 |
| 추가 `AiExtractionResultProcessorServiceTest`·`GeminiHttpVideoExtractionAdapterTest` 회귀 | 통과 | 직접 방문 fixture와 채널 제작자 관점 Provider 지침 유지 |
| 추가 `VerifyAiContentCandidateServiceTest` 방문 대상·선행 부정어·다절 문장 회귀 | 통과 | 검증된 맛집 대상만 허용, `안/못` 선행 부정 차단, 다절 1인칭 문장 과차단 방지 |
| `VerificationResult` 실패 사유 전파 테스트 | 통과 | 방문 근거 부족을 `VISIT_EVIDENCE_REQUIRED`로 Snapshot 커밋 명령에 전달 |
| `.\gradlew.bat test --tests com.masiton.orchestration.application.VerifyAiContentCandidateServiceTest --tests com.masiton.ai.application.AiCandidateValidatorTest --tests com.masiton.ai.application.AiExtractionResultProcessorServiceTest` | 통과 | 50 tests, 0 failures; `들렀` 부정형·영문 주어 토큰·부정 대상 문맥·태그 전용 `PARTIAL` 자동 확정 회귀 |
| `.\gradlew.bat test --tests com.masiton.orchestration.application.VerifyAiContentCandidateServiceTest --no-daemon --console=plain` | 통과 | 41 tests, 0 failures; 방문 동사 직전 목적어 관계와 리뷰 제시 다절 문장 3건을 포함한 기존 방문 판정 회귀 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 메뉴 표현→대표 카테고리, 접두어 주소, 한글 신규 태그, 선택 태그 누락, invalid completeness, 차단 태그 결정, 외부 예외 경계, 실제 방문 문구·근거 구간·방문 대상·선행 부정어·실패 사유 게이트를 회귀 테스트로 고정했다. PostgreSQL 원자성은 Testcontainers 테스트로 실제 제약과 트랜잭션을 확인했다.
- 다음 확인: 최신 방문 대상 관계 스레드까지 inline 답글·해결 처리를 완료했다. 원격 전체 CI는 `workflow_dispatch` job 조건으로 실행되지 않았고 Docker/Testcontainers 통합 검증은 기존 성공 CI 기록과 로컬 환경 제약을 함께 보존한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| PR 리뷰 미해결 스레드 | 20건 | PR #173 스레드 API, 2026-08-11 | 0건 | 20건 모두 원문 답글·해결 처리 완료 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드 | 2건 | PR #173 스레드 API, 2026-08-11 | 0건 | 방문 근거·orchestration 테스트 2건을 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(2차) | 2건 | PR #173 스레드 API, 2026-08-11 | 0건 | 부정·의문 문구와 TEXT_RANGE 계약 불일치 2건을 수정·검증 후 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(3차) | 1건 | PR #173 스레드 API, 2026-08-11 | 0건 | 자연스러운 실제 방문 문구를 과도하게 차단한 고정 목록을 문장 패턴으로 교체하고 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(4차) | 1건 | PR #173 스레드 API, 2026-08-11 | 0건 | 임의의 주어를 허용한 방문 패턴을 1인칭·장소 대상 맥락으로 제한하고 제3자 주어를 차단한 뒤 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(5차) | 4건 | PR #173 스레드 API, 2026-08-11 | 0건 | 다절 문장·방문 대상·선행 부정어·실패 사유 4건을 수정·검증 후 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(6차) | 4건 | PR #173 스레드 API, 2026-08-11 | 0건 | `들렀` 부정형·영문 토큰·부정 대상·태그 전용 `PARTIAL` 4건을 수정·검증 후 원문 답글·해결 처리 | PR 작성자, PR #173 |
| 추가 리뷰 미해결 스레드(7차) | 1건 | PR #173 스레드 API, 2026-08-11 | 0건 | 앞 절에서만 언급된 맛집이 최종 방문 대상처럼 처리되는 문제를 목적어 구간 제한과 3개 회귀 테스트로 수정·검증하고 답글·해결 | PR 작성자, PR #173 |
| 전체 리뷰 미해결 스레드 | 35건 | PR #173 스레드 API, 2026-08-11 | 0건 | 기존·추가 리뷰 35건에 원문 답글을 남기고 모두 해결 처리 | PR 작성자, PR #173 |
| orchestration 직접 테스트 스위트 | 0개 | PR #173 추가 리뷰 시점 | 1개 | 장소·YouTube·방문 근거 게이트 시나리오를 직접 검증 | PR #173 |
| PostgreSQL 원자성 회귀 실행 | 0회 | Testcontainers 통합 테스트 | 통과 | 백엔드 CI에서 원자성 회귀 포함 전체 테스트 성공 확인 | CI runs 31477603456, 31484115720, 31486331945 |
| 메뉴 표현의 대표 카테고리 매핑 | 직접 이름 조회 | 매핑 단위 테스트 | 냉면→한식 등 매핑 통과 | 표현과 저장 카테고리 책임 분리 | WS-04, PR #173 |

## 10. 남은 사항

- 로컬 Docker Desktop 데몬은 꺼져 있어 동일 테스트의 로컬 실행은 불가했지만, Docker가 제공되는 백엔드 CI에서 통과했다.
- 코드·문서 수정과 focused 테스트를 커밋 `28ebb34` 및 후속 기록 커밋으로 원격 브랜치에 반영했고, 최신 리뷰 스레드에도 인라인 답글을 남겨 해결 처리했다. 원격 전체 CI는 workflow 조건상 실행되지 않았고 로컬 Docker Desktop 데몬 부재로 Testcontainers의 로컬 재실행도 환경 제약으로 남는다.
