---
status: HOLD
evaluation_date: 2026-08-12
workstream: QUALITY-EVAL
release_decision: HOLD
related_documents:
  - third-expansion-evaluation-strategy.md
  - third-expansion-test-matrix.md
  - third-expansion-task-breakdown.md
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../04-product/traceability.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../07-adr/adr-traceability.md
---

# 맛있온 3차 확장 AI 평가 결과와 출시 보류 기록

## 1. 판정 요약

`E3-T08`의 평가 자산과 판정 형식은 준비됐지만, 이 문서 작성 시점에는 Release holdout 24건의 실제 제공자 실행과 지정 인간 판정자·검증자의 사후 판정이 수행·승인되지 않았다. 따라서 당시 출시 후보 `GOOGLE_GEMINI` / `gemini-3-flash-preview` / Prompt `P1` / Schema `S1`의 판정은 **`HOLD`**다. 이후 운영 모델이 `gemini-3.5-flash-lite`로 변경되었으므로, 이 문서는 이전 후보의 평가 기록으로 보존하며 새 모델은 별도 평가 실행이 필요하다. 아래 목표값은 승인된 기준이지 측정 결과가 아니다.

이 문서의 `HOLD`와 “운영 활성화 금지”는 위에 적은 이전 모델·Prompt·Schema 후보의 출시 판정 기록이다. 후속 정책에서는 장소명 완화 매칭의 런타임 기본값을 `true`로 운영하고, `AI_PLACE_IDENTITY_RELAXED_MATCHING_ENABLED=false`를 긴급 차단값으로 사용한다. 이는 새 모델의 품질이 검증됐다는 뜻이 아니며, 현재 모델의 Release holdout·인간 판정·Critical 오연결 0건 증거는 여전히 별도로 수집해 `E3-T13`에서 품질과 전체 AI 자동 등록의 go/no-go를 판정해야 한다.

현재 자산 점검 기준 120건의 `humanReview.status`는 모두 `PENDING`이다. Release holdout의 합성 validator 기대값은 `AUTO_CONFIRMED` 6건·`AUTO_BLOCKED` 14건·`AUTO_REJECTED` 4건이다. 이 수치는 평가기 배선 검사용 기대 분포일 뿐 실제 제공자 품질 결과나 인간 판정 결과가 아니다. 24건 모두 아직 실제 제공자 실행과 인간 판정 전이다.

| 판정 항목 | Release 목표 | 현재 결과 | 상태 |
|---|---:|---:|---|
| 맛집·주소 후보 precision | 90% 이상 | 미측정 | `HOLD` |
| 방문 근거 recall | 80% 이상 | 미측정 | `HOLD` |
| 자동 등록 precision | 90% 이상 | 미측정 | `HOLD` |
| 태그 후보 precision | 90% 이상 | 미측정 | `HOLD` |
| 태그 후보 recall | 80% 이상 | 미측정 | `HOLD` |
| Critical 오연결 | 0건 | 인간 판정 미수행 | `HOLD` |

평가 자산이나 자동 평가기의 존재를 Release 통과로 해석하지 않는다. 장소명 완화 매칭은 현재 운영 기본값으로 활성화되지만, 전체 AI 자동 등록의 Release 승인과 확장은 `E3-T13`의 최종 go/no-go 및 별도 품질 증거를 따른다.

## 2. 평가 후보와 자산

| 항목 | 고정값·경로 |
|---|---|
| 후보 | Provider `GOOGLE_GEMINI`, 모델 `gemini-3-flash-preview`, Prompt `P1`, Schema `S1` |
| Dataset | `aiextract-golden-v1.0.0`, 총 120건 |
| 분할 | Development 72건·Calibration 24건·Release holdout 24건 |
| Manifest | [`src/test/resources/eval/aiextract-golden-v1.0.0/manifest.json`](../../src/test/resources/eval/aiextract-golden-v1.0.0/manifest.json) |
| 비식별 평가 사례 | [`src/test/resources/eval/aiextract-golden-v1.0.0/cases.json`](../../src/test/resources/eval/aiextract-golden-v1.0.0/cases.json) |
| 프로그램 평가기 | [`AiExtractionGoldenEvaluationTest`](../../src/test/java/com/masiton/ai/application/AiExtractionGoldenEvaluationTest.java) — 합성 S1 validator dry-run과 자산 계약 검사 전용 |

합성 S1 dry-run은 기본 실행에서 Release holdout 사례를 제외하고 명시적 opt-in일 때 120건의 validator 배선만 검사한다. Manifest의 규모·분할·평가 ID와 cases의 일치, validator 결정·후보·태그·사유, `UNKNOWN_EVIDENCE`의 대상 필드, Critical 위험 합성 사례의 자동 확정 차단을 회귀 검증한다. 이 dry-run은 실제 Gemini 출력이나 Release 품질 측정 증거가 아니다. 별도의 실제 Release 실행 결과에는 실행 커밋, Dataset·모델·Prompt·Schema 버전, 분할별 집계, 실패 유형, 인간 판정 기록의 식별자만 남긴다. 원본 URL·자막·근거 본문·Provider 응답 전문·Prompt 전문·개인정보·비밀은 이 문서와 일반 로그에 기록하지 않는다.

## 3. `EVAL-AI-001~010` 증거 경로와 현재 상태

| 평가 ID | 판정 대상 | 준비된 증거 경로 | 현재 상태 |
|---|---|---|---|
| `EVAL-AI-001` | S1 Schema·필수 메타데이터·버전 | Manifest·cases·프로그램 평가기 | 자산 검증 대상, Release `HOLD` |
| `EVAL-AI-002` | 맛집·주소 후보 precision | cases 정답 Schema와 실제 제공자 실행·인간 판정 기록 | Release 측정·승인 미수행 |
| `EVAL-AI-003` | 방문 근거 recall | cases 정답 Schema와 실제 제공자 실행·인간 판정 기록 | Release 측정·승인 미수행 |
| `EVAL-AI-004` | 근거 충실성·Critical 오연결 | 인간 사후 판정 기록과 100% 교차 검토 | 미수행 |
| `EVAL-AI-005` | `UNKNOWN`·보수 후보 처리 | 합성 validator 경계 회귀와 실제 제공자 실행·인간 판정 기록 | Release 측정·승인 미수행 |
| `EVAL-AI-006` | 자동 검증·정식 Entity 원자성 | 합성 validator 회귀와 `TST-E3-AI-003`·`TST-E3-DATA-001` | Release 종합 판정 대기 |
| `EVAL-AI-007` | 자동 확정·차단 precision과 사유 | cases 판정 Schema와 실제 제공자 실행·인간 사후 판정 기록 | Release 측정·승인 미수행 |
| `EVAL-AI-008` | 중복·재시도·재기동·동시 claim | `TST-E3-AI-004` 연결 증거 | `E3-T13` 운영 증거 대기 |
| `EVAL-AI-009` | 공개 영상 입력·timestamp 근거 | 합성 timestamp Schema 회귀·별도 제공자 통합 증거·인간 판정 기록 | 제공자 실행·승인 미수행 |
| `EVAL-AI-010` | 태그 precision·recall·공개 경계 | cases 정답 Schema와 실제 제공자 실행·인간 판정 기록 | Release 측정·승인 미수행 |

Manifest·cases·합성 validator dry-run은 평가 입력·정답 Schema와 회귀 배선을 재현하는 공통 준비 경로다. precision·recall·Critical 0건은 실제 제공자 출력과 인간 판정 없이는 산출하지 않는다. 비동기·원자성·보안·운영 경계는 사례별 선언값이 아니라 [3차 확장 테스트 추적표](third-expansion-test-matrix.md)의 대응 `TST-E3-*` 실행 증거로 판정한다.

## 4. 자동 확정·차단 인간 사후 판정 기록 형식

Release 실행자는 자동 확정과 자동 차단 표본의 `humanReview`를 Dataset 계약의 아래 형식으로 private evaluation store에 기록한다. 문서에는 집계와 비식별 기록 ID만 연결한다.

| 필드 | 기록 규칙 |
|---|---|
| `sampleType` | `AUTO_CONFIRMED_SAMPLE` 또는 `AUTO_BLOCKED_SAMPLE` |
| `status` | 판정 전 `PENDING`, 지정 판정자와 검증자의 합의 완료 후에만 `COMPLETED` |
| `judgeRole` | 실명·개인정보가 아닌 지정 역할 코드 |
| `reason` | 정규화된 판정 사유. 원문·자막·응답·Prompt 전문은 금지 |
| `judgedAt` | 판정 완료 시각. `PENDING`이면 `null` |
| `disagreementStatus` | `UNRESOLVED` 또는 합의 완료 상태. 불일치가 남아 있으면 Release 승인 금지 |

실행 단위 승인 기록은 비식별 `evaluationRunId`, `datasetId`, `caseId`, `split`, 후보 버전 `GOOGLE_GEMINI/gemini-3-flash-preview/P1/S1`, 자동 결정, Critical 오연결 여부, 지정 판정자·검증자 역할, 합의 결과, rollback 증거 ID를 연결한다. 사례 파일의 `humanReview`와 실행 단위 승인이 모두 완료되어야 인간 판정 완료로 집계한다.

`AMBIGUOUS`는 정확도 분모에서 임의로 제외하지 않는다. 평가 전략의 합의 절차에 따라 최종 판정자가 처리 기준을 확정한 뒤 집계를 다시 생성한다.

## 5. Rollback readiness

코드 수준 rollback 경계에는 자동 등록 provenance, 수동 확정 후 원본 등록 Snapshot, AI 소유 태그만 제거하는 구현·회귀 테스트 경로가 존재한다. 그러나 Release 후보의 rollback readiness는 실제 holdout 자동 확정 사례에 대한 사후 판정과 rollback 검증 증거가 없으므로 아직 승인할 수 없다.

Release 승인 전에는 다음을 모두 확인한다.

1. 잘못된 자동 확정 사례를 비공개로 전환하고 해당 AI Snapshot이 만든 태그만 제거한다.
2. 재사용한 Visit처럼 소유권이 모호한 대상은 자동 rollback하지 않고 차단한다.
3. rollback 후 기존 관리자·다른 Snapshot의 태그와 감사 이력이 유지된다.
4. 실패한 rollback은 공개 상태를 부분 변경하지 않고 운영자에게 비식별 오류 코드와 추적 ID를 제공한다.
5. rollback 증거 ID를 실행 단위 승인 기록과 연결한다.

## 6. `HOLD` 해제 조건

다음 조건을 모두 충족하기 전에는 `E3-T08`의 출시 판정을 통과로 바꾸지 않는다.

- Release holdout 24건에 실제 제공자 후보 출력을 연결해 명시적 opt-in으로 실행하고 `EVAL-AI-001~010` 집계를 재현할 수 있다.
- 맛집·주소 precision 90%, 방문 근거 recall 80%, 자동 등록 precision 90%, 태그 precision 90%·recall 80% 이상이며 Critical 오연결이 0건이다.
- 자동 확정·차단 표본과 모든 Critical 후보를 지정 인간 판정자와 검증자가 판정·교차 검토하고 승인한다.
- rollback readiness 검증과 연결 증거가 통과한다.
- `E3-T13`에서 보안·성능·Worker 자원·Gemini Free Tier quota·비용 hard stop·추적표 정합성을 확인하고 운영 활성화 go 판정을 내린다.

하나라도 충족하지 못하면 상태는 `HOLD`를 유지한다. 목표 미달, Critical 오연결, 평가 버전 불일치, 인간 승인 누락, rollback 실패가 있으면 신규 운영 호출과 자동 등록 활성화를 차단하고 수동 등록 fallback을 유지한다.
