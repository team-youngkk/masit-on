---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-170-ai-video-extraction-review.md
  - ../troubleshooting/pr-173-ai-candidate-auto-registration-review.md
---

# #413 후보 완전성과 처리 성공 표시

## 문제와 원인

Validator는 Provider missingFields에 실제 누락 필수 후보를 합쳐 저장하지만 Processor는 Provider resultCompleteness를 그대로 저장한다. 따라서 COMPLETE 선언과 서버가 계산한 address·location 누락이 같은 Snapshot에 공존할 수 있다. SUCCEEDED는 처리 성공이며 자동 등록 완료를 뜻하지 않는 기존 계약인데 화면에 설명이 없다.

## 변경 범위와 설계

새 처리 결과의 완전성은 Provider가 PARTIAL이면 유지하고, 서버가 계산한 missingFields가 비어 있지 않거나 보존 후보의 UNKNOWN 근거가 있으면 PARTIAL로 정규화한다. 정상 COMPLETE는 유지한다. 다중 후보·외부 검증 차단 자체를 PARTIAL의 근거로 삼지 않으며 COMPLETE와 AUTO_BLOCKED의 독립성을 유지한다. 필수 후보 누락은 기존 Validator의 합집합 계산을 사용한다. 선택 태그만 누락된 PARTIAL은 정상 본문 등록을 계속 허용한다.

상세 화면에 SUCCEEDED는 추출 처리 성공이며 등록 결과는 등록 단위에서 확인한다는 안내를 추가한다. 후보와 등록 단위가 모두 0개이면 별도 안내로 원본 영상과 재추출·수동 등록 경로를 설명한다. 과거 Snapshot을 재작성하지 않으며 조회도 저장값을 유지한다.

## 의존성과 계약

#412 이후 작업을 시작했지만 해당 집계 변경을 복사하지 않아 develop 기준 독립 diff를 유지한다. #417의 S2 배열과 연결 판정은 수정하지 않는다. API·상태 Enum·Provider Schema·Prompt·DB·비용 정책은 변경하지 않는다. PRD 8절의 누락·UNKNOWN은 PARTIAL이고 API의 SUCCEEDED는 등록 완료가 아니라는 기존 정의를 구현한다. #173 기록의 태그 전용 PARTIAL 자동 등록 보존을 회귀 기준으로 사용한다.

## 검증 계획

고정 후보로 COMPLETE 선언과 실제 address·location 누락, 정상 COMPLETE, 후보 0개, 선택 태그만 누락된 PARTIAL, UNKNOWN 근거를 검증한다. Validator·Processor 관련 단위 테스트, 컴파일, 프론트 안내 함수 테스트와 TypeScript 검증을 실행한다. 실제 Kakao·YouTube·Gemini 호출, 운영 재처리·등록·배포는 없다. 결과는 구현 후 기록한다.

## 검증 결과와 제약

### 독립 리뷰의 계약 충돌과 결정 제안

PRD 8절은 일부 등록 단위 차단 시 결과 완전성 PARTIAL을 요구했지만, API 2.1절은 필드 완전성과 등록 판정이 독립이며 COMPLETE+AUTO_BLOCKED를 허용했다. 독립 리뷰에서 이 충돌을 발견했다. 2026-10-14 사용자가 소유자 합의 확보를 확인하여 PRD를 '추출 필수값이 누락되거나 UNKNOWN이면 PARTIAL이며 외부 검증·중복 등 등록 차단은 완전성과 독립'으로 정정했다. 새 서버 계산과 과거 Snapshot 보존을 동시에 유지한다.

- Gradle Wrapper compileJava·compileTestJava 통과. AiCandidateValidatorTest 14건, AiExtractionResultProcessorServiceTest 8건, ArchUnit 11건 통과.
- 프론트 AI 관련 테스트 37건, TypeScript tsc --noEmit, git diff --check 통과.
- 정상 COMPLETE·필수 누락 COMPLETE·후보0개·UNKNOWN 근거·선택 태그만 누락된 PARTIAL의 기존 등록 경로 유지 확인.
- Node는 시스템 24.19.0에서 실행했으며 계약 버전 24.18.0 재실행은 미검증이다. 격리 작업 디렉터리는 기존 루트 node_modules를 Junction으로 참조했다. 새로운 의존성은 추가하지 않았다.
- 저장된 과거 Snapshot과 운영 데이터는 수정하지 않았다. 독립 리뷰·커밋·푸시·PR은 주 에이전트가 이어서 진행한다.
- 독립 리뷰의 PRD/API 충돌 지적을 소유자 합의로 정정했고 재검토에서 P1/P2 지적 없이 대상 Java 테스트·ArchUnit·diff 검사를 다시 통과했다.
