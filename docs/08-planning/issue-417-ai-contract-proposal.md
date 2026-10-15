---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - third-expansion-test-matrix.md
---

# 이슈 417 식당별 후보 결속 계약 제안

> 상태: **승인 확보한 구현 설계**. 2026-10-14 사용자가 #417·#414·#419·#421의 소유자 합의 확보를 확인했다. 이 문서는 승인 근거와 구현 범위를 기록한다. 구현 PR에서는 BR·PRD·API·데이터·ADR의 P9/S3 계약 전파를 이 설계와 함께 완료한다.

## 문제와 결정 요청

현재 S2 후보는 필드별 배열이어서 식당 두 곳과 메뉴 다섯 개가 한 Snapshot에 있을 때 메뉴·주소·방문 근거의 소속을 보장하지 못한다. `DecomposeRegistrationUnitsService`가 배열 인덱스를 결속 근거로 삼으면 응답 순서 변화나 결측에서 다른 식당의 값을 연결할 위험이 있다. 승인된 원칙은 **모델이 명시한 식당별 결속 없이는 자동 등록하지 않는다**이다.

## 제안 설계

다음 송신/수신 객체를 S3의 `registrationUnits` 배열로 추가한다. 각 원소의 `candidateUnitId`는 Snapshot 안에서만 유효한 불투명 문자열이다. 모든 식당 관련 후보는 해당 객체 안에만 위치하며 식별자 중복은 거부한다. `candidates`는 태그 전용이다.

```json
{"candidateUnitId":"u1","restaurantName":{"value":"대풍식당","evidence":{}},"address":null,"location":{"value":"인천 강화군 교동면","evidence":{}},"menus":[{"value":"비빔냉면","evidence":{}}],"visitEvidence":{}}
```

- 식당명은 각 단위에 정확히 하나여야 한다. 메뉴는 같은 단위에서 여러 개를 허용한다.
- 주소·location·메뉴·방문 근거가 없으면 그 단위 안에서만 결측으로 남긴다. 다른 단위의 값을 채우거나 배열 순서를 보정 근거로 쓰지 않는다.
- `candidateUnitId` 중복, 단위 밖 식당 관련 값, 필수 구조 오류는 Provider Schema 오류로 Snapshot 저장·자동 등록을 하지 않는다.
- 식당별 결속을 표현하지 못한 새 S3 응답은 Provider `SCHEMA` 오류로 거부한다. S1/S2 이력의 복수 식당은 결속을 추론하지 않고 보류한다.

## 버전·이력 호환 제안

과거 S2 처리로 이미 저장된 다중 식당 등록 단위가 남아 있어도 재등록 시 배열 인덱스를 다시 해석하지 않는다. 새 분해 결과가 없는 이력의 재등록은 기존 422 검증 보류로 응답하고 등록 실행은 0건이며, 예기치 않은 500으로 처리하지 않는다. 이미 완료한 단위의 조회·롤백·감사는 저장된 이력과 식별자를 유지한다.

Prompt P8/Schema S2는 생성 당시 의미로 읽기 전용 보존한다. 새 형식은 Prompt P9/Schema S3로 동시에 승격하고, 작업 멱등 키가 P/S를 포함하는 기존 규칙을 유지한다. S2 Snapshot은 기존 보수적 분해만 허용한다. 즉 단일 식당으로 명백하거나 기존 보존 규칙이 이미 단위를 확정한 경우만 계속 읽고, 복수 식당·결측·순서 의존 사례는 새 추론을 하지 않고 차단한다. 과거 Snapshot을 재작성하거나 S2를 S3로 변환하지 않는다.

## 영향과 소유권

- WS-15: Gemini 요청 Schema·Prompt, 수신 검증, Snapshot 직렬화, `DecomposeRegistrationUnitsService`, 등록 단위 API 표현.
- 데이터 계약: 새 구조의 원문 보존 위치와 등록 단위 결속 감사 값을 결정한다. 새 컬럼이 필요하면 박진영의 Flyway 순서 합의와 새 migration이 필요하다.
- #414와 #416은 S3 단위의 location만 읽고, 결속 규칙을 재정의하지 않는다. #421은 S3 단위만 제한 재검증 대상으로 삼는다.

## 검증 계획

고정 Gemini/WireMock 응답으로 두 식당·다섯 메뉴, 메뉴 순서 변경, 동일 메뉴명, 공통 지역, 한 단위 필드 결측, 잘못된/중복 `candidateUnitId`, S2 이력을 검증한다. 각 경우 메뉴·주소·방문 근거가 다른 식당으로 이동하지 않음과 단위별 원자성을 확인한다. 실제 Gemini 호출은 하지 않는다.

## 위험과 수용 기준

S3의 구조화 출력 준수율 저하가 자동 확정률을 낮출 수 있다. 이 경우에도 오연결보다 보류를 선택하며, 골든 평가에서 오연결 0건, S2 이력 재해석 0건, 호출비용 정책 불변을 확인한 뒤 활성화한다.

## 구현 결과와 검증

- Gemini 송신 Prompt P9와 Schema S3는 식당별 명시적 단위 및 메뉴 배열을 요구한다. 수신 검증은 중복 식별자·단위 밖 후보·추가 중첩 키·잘못된 타입·근거를 거부하며 후보 상한 300과 절단 표시를 유지한다.
- Snapshot의 기존 `candidate_fields` JSON 안에 `registrationUnits`를 보존한다. 새 테이블·컬럼·마이그레이션은 없다. 기존 S1/S2 Snapshot을 재작성하지 않으며 복수 식당의 순서 추론을 중단했다. 단일 식당 이력은 호환한다.
- Worker는 한 단위의 여러 메뉴를 같은 단위의 메뉴 표현으로 전달하고 결측 단위만 독립적으로 차단한다. S3 경로에서는 기존 Snapshot 전체의 단일 등록 경로를 실행하지 않는다.
- BR·PRD·API·데이터·ADR 및 현재 버전을 서술하는 추적·계획 문서에 P9/S3와 P8/S2 역사 보존을 전파했다. [기존 Prompt 전파 누락 기록](../troubleshooting/pr-220-ai-prompt-version-propagation-review.md)의 회귀 방지 원칙을 적용했다.

| 검증 | 결과 |
|---|---|
| `gradlew.bat test --tests 'com.masiton.ai.*' --tests '*DecomposeRegistrationUnitsServiceTest' --tests '*ArchitectureTest' --console=plain` | 352개 통과. AI 전체 회귀·Testcontainers 통합·Gemini WireMock·ArchUnit 포함 |
| `gradlew.bat test --tests '*RegistrationUnitAutoExecutionServiceTest' --tests '*AiPromptVersionDocumentationContractTest' --console=plain` | 통과. 추가한 S3 복수 메뉴 전달·결측 단위 독립 차단, 현재 버전 문서 전파 검사 포함 |
| `gradlew.bat test --tests '*DecomposeRegistrationUnitsServiceTest' --console=plain` | 7개 통과. 단위·메뉴 순서 변경, 동일 메뉴명, 공통 지역, 주소 결측, S2 보수적 해석 포함 |
| Java·테스트 컴파일 | 위 테스트 실행에서 통과 |
| 실제 Kakao·YouTube·Gemini 호출 | 수행하지 않음. 고정 fixture·WireMock·Testcontainers 사용 |

검증 일자는 2026-10-14다. 독립 리뷰 및 후속 #416·#414·#421 통합 검증은 주 작업에서 수행한다. 운영 데이터 재추출·등록·폐기·롤백과 배포, 120건 운영 골든 평가, 실제 준수율 측정은 수행하지 않았다.

## 독립 리뷰 보완

실제 결측과 Provider 선언 결측의 합집합을 보존하고 빈 단위와 불완전 근거는 PARTIAL/차단으로 기록한다. 추가 키와 잘못된 타입은 구조 오류로 유지한다. 메뉴 결측은 Kakao 분류 우선 판정에 위임하고 UNKNOWN 메뉴는 fallback에서 제외한다. 이름·방문 근거 결측은 실행 전에 차단한다. P9/S3 버전과 식당별 결속 계약은 유지하고 고정 후보 및 Mockito 회귀 테스트로 검증한다.

리뷰 보완 검증: 컴파일과 관련 테스트 39건(ArchUnit 포함)이 통과했다. 실제 Provider 호출 없이 고정 후보·Mockito 및 통계 MockMvc/Testcontainers로 검증했다.
