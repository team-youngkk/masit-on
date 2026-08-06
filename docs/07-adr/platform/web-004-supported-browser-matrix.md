---
id: ADR-WEB-004
title: 지원 브라우저 매트릭스와 iPhone Safari 지원 수준
status: Accepted
decision_date: 2026-08-06
owners:
  - 양성훈
  - 김인안
related_requirements:
  - NFR-COMPATIBILITY-001
  - NFR-TEST-005
related_documents:
  - ../../00-overview/scope.md
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../08-planning/second-expansion-test-matrix.md
  - ../../08-planning/second-expansion-browser-verification.md
  - ../../08-planning/mvp-local-verification.md
  - web-001-frontend-platform.md
  - ../quality/test-001-automation-strategy.md
  - ../adr-traceability.md
supersedes: []
supersedes_decision: RV-NFR-006 지원 브라우저 범위의 iPhone Safari 항목
superseded_by: null
---

# ADR-WEB-004 지원 브라우저 매트릭스와 iPhone Safari 지원 수준

## 1. 상태

Accepted. 2026-08-06 iPhone Safari 검증 수단이 없는 상태를 계약과 일치시키기 위해 [RV-NFR-006](../../01-requirements/non-functional-requirements.md#rv-nfr-006-지원-브라우저-범위)의 iPhone Safari 항목을 개정했다. 이 결정은 [프론트엔드 최종 책임자](../../03-team/ownership.md) 양성훈·김인안이 소유하고, 팀 합의는 이 문서를 포함한 PR의 소유자 2인 승인으로 확정한다. 적용 추적은 [#149](https://github.com/team-youngkk/masit-on/issues/149)에서 한다.

## 2. 결정 요약

지원을 표방하는 브라우저 매트릭스는 **PC Chrome, PC Edge, Android Chrome**의 테스트 시점 최신 및 직전 안정 버전이다. 대표 화면 폭 360px, 390px, 768px, 1280px, 1440px 검증 기준은 바꾸지 않는다.

iPhone Safari는 매트릭스에서 **"검증 없이 지원 표방하지 않음"** 수준으로 낮춘다. 지원 대상으로 표방하지 않고 인수 판정 대상에도 넣지 않되, 의도적으로 차단하거나 기능을 제거하지 않는다. 매트릭스에서 완전히 삭제하지 않고 해제 조건(7절)을 가진 보류 상태로 남긴다.

## 3. 배경

iPhone Safari는 2026-07-27 RV-NFR-006에서 지원 대상으로 확정됐고 [범위](../../00-overview/scope.md), [NFR-COMPATIBILITY-001](../../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성), [ADR-WEB-001](web-001-frontend-platform.md)이 같은 문장을 담고 있다.

그런데 팀에 iPhone 실단말이 없다. MVP 최종 검증에서 확인하지 못했고([로컬 실행·회귀 검증 결과](../../08-planning/mvp-local-verification.md)), 2차 확장 `E2-T15` 최종 검증에서도 같은 이유로 보류했다([2차 확장 테스트 추적표](../../08-planning/second-expansion-test-matrix.md) 5절). 담당자를 배정해도 해소되지 않는 종류의 공백이다.

결과적으로 계약 문서는 지원을 표방하는데 검증 증거는 한 번도 만들어진 적이 없다. [ADR-TEST-001](../quality/test-001-automation-strategy.md)이 "검증하지 못한 항목을 완료로 보고하지 않는다"를 규정하므로, 이 불일치는 검증을 미루는 방식이 아니라 계약을 실제 상태에 맞추는 방식으로 해소해야 한다.

## 4. 결정 문제

검증 수단이 없는 iPhone Safari를 지원 브라우저 매트릭스에서 어떻게 다룰 것인가.

## 5. 고려한 선택지

| 대안 | 판단 | 이유 |
|---|---|---|
| 실단말을 확보한다 | 기각 | 팀 4인에게 iPhone이 없다. 개인 기기 구매나 대여를 완료 조건의 전제로 둘 수 없고, 확보 시점을 약속할 수 없어 계약과 실제의 불일치가 그대로 남는다. |
| 원격 실단말 서비스를 도입한다 | 보류 | 미결정 기술이다. 도구·비용·CI 연동을 정하는 ADR이 선행돼야 하고 초기 월 인프라 예산 15만 원 안에서 판단해야 한다. 이 결정에서 도구를 추가하지 않는다. |
| 매트릭스에서 iPhone Safari를 완전히 삭제한다 | 기각 | 실단말이 생기면 되돌려야 하는데, 삭제하면 되돌릴 근거와 조건이 문서에 남지 않는다. iOS 사용자를 의도적으로 배제한다는 뜻으로도 읽힌다. |
| **지원 표방을 낮추고 해제 조건을 남긴다** | **채택** | 검증하지 않은 것을 지원한다고 적지 않으면서, 기능을 제거하지 않고 되돌릴 조건을 명시한다. 계약 문서 3곳과 실제 검증 증거가 처음으로 일치한다. |

## 6. 계약

### 6.1 지원 표방 매트릭스

- PC Chrome, PC Edge, Android Chrome의 테스트 시점 최신 및 직전 안정 버전
- 대표 화면 폭 360px, 390px, 768px, 1280px, 1440px
- 이 조합의 핵심 인수 시나리오 통과율 100%가 `NFR-COMPATIBILITY-001` 판정 기준이다

### 6.2 iPhone Safari 취급

- 지원 대상으로 표방하지 않는다. 문서·화면·안내에 iOS Safari 동작을 보증하는 문구를 두지 않는다.
- 인수 시나리오 통과율과 완료 판정 계산에서 제외한다.
- 의도적 차단, 기능 제거, User-Agent 기반 분기와 안내 배너를 넣지 않는다. 표준 웹 기술로 구현하고 iOS 전용 우회 코드도 넣지 않는다.
- iOS Safari에서 발견된 결함은 접수하되 다른 지원 브라우저와 같은 우선순위로 다루지 않는다. 결함은 별도 이슈로 분리한다.
- 검증 기록 문서에서 iPhone Safari 행은 **미검증**으로 유지한다. 다른 Blink 계열 브라우저 확인 결과를 iPhone Safari 검증 증거로 대체하지 않는다.

## 7. 해제 조건

다음 중 하나가 충족되면 이 결정을 재검토하고 iPhone Safari를 6.1절 매트릭스로 되돌린다.

1. 팀이 iPhone 실단말을 확보하고 검증 환경(단말·iOS·Safari 버전)을 기록할 수 있게 된다.
2. 원격 실단말 서비스 도입 ADR이 Accepted가 되고 해당 환경에서 매트릭스를 실행할 수 있게 된다.

되돌릴 때는 6.1절, 계약 문서 3곳(8절)과 테스트 추적표를 같은 PR에서 함께 바꾼다.

## 8. 영향

| 대상 | 변경 |
|---|---|
| [범위](../../00-overview/scope.md) 8·9절 | 지원 브라우저 문장에서 iPhone Safari를 분리하고 보류 상태를 명시 |
| [NFR-COMPATIBILITY-001](../../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성) | 목표 기준의 지원 브라우저 목록 개정 |
| [RV-NFR-006](../../01-requirements/non-functional-requirements.md#rv-nfr-006-지원-브라우저-범위) | 2026-07-27 결정 내용에 이 ADR의 개정을 연결 |
| [ADR-WEB-001](web-001-frontend-platform.md) 12절 | 지원 브라우저 문장을 이 ADR 참조로 대체 |
| [2차 확장 테스트 추적표](../../08-planning/second-expansion-test-matrix.md) | `TST-E2-E2E-001`의 iPhone Safari 보류 항목을 결정 반영 상태로 갱신 |
| [2차 확장 브라우저 검증 기록](../../08-planning/second-expansion-browser-verification.md) | 확인 환경·결과와 미검증 항목의 증거 문서 |

## 9. 검증

- 계약 문서 3곳과 테스트 추적표에서 iPhone Safari를 지원 대상으로 표방하는 문장이 남아 있지 않은지 확인한다.
- 프론트엔드 코드에 User-Agent 분기, iOS 차단, iOS 전용 우회가 없는지 확인한다.
- 2차 확장 브라우저 검증 기록에 매트릭스 3종의 확인 환경(단말·OS·브라우저 버전)과 결과, iPhone Safari의 미검증 상태가 함께 남아 있는지 확인한다.

## 10. 재검토 조건

7절 해제 조건 충족, 승인된 지원 브라우저 범위의 추가 변경 또는 iOS 사용자 비중을 근거로 한 우선순위 재조정 요청이 있을 때 재검토한다.
