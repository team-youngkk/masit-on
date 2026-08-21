---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
---

# PR #278 리뷰 트러블슈팅: Kakao 장소명 완화 판정의 감사와 활성화 게이트

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#278 AI 장소명 부분 일치 검증](https://github.com/team-youngkk/masit-on/pull/278) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-21 |
| 범위 | 장소명 fallback의 운영 활성화 경계와 등록 단위 판정 JSON·Accepted 데이터 계약 정합성 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 |
| 기존 기록 | [PR #226 AI 자동 등록 계약 리뷰](pr-226-ai-auto-registration-contract-review.md), [PR #209 AI 후보 등록 경계 리뷰](pr-209-ai-candidate-registration-review.md)를 확인했으며, 이번 변경의 판정 JSON 감사 범위를 별도로 기록했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [P1 매핑 행 식별자](https://github.com/team-youngkk/masit-on/pull/278#discussion_r3829685075) | `category_decision`에 판정 당시 매핑 행 ID를 보존 | 데이터베이스 | 수정 필요 | `CategoryDecision`·직렬화에 `matchedMappingId`를 추가하고 데이터 계약·API·요구사항을 동기화 | `RegistrationUnitAutoExecutionServiceTest`, 전체 `clean build` 통과 |
| [P2 장소 감사 필드](https://github.com/team-youngkk/masit-on/pull/278#discussion_r3829685090) | 검색어와 채택한 Kakao 장소 ID가 실제 JSON에 없음 | 애플리케이션 | 수정 필요 | `PlaceDecision`·`place_decision`에 `searchQuery`, `kakaoPlaceId`를 추가 | Worker JSON 회귀 테스트, 전체 `clean build` 통과 |
| [P1 fallback 활성화](https://github.com/team-youngkk/masit-on/pull/278#discussion_r3829698401) | Release holdout 전 완화 매칭이 기본 활성화됨 | 애플리케이션 | 수정 필요 | `AI_PLACE_IDENTITY_RELAXED_MATCHING_ENABLED` feature flag를 추가하고 기본값을 `false`로 설정 | 비활성 플래그 회귀 테스트, 전체 `clean build` 통과 |
| [P1 장소 감사 계약](https://github.com/team-youngkk/masit-on/pull/278#discussion_r3829698404) | 문서의 장소 감사 보장과 저장 구현이 불일치 | 애플리케이션 | 수정 필요 | 요구사항·API·ADR·데이터 계약을 실제 decision JSON 필드와 일치시킴 | 전체 `clean build` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 직접적인 런타임 오류는 없었으며, 리뷰에서 계약-구현 불일치가 발견됐다.
- 발생 환경: Windows, Java 21, 브랜치 `feature/ws-15-kakao-place-name-matching`, AI 장소명 부분 일치 검증 변경.
- 재현 조건: exact-name 후보가 없고 이름 containment 후보가 존재하는 경우, 완화 매칭 feature flag가 꺼져 있으면 운영 경로는 `PLACE_NOT_FOUND`로 차단한다. 활성화된 테스트 정책에서는 후보를 평가하고 결과 JSON을 생성한다.
- 실제 결과: 기존 구현은 `ResolvedFoodCategory.matchedMappingId`와 `PlaceStep.kakaoPlaceId`·검색어를 최종 결정 JSON에 보존하지 않았고, fallback은 별도 운영 게이트 없이 평가됐다.
- 기대 결과: Accepted 데이터 계약의 판정 근거를 재현할 수 있어야 하며, Release holdout·인간 판정 승인 전에는 fallback이 운영에서 실행되지 않아야 한다.
- 영향 범위: 자동 등록의 오연결 위험, 과거 카테고리 판정 재현성, 관리자 감사 조회와 운영 활성화 판단.

## 4. 근본 원인

1. `RegistrationUnitExecutionService`가 카테고리 해석 결과의 `matchedMappingId`를 3개 필드짜리 `CategoryStep`으로 축약해 직렬화 경계에서 버렸다.
2. `PlaceDecision`이 URL·도로명주소·`matchedBy`만 표현해 검색어와 최종 Kakao 장소 ID를 감사 JSON으로 전달할 수 없었다.
3. `ResolvePlaceIdentityService`가 exact 후보가 없으면 운영 활성화 상태와 무관하게 fallback을 평가했다. PR 문서의 품질 게이트 보류 조건이 실행 정책으로 연결되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| Accepted 데이터 계약 5.2절과 `CategoryDecision`·JSON 직렬화 대조 | 계약은 매핑 행 ID와 당시 카테고리 값을 요구하지만 구현은 ID를 버림 | 매핑 ID를 결정 JSON에 추가 |
| BR-AIEXTRACT-009와 `PlaceDecision`·Worker 직렬화 대조 | 문서는 검색어·장소 식별자를 약속하지만 구현은 URL·주소·`matchedBy`만 저장 | 검색어·Kakao 장소 ID를 결정 JSON에 추가 |
| `ResolvePlaceIdentityService`의 exact 부재 경로와 운영 설정 대조 | fallback 실행 조건에 운영 게이트가 없음 | Application Port 정책과 기본 비활성 설정 추가 |
| `git diff --check` 및 관련 테스트 | 통과 | 전체 빌드로 확대 검증 |

## 6. 최종 해결

- 변경 내용:
  - `PlaceIdentityMatchingPolicy`와 `PlaceIdentityMatchingProperties`를 추가하고 `AI_PLACE_IDENTITY_RELAXED_MATCHING_ENABLED=false`를 기본값으로 설정했다.
  - `PlaceDecision`에 `searchQuery`, `kakaoPlaceId`를 추가하고 `place_decision` JSON에 저장한다.
  - `CategoryDecision`에 `matchedMappingId`를 추가하고 `category_decision` JSON에 저장한다. 수동 카테고리 보정은 `null`이다.
  - 요구사항·PRD·API·데이터 계약·ADR을 실제 저장 필드와 활성화 경계에 맞춰 갱신했다.
- 선택 이유: Accepted 데이터 계약의 과거 판정 재현성 요구를 유지하면서, 품질 게이트 전 자동 완화 판정이 운영에 노출되지 않도록 fail-closed 기본값을 적용했다.
- 변경 파일: `src/main/java/com/masiton/orchestration/application/`, `src/main/java/com/masiton/ai/application/RegistrationUnitJsonSupport.java`, `src/main/resources/application.yml`, 관련 테스트와 `docs/01-requirements/`, `docs/04-product/`, `docs/05-specs/`, `docs/07-adr/`.
- 수정 커밋: [8652d35a](https://github.com/team-youngkk/masit-on/commit/8652d35a)

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 공백·패치 오류 없음 |
| `.\gradlew.bat test --rerun-tasks --tests ...` | 통과 | 장소 판정, 등록 단위 실행, Worker 결정 JSON 회귀 통과 |
| `.\gradlew.bat clean build` | 통과 | 전체 단위·통합·아키텍처 테스트와 패키징 통과 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 완화 매칭 비활성 플래그 회귀 테스트, Worker 결정 JSON의 장소 ID·매핑 ID 검증을 추가하고, Accepted 데이터 계약의 감사 필드 설명을 구현과 동기화했다.
- 다음 확인: Release holdout 24건과 인간 판정 승인을 완료한 뒤 운영 환경에서만 `AI_PLACE_IDENTITY_RELAXED_MATCHING_ENABLED=true`를 검토한다. PR의 최소 2명 승인도 완료해야 한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| fallback 자동 등록 정밀도·Critical 오연결 | 미측정 | Release holdout 24건과 인간 판정 | 확인 예정 | 품질 게이트 전 운영 활성화 금지 | AI/restaurant 소유자, Release holdout 완료 시 |
| 판정 JSON 근거 재현성 | 매핑 ID·장소 ID 일부 누락 | 자동 등록 결과 JSON과 기준정보 비활성화 후 대조 | 확인 예정 | 이번 변경으로 필요한 식별자 보존 | PR #278 후속 검증 |

## 10. 남은 사항

- 리뷰 스레드 4건은 수정·검증·답변 후 해결 처리한다.
- Release holdout·인간 판정 승인과 PR 승인 절차는 아직 남아 있다.
