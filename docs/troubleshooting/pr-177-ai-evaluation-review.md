---
related_documents:
  - ../08-planning/third-expansion-ai-evaluation-result.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #177 리뷰 트러블슈팅: AI 평가 자산의 증거 범위·분할·Critical 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#177 AI 영상 정보 추출 평가 자산과 HOLD 판정](https://github.com/team-youngkk/masit-on/pull/177) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-12 |
| 범위 | 합성 평가 자산과 실제 Release 품질 증거의 분리, manifest 정합성, 분할 누수, UNKNOWN·Critical 경계 |
| 주 문제 유형 | 테스트·문서 |
| 기존 기록 | [PR #170 AI 영상 추출 리뷰](pr-170-ai-video-extraction-review.md)의 Provider 계약과 [PR #173 자동 등록 리뷰](pr-173-ai-candidate-auto-registration-review.md)의 보수적 자동 확정 경계를 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 |
|---|---|---|---|
| `PRRT_kwDOTf2xKc6YdvUp`, `PRRT_kwDOTf2xKc6Ydwdk` | 생성 fixture와 expected만 비교해 실제 품질 증거처럼 보임 | 수정 필요 | dry-run을 validator 계약 회귀로 한정하고 실제 Provider·인간 판정 전 precision·recall은 `UNMEASURED`로 유지했다. |
| `PRRT_kwDOTf2xKc6YdvUq`, `PRRT_kwDOTf2xKc6Ydwdm` | 120·72/24/24를 테스트에 중복 하드코딩 | 수정 필요 | case 수·분할·평가 ID·그룹·payload variant를 manifest에서 읽어 cases와 대조한다. |
| `PRRT_kwDOTf2xKc6YdwdO` | 주소 UNKNOWN 치환이 location까지 바꿈 | 수정 필요 | 첫 주소 evidence만 치환하고 issue code와 `address` field를 함께 검증한다. |
| `PRRT_kwDOTf2xKc6YdwdS` | groundTruth와 운영 기대값이 평가에 쓰이지 않음 | 수정 필요 | 판정·태그·Critical ground truth를 회귀 검증하고, 사례별 운영 선언은 제거해 `TST-E3-AI-003/004`, `TST-E3-DATA-001` 연결 증거로 이동했다. |
| `PRRT_kwDOTf2xKc6YdwdT` | groupId가 전부 유일하고 inputHash가 실제 hash가 아님 | 수정 필요 | 동일 split·scenario 파생 사례에 의미 그룹을 공유하고, hash 주장을 제거한 `fixtureRef`로 이름과 manifest 계약을 바꿨다. |
| `PRRT_kwDOTf2xKc6YdwdU` | Critical 양성 사례가 없음 | 수정 필요 | 동명 복수 후보·허위 주소 유보 20건을 Critical 위험 정답으로 표시하고 자동 확정되지 않음을 검증한다. 실제 Critical 0건 판정은 계속 미수행이다. |
| `PRRT_kwDOTf2xKc6YdwdV` | Prompt injection 사례가 실제로는 root field Schema 이탈뿐임 | 수정 필요 | 시나리오를 `SCHEMA_DEVIATION_UNEXPECTED_ROOT_FIELD`로 정정하고 11개 payload variant 수를 manifest와 대조한다. |

## 3. 근본 원인과 해결

평가 자산의 세 역할인 정답 Schema, 합성 validator 회귀, 실제 Provider 품질 평가가 한 문서와 테스트 이름에서 충분히 분리되지 않았다. 이 때문에 생성 payload가 expected와 맞는다는 결과가 precision·recall 증거처럼 읽혔고, 사용되지 않는 운영 선언과 유일한 groupId가 추적성만 있는 것처럼 보였다.

수정 후 합성 테스트는 manifest–cases 계약, validator의 후보·결정·태그·사유, UNKNOWN 대상 필드, Critical 위험 자동 차단만 확인한다. 실제 품질 수치는 Provider 출력과 인간 사후 판정이 연결될 때만 산출한다. 원자성·재시도·동시 claim은 평가 사례의 boolean 선언이 아니라 해당 통합 테스트 실행 결과로 판정한다.

## 4. 검증과 재발 방지

| 검증 | 결과 |
|---|---|
| `gradlew.bat test --tests "com.masiton.ai.application.AiExtractionGoldenEvaluationTest"` | 7건 통과, 기본 96건 dry-run |
| `gradlew.bat '-Dmasiton.eval.releaseHoldout=true' test --tests "com.masiton.ai.application.AiExtractionGoldenEvaluationTest"` | 7건 통과, opt-in 120건 dry-run |
| manifest–cases 정합성 | case 120, split 72/24/24, 의미 그룹 36, payload variant 11, Critical 위험 20건을 테스트에서 대조 |

재발 방지를 위해 규모·분할·평가 ID·그룹 수를 테스트에 다시 하드코딩하지 않는다. 새로운 사례가 실제 입력의 변형이면 같은 의미 그룹을 유지하고 한 split 안에 둔다. 합성 dry-run 결과를 precision·recall·Critical 0건 또는 Release 승인 증거로 기록하지 않는다.

## 5. 도입 전후 비교 지표

| 지표 | 도입 전 | 변경 후 | 확인 시점 |
|---|---:|---:|---|
| 의미 있는 분할 그룹 | 0개(120개 유일 ID) | 36개 | PR #177 대상 테스트 |
| Critical 위험 양성 정답 | 0건 | 20건 | PR #177 대상 테스트 |
| 실제 Provider 품질 수치 | 미측정 | 미측정 유지 | Release holdout·인간 승인 시 |

## 6. 남은 사항

실제 Gemini Release holdout 실행, 인간 사후 판정, precision·recall·Critical 0건 승인과 `E3-T13` 운영 게이트는 이번 리뷰 수정 범위에서 수행하지 않았다. 수정 커밋 push, 스레드 답글과 resolve는 별도 사용자 승인 후 진행한다.
