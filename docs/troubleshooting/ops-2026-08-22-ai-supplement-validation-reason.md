---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../04-product/user-flows/third-expansion-user-flows.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../08-planning/third-expansion-browser-verification.md
---

# 운영 작업: AI 보충 입력 검증 실패 사유가 화면에서 사라졌다

## 1. 현상

관리자 AI 작업 `b35829d0-9ca1-4d53-97f0-e49f9e496164`의 `사랑방칼국수` 등록 단위에서 완전한 Kakao URL `https://place.map.kakao.com/7919722`와 보충 사유를 제출했다. 화면에는 새 작업이나 실행 시도가 생성되지 않았고, 등록 단위는 `AUTO_BLOCKED`로 남았으며 다음과 같은 일반 오류만 표시됐다.

`후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다. 최신 작업 상태를 확인해 주세요.`

작업 자체는 `SUCCEEDED / COMPLETE`, 실행 시도 1회였고 Kakao URL 입력값은 잘리지 않았다. 따라서 이 증상은 URL 형식 오류나 전체 작업 재시도 누락이 아니라, 기존 작업의 한 등록 단위에 대한 보충 `CONFIRM` 재검증 실패와 오류 표시 손실로 분류한다.

## 2. 기대 동작과 실제 동작

| 구분 | 기대 동작 | 실제 동작 |
|---|---|---|
| 보충 입력 `CONFIRM` | 같은 작업의 같은 등록 단위만 재검증하고 성공 시 해당 단위를 등록한다 | 새 작업을 만들지 않는 것은 계약대로 동작했다 |
| 기존 차단 사유 | `blockReason`과 복구 경로를 유지한다 | 백엔드는 유지했다 |
| 이번 검증 실패 사유 | 안전한 사유 코드와 traceId를 관리자에게 표시한다 | 백엔드는 후속 실패 사유를 버리고, 프론트는 422 상세를 파싱하지 않아 일반 문구만 표시했다 |
| 다른 등록 단위 | 상태와 등록 결과를 건드리지 않는다 | 단위별 경계는 유지됐다 |

## 3. 근본 원인

`RegistrationUnitCommandService.confirm()`은 보충 URL을 적용한 뒤 `ExecuteRegistrationUnitUseCase`를 호출한다. 장소·카테고리·YouTube·방문 근거·중복 검증 중 하나가 실패해 `RegistrationUnitExecutionResult.confirmed()`가 false가 되면, 계약상 원래 차단 사유를 유지하기 위해 `validationConflict(unit.blockReason())`만 던진다. 이 과정에서 실행 결과의 `result.blockReason()`이 응답에 전달되지 않았다.

프론트 `AiRegistrationUnits.tsx`의 `submitSupplement()`은 같은 422 응답을 받았을 때 등록 실행 경로와 달리 `aiValidationConflictFrom()`을 호출하지 않고 `aiExtractionMessageFor()`만 호출한다. 따라서 사용자는 원래 `PLACE_AMBIGUOUS` 복구 폼과 일반 오류만 보고, 이번 요청이 `VISIT_EVIDENCE_REQUIRED`, `DUPLICATE_CONFLICT`, `CATEGORY_UNRESOLVED`, `EXTERNAL_SERVICE_ERROR` 중 무엇으로 실패했는지 확인할 수 없었다.

## 4. 처리

- `details.blockReason`·`recoveryPaths`·`requiredSupplements`는 기존 계약대로 유지한다.
- 보충 `CONFIRM`의 후속 검증 실패 시 선택 필드 `details.validationFailureReason`에 실제 안전한 사유 코드를 추가한다.
- 프론트는 기존 복구 경로를 유지하면서 `이번 보충 검증 실패` 사유를 별도로 표시한다.
- 보충 실패 시 등록 단위 상태, 정식 Restaurant·Creator·Video·Visit, 감사 이력, 새 AI 작업과 실행 시도를 만들지 않는다.
- API 계약, 사용자 흐름, 이 운영 기록과 백엔드·프론트 회귀 테스트를 같은 변경에서 동기화한다.

## 5. 재발 방지

- 상태 전이를 결정하는 `blockReason`과 한 요청의 재검증 결과를 나타내는 `validationFailureReason`을 같은 필드로 재사용하지 않는다.
- `AIEXTRACT_VALIDATION_CONFLICT`를 처리하는 모든 프론트 경로는 공통 오류 문구로 축약하기 전에 `details`를 파싱한다.
- 보충 입력 실패 테스트는 기존 차단 사유 유지, 실제 후속 실패 사유 노출, 정식 저장 0건, 새 작업 미생성을 함께 확인한다.
- 브라우저 재현 시 작업 ID·요청 경로·HTTP 상태·안전한 오류 코드·traceId·재시도 횟수를 기록하되 외부 응답 원문과 비밀정보는 기록하지 않는다.

## 6. 추적

- GitHub 이슈: [#291](https://github.com/team-youngkk/masit-on/issues/291)
- Workstream: WS-15 AI 영상 정보 추출
- 관련 계약: `FR-AIEXTRACT-003`, `BR-AIEXTRACT-011`, 관리자 AI 영상 추출 API 3.5·3.6절
