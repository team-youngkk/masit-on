---
related_documents:
  - ../07-adr/README.md
  - ../07-adr/platform/web-001-frontend-platform.md
  - ../07-adr/platform/web-007-next-security-patch-baseline.md
  - ../07-adr/adr-index.md
  - ../07-adr/adr-traceability.md
  - ../06-architecture/technology-policy.md
  - pr-210-application-port-binding-review.md
  - pr-257-runtime-baseline-review.md
---

# PR #396 리뷰 트러블슈팅: 승인 기준과 보안 패치 변경안 분리

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#396](https://github.com/team-youngkk/masit-on/pull/396) |
| 작성자 | 김인안 (`inan0226`) |
| 처리 일자 | 2026-10-05 |
| 범위 | 기존 P2·P1과 재리뷰 P1·P2, Next.js 기준선 채택 및 파생 문서 정합화 |
| 주 문제 유형 | 기타 — 런타임 결함이 아닌 ADR 상태·문서 권위의 불일치 |
| 기존 기록 | [PR #210](pr-210-application-port-binding-review.md)의 Accepted 결정 보존·대체 범위 확인, [PR #257](pr-257-runtime-baseline-review.md)의 파생 결론까지 함께 정정하는 점검 방식을 재사용했다. 이번에는 미승인 패치를 다루므로 기존 사례의 Accepted 상태를 그대로 적용하지 않았다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [기준선 혼재](https://github.com/team-youngkk/masit-on/pull/396#discussion_r4163216811) | Accepted 결정과 Proposed 패치 변경안 구분 | 기타 | 수정 필요 | 당시 첫 번째 제안으로 기존 Accepted 16.3.4를 보존했다. 이번 합의 후 16.3.8을 채택하고 활성 기준을 정합화했다. | WEB-001, WEB-007, 기술 정책 및 인덱스 대조 |
| [병합 전 기준선 채택](https://github.com/team-youngkk/masit-on/pull/396#discussion_r4163575060) | 소유자 합의 또는 패키지 변경 제외 후 모든 승인 기준 갱신 | 기타 | 수정 필요 | 양성훈·김인안의 16.3.8 채택 합의를 확인해 ADR-WEB-007을 Accepted로 전환하고 모든 기준 문서를 같은 PR에서 갱신했다. | 2026-10-05 ADR decision date와 Accepted 상태, package.json·잠금 파일 16.3.8 확인 |
| [재리뷰 P1: Accepted 기준선 불일치](https://github.com/team-youngkk/masit-on/pull/396) | 16.3.8 채택 및 승인·파생 기준 문서 정합화 | 기타 | 수정 필요 | AGENTS.md, CLAUDE.md, ADR-WEB-001·007, ADR 인덱스·추적표, 기술 정책, MVP 계획, 성능 ADR을 갱신했다. | Next.js 활성 기준 16.3.8만 유지되는지 저장소 전체 검색 |
| [재리뷰 P2: 플랫폼 ADR 인덱스](https://github.com/team-youngkk/masit-on) | ADR-WEB-007을 플랫폼 README의 링크와 ADR 목록에 추가 | 기타 | 수정 필요 | 플랫폼 README의 related_documents와 ADR 표에 추가했다. | README 링크 대상과 표 행 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 코드 실행 오류가 아니라 승인되지 않은 기준선의 문서 전파 문제다.
- 발생 환경: PR #396, `codex/next-security-patch`, 리뷰 기준 HEAD `95e69c34`; 합의 반영일 2026-10-05.
- 재현 조건: package.json·잠금 파일에 16.3.8이 적용된 상태에서 ADR-WEB-007은 Proposed로, 승인 기준 문서는 16.3.4로 남아 있었다.
- 실제 결과: 기본 브랜치에 병합하면 실제 의존성과 Accepted 계약이 달라지고 플랫폼 ADR 하위 인덱스에서 새 ADR을 찾을 수 없었다.
- 기대 결과: 사용자 전달에 따른 소유자 채택 합의를 기록하고, 의존성 버전과 모든 활성 승인·탐색 문서를 같은 기준으로 맞춘다.
- 영향 범위: 프론트엔드 의존성 보안 기준과 ADR·정책·작업 지침·계획의 해석. API·DB 계약은 바뀌지 않는다.

## 4. 근본 원인

초기 리뷰 반영에서는 팀 채택 결정이 없는데도 패키지·잠금 파일만 16.3.8로 바뀌어 있었다. 이번 작업에서는 사용자가 양성훈·김인안의 채택 합의를 전달했으므로, Proposed 변경안을 Accepted ADR로 확정하고 계약 문서와 탐색 인덱스까지 같은 변경에서 갱신했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub 미해결 스레드·승인·변경 요청 확인 | 기존 P1 thread와 재리뷰 P1/P2 확인 | 당시 일반 승인을 합의로 확대하지 않았으며, 이번 사용자 입력을 채택 합의 근거로 반영 |
| 기존 트러블슈팅 검색 | #210은 결정 소유권, #257은 파생 기준선 정합성 문제 | 점검 방식만 재사용 |
| ADR-WEB-001·007과 파생 표 대조 | 실제 의존성과 Accepted 기준 불일치 | 합의 전달에 따라 ADR-WEB-007 채택 및 전체 기준 갱신 |
| 플랫폼 ADR 하위 README 점검 | WEB-007 누락 | related_documents와 ADR 목록에 추가 |

## 6. 최종 해결

- ADR-WEB-007을 Accepted로 전환하고 ADR-WEB-001의 활성 버전·검증 기준을 16.3.8로 갱신했다. 2026-09-09 16.3.4 결정과 기존 검증 기록은 역사로 보존했다.
- ADR 인덱스·추적표·플랫폼 README, 기술 정책, AGENTS.md·CLAUDE.md, 성능 ADR, MVP 계획의 승인 버전을 16.3.8로 정합화했다.
- 플랫폼 ADR README의 related_documents와 ADR 목록에 WEB-007을 추가했다.
- 패키지·잠금 파일은 이미 16.3.8이며 이번 작업에서 바꾸지 않았다. API·DB·실행 코드는 변경하지 않았다.
- 이 기록을 [트러블슈팅 목록](README.md)에 등록했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 문서 공백 오류 없음 |
| 저장소 전체 활성 기준선 검색 | 통과 | 과거 이력 외에 활성 기준 문서가 Next.js 16.3.8을 사용 |
| 변경 Markdown 11개 상대 링크 검사 | 통과 | 내부 상대 링크 대상이 모두 존재 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| [PR HEAD CI run 37317149479](https://github.com/team-youngkk/masit-on/actions/runs/37317149479) | 통과 | 프론트 빌드·타입, 백엔드 빌드·테스트, RSA 키, Terraform 계약 검사 통과. 배포·이미지 작업은 PR 이벤트에서 skip |
| 기존 [보안 패치 CI](https://github.com/team-youngkk/masit-on/actions/runs/36842275221) | 통과 — 리뷰 반영 전 커밋 | Node 24.18.0 감사·프론트 빌드·타입, 백엔드, 비밀키, Terraform. 새 문서 커밋 결과로 간주하지 않음 |
| [문서 수정 CI](https://github.com/team-youngkk/masit-on/actions/runs/36976885014) | 통과 — `3fc8a39b` | 백엔드·프론트 감사/빌드/타입·비밀키·Terraform 통과. 추가 P1 처리 기록만 보완한 후속 커밋과 구별 |

## 8. 재발 방지 및 다음 확인

- 정책과 변경안에 승인 기준·검증 형상을 분리한 절과 병합 전 승인 게이트를 추가했다.
- 다음 확인: 변경을 PR 브랜치에 반영한 뒤 리뷰 요청자 `w00lam`에게 재검토를 요청한다.
- #395에도 같은 보안 패치가 있으므로 #396 확정 후 문서 정합성과 병합 결과를 확인해야 한다. 이번 작업은 #396만 수정하며 #395 브랜치를 자동 갱신하지 않는다.

## 9. 도입 전후 비교 지표

해당 없음 — 런타임 동작·성능을 바꾸지 않는 승인 상태 문서 정정이다. 배포 전후 성능 수치를 만들지 않는다. 효과는 동일 문서의 승인 버전 및 변경안 구분을 정적으로 대조한다.

## 10. 남은 사항

기존 P1과 최신 재리뷰 P1/P2를 반영하고 PR HEAD CI 통과를 확인했다. 실제 브라우저 인수·운영 적용은 수행하지 않았다.
