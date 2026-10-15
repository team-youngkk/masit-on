---
related_documents:
  - ../08-planning/issue-414-ai-contract-proposal.md
  - ../08-planning/issue-417-ai-contract-proposal.md
  - ../08-planning/issue-416-bounded-place-search.md
  - ../01-requirements/business-rules.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - pr-430-bounded-place-search-ci-review.md
---

# PR #431 리뷰 트러블슈팅: S3 Snapshot 위치 결속과 develop 충돌

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#431 주소 누락 시 같은 식당의 위치 표현으로 지역 검증](https://github.com/team-youngkk/masit-on/pull/431) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-10-15 |
| 범위 | S3 `registrationUnits` Snapshot 보존 리뷰와 #417/#416 병합 뒤 develop 충돌 통합 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #430 지역 결합 후보 검색 기록](pr-430-bounded-place-search-ci-review.md) 및 [PR #279 장소 동일성 리뷰](pr-279-ai-place-identity-review.md)의 지역 경계·테스트 계약을 확인했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [S3 registrationUnits Snapshot 보존](https://github.com/team-youngkk/masit-on/pull/431#discussion_r4278167463) | Provider 응답에서 Snapshot `candidate_fields`까지 단위 location을 보존하고, Worker·관리자 양 경로가 같은 S3 단위 location을 사용하는 회귀 검증 | 애플리케이션 | 수정 필요 | 최신 develop의 #417 생산자 경로를 유지하고 양쪽 실행 경로에 expected `candidateUnitId`와 식당명 일치 검사를 연결했다. Snapshot 전달·양 경로 명령 인수·지역 재검증 테스트를 보강했다. | 8개 관련 테스트 클래스 97건 통과; push 뒤 스레드 답글과 해결 상태를 동기화한다. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `MISSING_REQUIRED_FIELD`.
- 발생 환경: PR #431의 기존 head `c06f5afa`, Java 21, develop의 #417/#416 병합 전후.
- 재현 조건: 주소가 없는 S3 단위가 Worker 또는 관리자 등록 실행에 도달할 때 `candidate_fields.registrationUnits`에서 같은 단위의 명시적 location을 복원하지 못하는 경우.
- 실제 결과: 리뷰 시점 base에는 provider 후보를 Snapshot JSON으로 보존하는 #417 생산자 변경이 없어 `boundLocation()`이 null을 반환할 수 있었다. 선행 PR 병합 뒤 오래된 #431 브랜치와 새 develop 사이에는 문서·서비스·테스트 충돌도 발생했다.
- 기대 결과: Provider 후보 전체가 Snapshot에 보존되고, 같은 `candidateUnitId`·식당명인 단위의 위치만 주소 누락 시 검색 단서가 된다. 최종 Kakao 주소의 행정구역이 다른 경우 등록 저장은 0건이어야 한다.
- 영향 범위: 주소가 없는 AI 등록 단위의 자동 장소 동일성 검증. location만으로 주소·좌표를 추정하거나 저장하지 않는다.

## 4. 근본 원인

PR #431은 #417 생산자 경로가 아직 base에 없는 시점에 구현되었다. 이후 #417/#416 선행 PR이 develop에 병합됐지만 #431은 최신 base에 재통합되지 않아 리뷰가 요구한 provider→Snapshot→Worker/관리자 경로를 한 번에 검증할 수 없었다. 충돌 해결 중 기존 bounded `searchCandidates(name, district)`와 #420 외부 실패 진단 경계를 보존해야 했다. 통합 과정에서 location 조회 함수도 bundle의 기대 `candidateUnitId`를 직접 비교하도록 좁혀, 인덱스만 맞는 다른 단위 값을 전달하지 못하게 했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 미해결 인라인 스레드와 PR 작성자·head 확인 | P1 한 건, 작성자 `tjdgns0618`, head `c06f5afa` | 검증 후 수정하고 동일 스레드에 답한다. |
| 현재 `origin/develop`과 PR 브랜치 비교 | #417/#416 선행 코드가 develop에 있고 PR 파일 9곳에서 충돌 | 최신 develop 위로 PR 변경을 재기반화한다. |
| `ResolvePlaceIdentityServiceTest` 실행 | 구형 `search(name)` stub 한 건 실패 | #430 계약에 맞춰 정규화된 district 인수 stub·검증으로 변경했다. |
| Snapshot producer와 Worker·관리자 consumer 테스트 확인 | producer는 전체 registrationUnits JSON을 전달하고, consumer는 각자 실행 명령을 구성 | Snapshot location 보존 단언과 양 consumer 경로의 expected ID·location 단언을 추가했다. |

## 6. 최종 해결

- 변경 내용: #417 S3 Snapshot 및 다중 메뉴 흐름과 #420 진단 로깅을 보존하면서 #414의 단위 결속 location 전달, 주소 없는 지역 검색, 최종 물리 지역 일치 검증을 통합했다. 중복·불일치 ID와 식당명에는 location을 전달하지 않는다.
- 선택 이유: 승인된 #414 계약을 적용하면서 선행 PR 변경을 후속 diff에 복사하지 않고 develop의 최신 구현을 기준으로 통합한다.
- 변경 파일: API/PRD/비즈니스 규칙/ADR 문서, `docs/08-planning/README.md`, #414 설계·트러블슈팅 기록, AI 등록 단위 실행·JSON 지원·장소 동일성 서비스 및 관련 테스트.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| 관련 8개 테스트 클래스 Gradle Wrapper 실행 | 통과 | Provider/Snapshot 필드 보존, S3 분해, Worker·관리자 location 결속, 주소 누락 검색, 지역 불일치 저장 0건 및 기존 #420 진단 테스트 포함 97건 통과 |
| `git diff --check` | 통과 | 통합 변경의 공백 오류 없음 |
| 실제 Kakao·YouTube 호출 | 실행하지 않음 | 고정 후보·Mockito 기반 검증 |
| GitHub PR CI | 대기 | 수정 커밋 push 뒤 최신 head에서 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Snapshot JSON의 provider→candidate_fields→Worker/관리자 전달을 각각 단언하고, location 복원 시 expected `candidateUnitId`와 식당명을 모두 검사한다.
- 다음 확인: push 후 P1 스레드에 수정·검증 링크를 답하고 해결 처리한다. PR CI 통과와 리뷰 재승인을 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 관련 자동화 테스트 실패 | 최신 base 통합 테스트 전 | 동일 8개 관련 클래스 실행 | 97건 중 0건 실패 | 실행 경로별 S3 단위 location 전달 회귀가 로컬에서 통과 | PR #431 CI 완료 시 원격 결과 갱신 |

## 10. 남은 사항

- 코드·문서 변경은 최신 develop에 재기반화했다. push 후 PR CI, 새 승인 및 리뷰 스레드의 원격 상태를 확인해야 한다.
