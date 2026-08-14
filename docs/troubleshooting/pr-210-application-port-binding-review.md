---
related_documents:
  - ../07-adr/README.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/platform/web-005-application-port-binding.md
  - ../07-adr/adr-index.md
  - ../07-adr/adr-traceability.md
  - ../08-planning/README.md
  - ../08-planning/issue-200-application-port-binding.md
---

# PR #210 리뷰 트러블슈팅: 운영 포트 결정의 ADR 분리와 문서 탐색성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#210 Spring Boot 운영 포트의 Nginx 우회 접근을 방지하도록 바인딩을 고정한다](https://github.com/team-youngkk/masit-on/pull/210) |
| 작성자 | 이우람 (`w00lam`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 최초 미해결 인라인 리뷰 스레드 2건의 ADR 대체 절차와 계획 문서 탐색 목록 반영 |
| 주 문제 유형 | 기타(ADR·문서 추적성) |
| 기존 기록 | 동일 문제의 기존 기록은 없었다. `docs/troubleshooting`의 ADR 추적성 리뷰 기록 형식을 확인하고 이번 PR의 운영 포트 결정에 맞춰 새 기록을 작성했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [ADR 직접 수정](https://github.com/team-youngkk/masit-on/pull/210#discussion_r3782297093) | Accepted ADR-WEB-003에 새 운영 포트 결정을 직접 추가하지 말고 후속 ADR·대체 관계·추적표를 작성 | 기타 | 수정 필요 | ADR-WEB-003을 Superseded로 연결하고 ADR-WEB-005를 추가했다. 인덱스·추적성·플랫폼 README를 갱신하고 기존 경로 라우팅 결정은 보존했다. | `git diff --check`, ADR frontmatter·인덱스·추적표 대조 |
| [계획 문서 탐색 목록](https://github.com/team-youngkk/masit-on/pull/210#discussion_r3782297097) | `issue-200-application-port-binding.md`를 `docs/08-planning/README.md`의 related_documents와 표에 등록 | 기타 | 수정 필요 | 계획 README의 related_documents와 문서 목록에 신규 문서를 추가했다. | README 링크·표 항목과 파일 경로 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. ADR 변경 이력과 문서 탐색 목록 누락이다.
- 발생 환경: PR #210 `fix/application-port-binding`, `develop` 대상, 운영 포트 loopback 바인딩 변경.
- 재현 조건:
  - Accepted 상태인 ADR-WEB-003의 본문·강제 규칙·검증 방법에 운영 포트 결정을 직접 추가한다.
  - 신규 계획 문서를 만들고 해당 하위 디렉터리 README의 frontmatter와 목록을 갱신하지 않는다.
- 실제 결과: Accepted ADR의 원래 결정과 후속 결정의 변경 경계가 섞이고, 계획 문서가 디렉터리 진입점에서 검색되지 않는다.
- 기대 결과: 기존 ADR은 대체 관계를 통해 이력을 보존하고, 새 결정은 별도 ADR·인덱스·추적표에서 현재 기준으로 조회되며, 계획 문서는 하위 README에서 탐색된다.
- 영향 범위: 운영 포트 보안 경계의 결정 이력, NFR·Workstream 추적성, 배포 후 검증 절차 탐색성.

## 4. 근본 원인

PR 구현 과정에서 운영 포트 loopback 바인딩을 기존 라우팅 ADR의 강제 규칙에 추가하면 관련 결정이 한 곳에서 관리된다고 판단했다. 그러나 저장소 규칙은 Accepted ADR의 결론을 덮어쓰지 않고 후속 ADR의 `supersedes`·기존 ADR의 `superseded_by`로 변경 이력을 남기도록 한다. 또한 계획 파일 생성 시 `docs/08-planning/README.md`의 탐색 계약을 함께 확인하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL로 PR #210의 미해결 스레드와 인라인 문맥 조회 | 미해결 스레드 2건, 모두 outdated 아님 | 두 요청 모두 현재 PR 범위에서 수정 필요로 분류 |
| `docs/07-adr/README.md` 9절과 Superseded ADR 사례 확인 | Accepted ADR 변경은 후속 ADR과 대체 관계를 남겨야 함 | ADR-WEB-005를 새로 만들고 ADR-WEB-003 metadata를 갱신 |
| `docs/08-planning/README.md` 구조 확인 | 신규 계획 문서가 related_documents·표에 없었음 | 두 위치에 동일 문서 링크 추가 |
| 기존 `docs/troubleshooting` 기록 검색 | 같은 PR의 기존 기록 없음, 인덱스와 선행 기록 형식 확인 | 새 기록을 추가하고 README 인덱스에 등록 |

## 6. 최종 해결

- 변경 내용: 운영 포트 loopback 결정을 ADR-WEB-005로 분리하고 ADR-WEB-003을 Superseded로 연결했다. ADR 인덱스·추적성·플랫폼 README와 계획 README를 갱신했다.
- 선택 이유: 기존 웹 경로 라우팅 결정의 원문과 이력을 보존하면서 운영 포트 결정의 현재 기준과 영향 범위를 독립적으로 추적하기 위해서다.
- 변경 파일: `docs/07-adr/platform/web-003-routing-boundary.md`, `docs/07-adr/platform/web-005-application-port-binding.md`, `docs/07-adr/platform/README.md`, `docs/07-adr/adr-index.md`, `docs/07-adr/adr-traceability.md`, `docs/08-planning/README.md`.
- 고려한 대안: ADR-WEB-003에 계속 규칙을 추가하는 방식은 저장소의 Accepted ADR 대체 절차와 충돌하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 문서·frontmatter·링크 변경에 공백 오류 없음 |
| `./gradlew.bat test --tests "com.masiton.deployment.AppRunScriptContractTest" --no-daemon --console=plain` | 실행 후 결과 기록 | 운영 프로파일 loopback, 실행 네트워크, Nginx·상태 지표 대상 계약 회귀 확인 |
| ADR·README 링크 대조 | 통과 | ADR-WEB-003 ↔ ADR-WEB-005 대체 관계, ADR 인덱스·추적표·하위 README 경로 일치 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Accepted ADR 변경 시 후속 ADR 대체 관계, 루트 인덱스, 추적표, 하위 영역 README를 함께 점검하는 기록을 남겼다.
- 다음 확인: PR #210의 최신 커밋에 대해 GitHub Actions 백엔드 빌드·테스트와 두 리뷰어의 재검토를 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| Accepted ADR의 운영 포트 결정 추적 경로 | ADR-WEB-003 본문에 직접 기록 | ADR frontmatter·인덱스·추적표 검색 | ADR-WEB-005 독립 문서 + `superseded_by`·`supersedes` 연결 | 변경 이력과 현재 결정의 분리 확인 | PR 작성자, PR #210 리뷰 반영 시점 |
| 운영 계획 문서 탐색 경로 | `docs/08-planning/README.md`에 미등록 | related_documents와 목록 대조 | 두 위치에 등록 | 하위 디렉터리 진입점에서 검색 가능 | PR 작성자, PR #210 리뷰 반영 시점 |

## 10. 남은 사항

- 리뷰 스레드 2건은 원격 커밋·검증 완료 후 각각 인라인 답글을 남기고 해결 처리한다.
- 운영 배포 후 실제 외부 8080 직결 차단 검증은 PR 본문과 ADR의 미검증 항목으로 남아 있다.
