---
related_documents:
  - ../07-adr/README.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/platform/web-005-application-port-binding.md
  - ../07-adr/adr-index.md
  - ../07-adr/adr-traceability.md
  - ../06-architecture/technology-policy.md
  - ../08-planning/README.md
  - ../08-planning/issue-200-application-port-binding.md
---

# PR #210 리뷰 트러블슈팅: 운영 포트 결정의 ADR 분리와 대체 범위 판정

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#210 Spring Boot 운영 포트의 Nginx 우회 접근을 방지하도록 바인딩을 고정한다](https://github.com/team-youngkk/masit-on/pull/210) |
| 작성자 | 이우람 (`w00lam`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 인라인 리뷰 스레드 3건. Accepted ADR 직접 수정, 계획 문서 탐색 목록, 그리고 대체 범위와 결정 소유권 |
| 주 문제 유형 | 기타(ADR 절차·문서 추적성) |
| 기존 기록 | 동일 문제의 기존 기록은 없었다. `docs/troubleshooting`의 ADR 추적성 리뷰 기록 형식을 확인하고 이번 PR의 운영 포트 결정에 맞춰 새 기록을 작성했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [ADR 직접 수정](https://github.com/team-youngkk/masit-on/pull/210#discussion_r3782297093) | Accepted ADR-WEB-003에 새 운영 포트 결정을 직접 추가하지 말고 후속 ADR·추적표를 작성 | 기타 | 수정 필요 | ADR-WEB-003에 추가했던 강제 규칙 3줄(6.1 서술·10절 규칙·13절 검증)을 제거하고 ADR-WEB-005를 신설했다. 인덱스·추적표·플랫폼 README와 기술 정책을 갱신했다. | ADR frontmatter·인덱스·추적표 대조, `git diff --check` |
| [계획 문서 탐색 목록](https://github.com/team-youngkk/masit-on/pull/210#discussion_r3782297097) | `issue-200-application-port-binding.md`를 `docs/08-planning/README.md`의 related_documents와 표에 등록 | 기타 | 수정 필요 | 계획 README의 related_documents와 문서 목록에 신규 문서를 추가했다. 계획 문서 1절에 결정 소유가 ADR-WEB-005임을 명시했다. | README 링크·표 항목과 파일 경로 대조 |
| [대체 범위와 결정 소유권](https://github.com/team-youngkk/masit-on/pull/210#discussion_r3782608395) | ADR-WEB-005가 ADR-WEB-003 전체를 `supersedes`로 선언했으나 실제로는 포트 바인딩만 이관해, ADR-WEB-003이 계속 소유하는 결정에 Accepted 소유자가 없어짐 | 기타 | 수정 필요 | 대체 관계를 걷어냈다. `ADR-WEB-005`는 `supersedes: []`, `ADR-WEB-003`은 `Accepted`·`superseded_by: null`로 되돌렸다. 두 문서 1절에 소유 범위 분리를 명시하고 인덱스·추적표를 함께 정정했다. | 두 ADR frontmatter, 인덱스 상태 열, 추적표 Nginx 행 대조. `Superseded` 표기 잔존 0건 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. ADR 변경 절차와 결정 소유권 표기의 문제다.
- 발생 환경: PR #210 `fix/application-port-binding`, `develop` 대상, 운영 포트 loopback 바인딩 변경.
- 재현 조건:
  - Accepted 상태인 ADR-WEB-003의 본문·강제 규칙·검증 방법에 운영 포트 결정을 직접 추가한다.
  - 신규 계획 문서를 만들고 해당 하위 디렉터리 README의 frontmatter와 목록을 갱신하지 않는다.
  - 위 지적을 반영하면서, 결론을 덮어쓰지 않는 **추가** 결정에도 문서 전체 단위 대체 관계를 적용한다.
- 실제 결과: 세 번째 조건에서 ADR-WEB-003이 `Superseded`가 되어, 그 문서가 계속 소유하는 경로 소유권(6.1)·화면 경로(6.2)·관리자 인증 순서(6.3)·인증 상태 복구(6.4)·상태 확인 경로(6.5)에 Accepted 소유자가 없어졌다. `Superseded` ADR은 현재 구현 기준이 아니므로 이를 인용하는 기술 정책·보안 경계·API 계약 문서가 대체된 결정을 근거로 삼는 상태가 됐다.
- 기대 결과: 결론을 바꾸는 변경만 대체 절차를 따르고, 추가 결정은 독립 Accepted ADR로 두어 두 결정의 소유자가 각각 분명해야 한다.
- 영향 범위: 운영 포트 보안 경계의 결정 이력, 경로 라우팅·인증 순서·상태 확인 경로의 현재 기준, NFR·Workstream 추적성.

## 4. 근본 원인

두 단계로 나뉜다.

첫째, 운영 포트 loopback 바인딩을 기존 라우팅 ADR의 강제 규칙에 추가하면 관련 결정이 한 곳에서 관리된다고 판단했다. 그러나 [ADR README 9절](../07-adr/README.md#9-변경-및-대체-절차)은 Accepted ADR의 결론을 덮어쓰지 않도록 규정한다.

둘째, 그 지적을 반영할 때 절차의 적용 조건을 넓게 해석했다. 9절의 대체 절차는 **결론을 바꾸는** 변경에 적용된다. 첫 단계에서 추가한 강제 규칙 3줄을 제거한 시점에 ADR-WEB-003의 결론은 결정 당시 상태로 복원됐고, 남은 것은 "운영 애플리케이션 포트의 바인딩 주소"라는 새 결정뿐이었다. 그 상태에서는 대체할 결론이 없으므로 절차 자체의 대상이 아니었는데, `supersedes`를 선언해 문서 전체를 대체한 것으로 표기했다.

또한 계획 파일 생성 시 `docs/08-planning/README.md`의 탐색 계약을 함께 확인하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL로 PR #210의 미해결 스레드와 인라인 문맥 조회 | 최초 미해결 2건, 반영 후 신규 1건 추가 | 세 요청 모두 현재 PR 범위에서 수정 필요로 분류 |
| `docs/07-adr/README.md` 9절과 기존 Superseded 사례(ADR-DEPLOY-001 → ADR-DEPLOY-002) 확인 | `supersedes`는 YAML 리스트, `related_documents`에 인덱스·추적표 포함 | ADR-WEB-005 frontmatter 형식을 선례에 맞춤 |
| ADR-WEB-003이 소유한 결정 범위 확인 | 6.1~6.5의 다섯 결정을 계속 소유하고 ADR-WEB-005는 그중 어느 것도 가져오지 않음 | 문서 전체 대체가 성립하지 않는다고 판단 |
| 라우팅 결정을 ADR-WEB-005로 옮겨 적는 대안 검토 | 같은 계약이 두 문서에 존재하게 됨 | 한쪽만 갱신될 위험이 있어 채택하지 않음 |
| ADR-WEB-003 강제 규칙 제거 후 남은 변경의 성질 확인 | ADR-WEB-003의 어떤 결론도 바뀌지 않고 새 결정만 추가됨 | 대체 관계를 걷어내고 두 ADR을 모두 Accepted로 결정 |
| `ADR-WEB-003`을 인용하는 문서 전수 확인 | 기술 정책 152행, 보안 경계, 계획 문서 32행, 플랫폼 README | 모두 경로 라우팅·상태 확인 경로 근거여서 Accepted 복원 후 그대로 유효 |
| `develop` 병합 | 문서 4건과 계약 테스트 1건 충돌 | 양쪽 추가 항목을 모두 보존해 해소 |

## 6. 최종 해결

- 변경 내용: 운영 포트 loopback 결정을 ADR-WEB-005로 분리했다. **대체 관계는 두지 않는다.** ADR-WEB-005는 `supersedes: []`인 신규 Accepted ADR이고 ADR-WEB-003도 Accepted를 유지한다. 두 문서 1절에 각자 소유하는 결정을 명시했다. ADR 인덱스·추적표·플랫폼 README·기술 정책·계획 README를 갱신했다.
- 선택 이유: ADR-WEB-005는 ADR-WEB-003의 결론을 바꾸지 않고 새 결정만 추가한다. ADR README 9절의 대체 절차는 결론을 바꾸는 변경에 적용되므로 이 경우는 대상이 아니다. 대체 관계를 두면 ADR-WEB-003이 계속 소유하는 다섯 결정이 현재 기준에서 빠진다.
- 변경 파일: `src/main/resources/application-prod.yml`, `src/test/java/com/masiton/deployment/AppRunScriptContractTest.java`, `docs/07-adr/platform/web-005-application-port-binding.md`, `docs/07-adr/platform/web-003-routing-boundary.md`, `docs/07-adr/platform/README.md`, `docs/07-adr/adr-index.md`, `docs/07-adr/adr-traceability.md`, `docs/06-architecture/technology-policy.md`, `docs/06-architecture/security-boundary.md`, `docs/08-planning/issue-200-application-port-binding.md`, `docs/08-planning/README.md`.
- 고려한 대안: ADR-WEB-005가 라우팅 결정까지 흡수하는 방식은 경로 소유권·화면 경로·인증 순서·인증 상태 복구·상태 확인 경로를 새 문서로 옮겨 적어야 해서 같은 계약이 두 곳에 생기고 한쪽만 갱신될 위험이 남는다. 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew clean build` | 통과 | `BUILD SUCCESSFUL`. 운영 프로파일 loopback, 두 분기의 실행 네트워크, frontend `HOSTNAME`, Nginx upstream 2개, 상태 지표 대상, 바인딩 확장 환경 변수 미전달 계약을 대조 |
| GitHub Actions 백엔드 빌드·테스트 | 통과 (커밋 `f3f932f`) | 격리된 체크아웃에서 전체 테스트 통과 |
| GitHub Actions 프론트엔드 빌드·타입 검사 | 통과 | 타입 검사·빌드 |
| ADR frontmatter·인덱스·추적표 대조 | 통과 | 두 ADR 모두 `Accepted`·`supersedes: []`·`superseded_by: null`. `Superseded` 표기 잔존 0건 |
| 문서 상대 경로 링크 검사 | 통과 | 변경 문서 전체에서 끊긴 링크 0건 |
| `develop` 병합 충돌 해소 | 통과 | 충돌 마커 잔존 0건. ADR-DEPLOY-004 갱신과 ADR-WEB-005 추가를 모두 보존 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Accepted ADR을 건드릴 때 **결론을 바꾸는 변경인지 새 결정을 추가하는 변경인지 먼저 구분한다.** 결론을 바꾸면 대체 절차를 따르고, 추가면 독립 ADR로 두고 대체 관계를 만들지 않는다. 대체 관계를 만들 때는 기존 ADR이 소유한 결정을 전수로 열거하고 후속 ADR이 그것을 모두 가져오는지 확인한다. 하나라도 남으면 문서 전체 대체가 성립하지 않는다.
- 재발 방지: Accepted ADR 상태를 바꾸면 루트 인덱스, 추적표, 기술 정책, 하위 영역 README, 그리고 그 ADR을 인용하는 코드 주석·테스트 Javadoc까지 함께 점검한다.
- 다음 확인: 병합 커밋 이후 GitHub Actions 백엔드 빌드·테스트와 두 리뷰어의 재검토를 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 운영 포트 결정의 소유 문서 | ADR-WEB-003 강제 규칙에 직접 기록 | ADR frontmatter·인덱스·추적표 검색 | ADR-WEB-005 독립 Accepted 문서 | 결정 소유가 문서 단위로 분리됨 | PR 작성자, PR #210 리뷰 반영 시점 |
| Accepted 소유자가 없는 결정 수 | 5건 (ADR-WEB-003 6.1~6.5, 문서 전체 대체 시점) | 인덱스 상태 열과 ADR frontmatter 대조 | 0건 | 대체 관계 제거로 해소 | PR 작성자, PR #210 리뷰 반영 시점 |
| 운영 계획 문서 탐색 경로 | `docs/08-planning/README.md`에 미등록 | related_documents와 목록 대조 | 두 위치에 등록 | 하위 디렉터리 진입점에서 검색 가능 | PR 작성자, PR #210 리뷰 반영 시점 |
| 바인딩 경계 회귀 고정 지점 | 0개 | 계약 테스트 단정 수 | 6개 (프로파일 값, 확장 환경 변수 미전달, 두 분기 host 네트워크, frontend `HOSTNAME`, Nginx upstream 2개, 상태 지표 대상) | 양방향 회귀 고정 | PR 작성자, PR #210 리뷰 반영 시점 |

## 10. 남은 사항

- 운영 배포 후 실제 외부 8080 직결 차단과 loopback 경로 정상 동작 검증은 PR 본문과 [운영 애플리케이션 포트 바인딩 경계](../08-planning/issue-200-application-port-binding.md) 4절의 미검증 항목으로 남아 있다. 보안 그룹·호스트 방화벽은 저장소 산출물이 아니므로 계약 테스트로 고정할 수 없다.
- ADR-PERF-001의 인스턴스 간 8080 직결 측정은 이 결정 이후 성립하지 않는다. 인스턴스 내부 loopback 측정과 측정 전용 인스턴스의 `local` 프로파일 기동 중 어느 쪽을 쓸지는 측정 절차 소유자의 결정으로 남겼다.
