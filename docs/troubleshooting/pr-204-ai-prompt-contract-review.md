---
related_documents:
  - ../00-overview/scope.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../08-planning/third-expansion-ai-candidate-loss-analysis.md
  - ./pr-191-gemini-model-transition-review.md
---

# PR #204 리뷰 트러블슈팅: Prompt P2 계약 동기화와 후보 결과 불변성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#204 AI 추출 송신 Schema를 계약에 맞추고 복수 후보를 보존한다](https://github.com/team-youngkk/masit-on/pull/204) |
| 작성자 | 양성훈 (`tjdgns0618`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 최초 미해결 인라인 리뷰 스레드 6건의 재현, Prompt P2 문서 동기화, 후보 결과 불변성·버전 상수 단일화, 회귀 검증 |
| 주 문제 유형 | 애플리케이션 / 기타(계약·추적 문서 정합성) |
| 기존 기록 | [PR #191 Gemini 모델 전환 리뷰](./pr-191-gemini-model-transition-review.md)의 현재 운영 계약과 역사적 평가 자산 분리 원칙을 재사용했다. PR #204 자체 기록은 없어서 이 문서를 새로 작성했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [데이터 계약 Prompt 버전](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781837118) | 현재 계약의 P1 표기를 P2로 바꾸고 기존 P1 이력을 구분 | 기타 | 수정 필요 | 데이터 계약을 현재 P2/S1·기존 P1 이력 보존으로 정정 | `AiExtractionContract.PROMPT_VERSION=P2`, ADR-AI-001 10절과 대조 |
| [API 응답 예시 Prompt 버전](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781837122) | 신규 작업 응답 예시를 P2로 동기화 | 기타 | 수정 필요 | 공통 작업 응답 예시의 `promptVersion`을 `P2`로 정정 | API 문서와 런타임 상수 대조 |
| [P1 실측의 시간 경계](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781837124) | P1 실측이 P2 배포 검증으로 오해되지 않게 구분 | 기타 | 수정 필요 | P2 변경 전 배포본으로 수행한 P1 사전 실측·역사적 증거임을 명시 | 실측 문서 4절의 P1→P2 변경 시점과 대조 |
| [후보 목록의 얕은 불변화](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781862807) | 결과 Map 내부의 `List<Candidate>`도 복사 | 애플리케이션 | 수정 필요 | 후보별 목록을 `List.copyOf`로 복사하는 전용 불변화 함수와 회귀 테스트 추가 | 원본 목록 변경 뒤 값 보존·반환 목록 변경 거부 테스트 통과 |
| [Prompt 상수 중복](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781862811) | 실제 전송 버전과 감사 이력 버전의 단일 출처 확보 | 애플리케이션 | 수정 필요 | Gemini 설정의 모델·Prompt·Schema 상수가 `AiExtractionContract`를 참조하도록 통합 | 컴파일 및 Gemini 설정·Adapter 테스트 통과 |
| [현재 계약 문서 전반 P2 동기화](https://github.com/team-youngkk/masit-on/pull/204#discussion_r3781863576) | 현재 운영 문서·추적표는 P2/S1, 역사적 fixture는 P1로 구분 | 기타 | 수정 필요 | 개요·분석·PRD·와이어프레임·API·데이터·ADR·계획 문서의 현재 계약을 P2로 동기화하고 역사적 P1에 보존 의미 추가 | 문서 검색 결과 남은 P1은 역사적 기록 또는 독립된 자연어 parser 단계·테스트 데이터로 분류 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 정적 계약 불일치와 변경 가능한 반환값 문제다.
- 발생 환경: PR #204 `feature/ws-15-ai-candidate-preservation`, Java 21, Spring Boot 4.1.0.
- 재현 조건:
  - 현재 계약 문서와 `AiExtractionContract.PROMPT_VERSION`을 대조하면 일부 문서·API 예시는 P1, 런타임은 P2를 가리킨다.
  - 변경 가능한 후보 목록을 결과 생성자에 전달하거나 `result.candidates().get(field)`로 받은 목록에 원소를 추가한다.
  - Prompt 버전을 바꿀 때 애플리케이션 계약과 Gemini 설정 상수 중 한 곳만 수정한다.
- 실제 결과: 신규 작업의 기록·전송 버전과 문서가 불일치할 수 있고, 검증 완료 결과의 후보 목록이 생성 이후 바뀔 수 있으며, 두 버전 상수는 서로 다른 값으로 컴파일될 수 있었다.
- 기대 결과: 신규 작업·문서·실제 전송은 P2/S1로 일치하고 P1은 역사적 이력으로만 식별되며, 검증 결과는 생성 이후 불변이고 버전 상수는 한 계약을 참조한다.
- 영향 범위: `BR-AIEXTRACT-004` 재현성, 관리자 API 이해, Gemini 시스템 지시문, 후보 Snapshot 생성 전 검증 결과의 무결성.

## 4. 근본 원인

Prompt 버전을 P2로 올린 변경이 런타임 상수와 ADR 중심으로 적용되고, 같은 현재 계약을 중복 서술하는 API·데이터·분석·계획 문서의 참조 목록을 함께 검색하지 않았다. P1 실측은 변경 전 증거였지만 시간 경계를 문장에 남기지 않아 현재 배포 검증처럼 읽혔다.

코드에서는 `Map<String, Candidate>`를 복수 후보용 `Map<String, List<Candidate>>`로 바꾸면서 기존 바깥 Map 복사 함수만 유지했다. 따라서 새로 생긴 내부 컬렉션 경계에 `List.copyOf`가 적용되지 않았다. 또한 Gemini 설정이 애플리케이션 계약과 같은 모델·Prompt·Schema 값을 독립 상수로 선언해 동기화를 사람의 수정에 의존했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL로 review thread 6건의 상태·인라인 문맥 조회 | 6건 모두 미해결·outdated 아님 | 전부 현재 코드·문서에서 재현해 수정 필요로 분류 |
| `docs/troubleshooting`에서 Gemini 모델·계약 변경 기록 검색 | PR #191에서 역사적 평가 자산과 현재 운영 계약을 구분한 선례 확인 | 같은 구분 원칙 재사용 |
| 현재 계약 문서의 `Prompt P1`, `P1/S1`, `promptVersion: P1` 검색 | 현재 계약 표기와 역사적 fixture·자연어 parser 단계가 섞여 있음 | AI 현재 계약만 P2로 바꾸고 P1 보존 이유를 명시 |
| 후보 Map 구현과 `AiCandidateValidator`의 원본 `ArrayList` 전달 경로 확인 | 바깥 Map만 복사하고 내부 목록 참조를 유지 | 후보별 `List.copyOf`와 회귀 테스트 채택 |
| 버전 상수 사용처 검색 | 시스템 지시는 Gemini 설정, 작업·Snapshot은 애플리케이션 계약을 사용 | Gemini 설정 상수가 애플리케이션 계약을 참조하도록 통합 |
| 전체 `clean build` 실행 | 5분 제한 뒤에도 Testcontainers가 Docker 응답을 기다리며 테스트 프로세스가 종료되지 않음 | 관련 단위 테스트는 별도로 통과했으며 전체 회귀는 CI에서 재확인 |

## 6. 최종 해결

- 변경 내용: 후보 Map의 내부 목록까지 방어적 복사하고 원본 변경·외부 변경을 막는 테스트를 추가했다. Gemini 설정의 모델·Prompt·Schema 상수를 `AiExtractionContract` 참조로 바꿨다. 현재 AI 운영 계약 문서를 P2/S1로 동기화하고 P1 실측·Snapshot·평가 자산은 역사적 이력임을 명시했다.
- 선택 이유: API·DB Schema를 바꾸지 않으면서 검증 결과 무결성과 `BR-AIEXTRACT-004`의 버전별 재현성을 동시에 회복하는 최소 동작 변경이다.
- 변경 파일: `AiCandidateValidationResult.java`, `GeminiProviderProperties.java`, `AiCandidateValidatorTest.java`, 현재 계약을 서술하는 개요·분석·PRD·와이어프레임·API·데이터·ADR·계획 문서, 이 기록과 트러블슈팅 인덱스.
- 고려한 대안: Gemini 설정에 별도 일치 테스트만 추가하면 중복 상수 자체는 남아 다음 변경도 두 곳을 수정해야 하므로 채택하지 않았다. 모든 P1 문자열의 일괄 치환은 자연어 parser 단계와 역사적 평가·Snapshot 이력을 훼손하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests "com.masiton.ai.application.AiCandidateValidatorTest" --tests "com.masiton.ai.infrastructure.provider.config.GeminiProviderPropertiesTest" --tests "com.masiton.ai.infrastructure.provider.config.GeminiHttpVideoExtractionAdapterTest" --no-daemon --console=plain` | 통과 | 후보 내부 목록 불변성, P2 고정 설정, Gemini 송신 Schema·시스템 지시 회귀 없음 |
| `rg -n -i 'Prompt [\x60"]?P1|P1/S1|P1.*Schema \x60S1\x60|promptVersion.*P1' docs src` 결과 분류 | 통과 | 현재 계약 표기는 P2로 동기화됐고 남은 P1은 역사적 AI 자산·기존 이력, 자연어 parser 단계 또는 명시적 과거 데이터 테스트임 |
| `./gradlew.bat clean build --no-daemon --console=plain` | 환경 제한으로 미완료 | 5분 제한 뒤 Testcontainers의 Docker 응답 대기 상태를 `jcmd`로 확인하고 남은 Gradle 프로세스를 종료했다. 코드 실패 출력은 없었으나 전체 성공으로 기록하지 않는다. |
| [GitHub Actions run 31781013533](https://github.com/team-youngkk/masit-on/actions/runs/31781013533) | 통과 | 백엔드 전체 빌드·자동화 테스트와 프론트엔드 빌드·타입 검사 통과 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 후보 결과 내부 컬렉션의 원본 별칭과 외부 변경을 함께 검증하는 테스트를 추가했다. Provider 설정의 버전 표식은 애플리케이션 계약 상수를 참조해 한쪽만 바뀌는 경로를 제거했다. 문서에는 현재 P2와 역사적 P1의 시간 경계를 명시했다.
- 다음 확인: 원격 CI에서 전체 `clean build`를 다시 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 독립 Prompt 버전 상수 정의 | 2곳 | `PROMPT_VERSION` 선언과 참조 검색 | 1개 정본 + Provider 별칭 1개 | Provider가 정본을 참조해 독립 변경 경로 제거 | PR 작성자, PR #204 반영 시점 |
| 후보 내부 목록 변경 가능 경로 | 원본 목록 변경과 반환 목록 `add` 모두 가능 | 회귀 테스트에서 두 변경 경로 실행 | 원본 변경 영향 없음, 반환 목록 변경은 예외 | 검증 결과의 생성 후 불변성 확보 | PR 작성자, PR #204 반영 시점 |
| 미해결 리뷰 스레드 | 6건 | GitHub review thread GraphQL 조회 | 0건 | 원인·변경·검증·기록 답글 후 6건 모두 해결 | PR 작성자, PR #204 리뷰 반영 시점 |

## 10. 남은 사항

- 최초 미해결 리뷰 스레드 6건은 모두 원문 인라인 답글을 남기고 해결 처리했으며, GraphQL 재조회에서 0건을 확인했다.
- 로컬 전체 `clean build`는 7절의 Testcontainers 환경 제약으로 완료하지 못했지만, 원격 GitHub Actions에서 백엔드·프론트엔드 전체 필수 검증이 통과했다.
