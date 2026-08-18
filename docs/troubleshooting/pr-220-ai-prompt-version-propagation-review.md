---
related_documents:
  - ../00-overview/scope.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../08-planning/third-expansion-ai-candidate-loss-analysis.md
  - ../08-planning/third-expansion-scope-and-terminology.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ./pr-204-ai-prompt-contract-review.md
  - ./pr-191-gemini-model-transition-review.md
---

# PR #220 리뷰 트러블슈팅: Prompt 버전 상향의 문서 전파 누락 재발과 검사 자동화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#220 AI 영상 추출의 맛집 자동 저장 경로를 실측으로 완성한다](https://github.com/team-youngkk/masit-on/pull/220) |
| 작성자 | 양성훈 (`tjdgns0618`) |
| 처리 일자 | 2026-08-17 |
| 범위 | 최초 미해결 인라인 리뷰 스레드 2건의 재현, 현재 Prompt 계약 P7 문서 전파, 전파 누락 회귀 검사 추가 |
| 주 문제 유형 | 기타(계약·추적 문서 정합성) |
| 기존 기록 | [PR #204 Prompt P2 계약 동기화](./pr-204-ai-prompt-contract-review.md)가 P1→P2 상향에서 같은 누락을 다뤘다. 그 기록의 "현재 계약과 역사적 이력을 구분해 서술한다" 원칙을 재사용했으나, 문서 관행만으로는 재발을 막지 못해 이번에 검사로 승격했다. [PR #191](./pr-191-gemini-model-transition-review.md)의 현재 운영 계약·역사적 평가 자산 분리 원칙도 그대로 따랐다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [계획·범위 용어 문서의 P7 전파](https://github.com/team-youngkk/masit-on/pull/220#discussion_r3793868238) | 범위 용어 57·74·180행과 구현 계획 119행, Task 분해 40행의 현재 계약을 P7로 통일하고 이력은 P1~P6으로 명시 | 기타 | 수정 필요 | 해당 5개 위치를 현재 P7·기존 P1~P6으로 정정 | `AiExtractionContract.PROMPT_VERSION=P7`과 대조. 같은 문서의 `P0~P6` 실행 체크포인트는 Prompt 버전이 아니므로 제외 |
| [평가·출시 게이트의 P7 전파](https://github.com/team-youngkk/masit-on/pull/220#discussion_r3793868243) | 평가 전략 확정 표의 현재 버전을 P7/S1로 바꾸고 P1~P6은 이력으로 보존 | 기타 | 수정 필요 | 평가 전략 375·378행과 함께 누락돼 있던 선행 상태 검토 171·264행도 정정 | 문서 전수 검색으로 남은 현재 계약 표기가 없음을 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 정적 계약 불일치다.
- 발생 환경: PR #220 `feature/ws-15-ai-auto-registration`, Java 21, Spring Boot 4.1.0.
- 재현 조건: Prompt 버전을 올린 뒤 현재 계약을 서술하는 문서를 전수 검색하지 않는다.
- 실제 결과: 런타임 상수·설정·API 예시·ADR은 P7인데 계획·범위·평가·선행 검토 문서 9개 위치는 P6으로 남았다.
- 기대 결과: 현재 운영 계약을 서술하는 모든 문서가 실행 상수와 같은 버전을 가리키고, 과거 버전은 `기존`으로만 등장한다.
- 영향 범위: `BR-AIEXTRACT-004` 버전별 재현성, 120건 평가와 출시 게이트 판정 기준, 후속 구현·검증 담당자의 작업 기준.

## 4. 근본 원인

Prompt 버전을 서술하는 문서가 14개 파일에 흩어져 있고 표현이 제각각(`현재 Prompt P7`, `현재 P7/S1`, `Prompt \`P7\`·Schema \`S1\``)이라, 상향할 때마다 사람이 전수 검색으로만 동기화해 왔다. 이번에는 검색 결과를 필터링·절단해 확인하면서 계획 문서군을 놓쳤다.

더 근본적으로는 PR #204에서 같은 누락이 발생했을 때 재발 방지를 "문서에 현재와 역사적 이력의 경계를 명시한다"는 서술 관행으로만 남기고 검증 수단을 만들지 않았다. 관행은 다음 상향(P6→P7)에서 그대로 재발했다. 즉 근본 원인은 개별 누락이 아니라 **현재 계약 버전에 기계 검증이 없었다는 것**이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL로 review thread 2건의 상태·인라인 문맥 조회 | 2건 모두 미해결·outdated 아님 | 현재 코드·문서에서 재현해 수정 필요로 분류 |
| `docs/troubleshooting`에서 Prompt 버전·문서 동기화 기록 검색 | PR #204가 P1→P2 상향에서 동일 누락을 기록 | 같은 구분 원칙 재사용, 재발 방지 수단은 강화 필요로 판단 |
| `docs`·`src` 전체에서 `P6` 검색 후 현재 계약/역사 이력/실행 체크포인트로 분류 | 현재 계약 표기 9곳, 역사 서술 6곳, `P0~P6` 체크포인트 9곳 | 현재 계약 9곳만 수정. 체크포인트와 역사 서술은 의미가 달라 보존 |
| 손실 분석 12절의 "Prompt는 P6 유지" 문장 재확인 | 태그 수정 자체는 버전 상향이 불필요했으나 같은 PR의 13절이 P7로 올려 현재 계약으로 오독될 수 있음 | 문장 범위를 "이 수정만으로는 올리지 않는다"로 한정하고 13절을 참조 |
| 문서 전파 검사 테스트 추가 후 정상 상태로 실행 | 3건 통과 | 검사가 현재 상태를 통과함을 확인 |
| 범위 용어 문서 57행을 의도적으로 P6으로 되돌려 재실행 | 실패. 위반 파일과 문구(`docs/08-planning/third-expansion-scope-and-terminology.md: 현재 Prompt P6`)를 지목 | 검사가 실제 회귀를 잡음을 확인하고 원상 복구 |
| 문서만 바꾼 뒤 `./gradlew test` 실행 | `:test UP-TO-DATE`로 건너뜀 | 문서는 test task의 선언된 입력이 아니다. CI는 `clean build`라 항상 재실행되므로 병합 게이트에서는 유효하다. 8절에 한계로 기록 |

## 6. 최종 해결

- 변경 내용: 계획·범위·평가·선행 검토 문서 9개 위치의 현재 계약을 P7로 정정하고 이력을 P1~P6으로 명시했다. 손실 분석 12절의 버전 유지 문장 범위를 한정했다. 현재 Prompt 버전 선언을 실행 상수와 대조하는 회귀 검사를 추가했다.
- 선택 이유: 리뷰가 지적한 불일치를 해소하면서, 같은 누락이 세 번째로 반복되지 않도록 사람의 전수 검색을 검사로 대체하는 최소 변경이다. API·DB 계약과 마이그레이션은 건드리지 않는다.
- 변경 파일: `docs/08-planning/third-expansion-scope-and-terminology.md`, `docs/08-planning/third-expansion-evaluation-strategy.md`, `docs/08-planning/third-expansion-implementation-plan.md`, `docs/08-planning/third-expansion-task-breakdown.md`, `docs/08-planning/third-expansion-baseline-review.md`, `docs/08-planning/third-expansion-ai-candidate-loss-analysis.md`, `src/test/java/com/masiton/ai/application/AiPromptVersionDocumentationContractTest.java`, 이 기록과 트러블슈팅 인덱스.
- 고려한 대안: 모든 `P6` 문자열의 일괄 치환은 `P0~P6` 실행 체크포인트와 역사적 서술을 훼손하므로 채택하지 않았다. `docs`를 `test` task의 입력으로 선언하면 문서만 바꿔도 전체 테스트가 재실행돼 문서 PR 비용이 커지므로 채택하지 않고, CI의 `clean build`를 병합 게이트로 사용한다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests "com.masiton.ai.application.AiPromptVersionDocumentationContractTest"` | 통과 | 현재 계약 선언 14개 문서, API 예시, `application.yml`이 모두 P7 |
| 범위 용어 문서를 P6으로 되돌린 뒤 `./gradlew cleanTest test --tests "...AiPromptVersionDocumentationContractTest"` | 실패(의도) | 위반 파일과 문구를 지목. 확인 후 원상 복구 |
| `./gradlew test --tests "com.masiton.ai.*" --tests "com.masiton.orchestration.*"` | 통과 | 문서 수정과 검사 추가가 기존 AI·오케스트레이션 동작에 회귀를 만들지 않음 |
| `docs`·`src` 전체 `P6` 재검색과 분류 | 통과 | 남은 `P6`은 역사적 서술 또는 `P0~P6` 실행 체크포인트뿐 |
| GitHub Actions `백엔드 빌드·테스트` | 확인 예정 | 원격 `clean build`에서 전체 회귀와 이 검사를 재확인한다 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `AiPromptVersionDocumentationContractTest`를 추가했다. `현재` 뒤에 오는 Prompt 버전 토큰, API 응답 예시의 `promptVersion`, `application.yml`의 `prompt-version`을 `AiExtractionContract.PROMPT_VERSION`과 대조한다. 세 번째 검사가 검사 목록 자체의 누락을 막는다. `docs` 전체를 훑어 현재 계약을 선언하는 문서가 목록 밖에 새로 생기면 실패한다.
- 한계: 문서는 Gradle `test` task의 선언된 입력이 아니어서 로컬 증분 실행에서는 `UP-TO-DATE`로 건너뛸 수 있다. 병합을 막는 필수 검사인 CI는 `clean build`를 실행하므로 게이트로서는 유효하다. 로컬에서 문서만 고쳤을 때 확인하려면 `cleanTest`를 함께 실행한다.
- 다음 확인: 원격 CI에서 전체 `clean build` 통과를 확인한다. 담당자는 PR 작성자, 시점은 PR #220 병합 전이다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 현재 계약과 실행 상수가 어긋난 문서 위치 | 9곳 | `AiPromptVersionDocumentationContractTest` 실행 | 0곳 | 리뷰 지적 2건이 가리킨 범위를 포함해 전부 해소 | PR 작성자, PR #220 리뷰 반영 시점 |
| Prompt 버전 전파의 검증 수단 | 없음(사람의 전수 검색) | 회귀 검사 존재 여부와 회귀 주입 시 실패 확인 | 검사 3건. 회귀 주입 시 위반 위치를 지목하며 실패 | 같은 누락의 3회차 재발 경로를 제거 | PR 작성자, PR #220 반영 시점 |
| 같은 유형의 재발 횟수 | 2회(PR #204 P1→P2, PR #220 P6→P7) | 다음 Prompt 상향 PR에서 이 검사의 통과·실패 확인 | 확인 예정 | 확인 예정 | PR 작성자, 다음 Prompt 버전 상향 시점 |
| 미해결 리뷰 스레드 | 2건 | GitHub review thread GraphQL 조회 | 0건 | 원인·변경·검증·기록 답글 후 2건 모두 해결 | PR 작성자, PR #220 리뷰 반영 시점 |

## 10. 남은 사항

- 최초 미해결 리뷰 스레드 2건은 모두 원문 인라인 답글을 남기고 해결 처리했다.
- PR #220 본문의 "검증하지 못한 항목"에 적은 P7 실제 제공자 호출 미실행은 이 리뷰 범위 밖이며 그대로 남아 있다. 로컬 무료 quota가 소진돼 quota window를 조작하지 않고는 재실행할 수 없다.
