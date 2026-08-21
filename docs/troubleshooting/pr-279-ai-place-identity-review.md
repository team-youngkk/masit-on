---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../08-planning/third-expansion-test-matrix.md
  - pr-278-kakao-place-name-matching-review.md
---

# PR #279 리뷰 트러블슈팅: 장소명 정규화·감사 값 호환·운영 기본값

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#279 AI 장소명 완화 매칭 운영 기본값과 역방향 판정 보강](https://github.com/team-youngkk/masit-on/pull/279) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-21 |
| 범위 | 미해결 리뷰 스레드 5건: 입력 끝 공백·괄호 지점 표기, legacy `matchedBy` 조회 호환, 운영 기본값 정책 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 / 배포 |
| 기존 기록 | [PR #278 리뷰 기록](pr-278-kakao-place-name-matching-review.md)을 확인했다. PR #278의 기본값·활성화 게이트 서술은 역사적 기록으로 유지하고, 이번 PR의 현재 운영 정책과 구분했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [PR #279](https://github.com/team-youngkk/masit-on/pull/279) (`PRRT_kwDOTf2xKc6bJSIu`) | 끝 공백 입력을 정규화한 뒤 기본명 검색 | 애플리케이션 | 수정 필요 | `stripBranchSuffix`가 접미사 판정 전에 `strip()`하도록 수정하고 끝 공백 회귀 테스트 추가 | `ResolvePlaceIdentityServiceTest` 통과 |
| [PR #279](https://github.com/team-youngkk/masit-on/pull/279) (`PRRT_kwDOTf2xKc6bJSIy`) | PR #278의 기존 `matchedBy` JSON이 API 계약 밖이 되지 않도록 호환 | 데이터베이스 | 수정 필요 | legacy 값을 조회 허용 값으로 문서화하고 상세 응답 보존 회귀 테스트 추가. 기존 JSON 데이터는 변경하지 않음 | `AdminAiVideoExtractionControllerTest` 통과 |
| [PR #279](https://github.com/team-youngkk/masit-on/pull/279) (`PRRT_kwDOTf2xKc6bJTcN`) | Release holdout 전 기본값을 `false`로 유지 | 배포 | 수정 불필요 | 현재 요구사항이 운영 기본값 `true`와 환경 변수 `false` 긴급 차단을 명시한다. 전체 AI 자동 등록의 품질·확장 게이트와 장소명 런타임 flag를 문서에서 분리했다 | 요구사항·PRD·ADR·평가 문서·테스트 매트릭스 대조 |
| [PR #279](https://github.com/team-youngkk/masit-on/pull/279) (`PRRT_kwDOTf2xKc6bJVd6`) | `우래옥 (본점)` 형태도 기본명으로 정규화 | 애플리케이션 | 수정 필요 | 괄호 지점 표기 회귀 테스트를 추가하고 정규화된 상호명으로 접미사를 판정 | `ResolvePlaceIdentityServiceTest` 통과 |
| [PR #279](https://github.com/team-youngkk/masit-on/pull/279) (`PRRT_kwDOTf2xKc6bJVd-`) | 기존 `NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY` 저장 값 호환 | 데이터베이스 | 수정 필요 | 새 실행은 새 값만 생성하고 기존 값은 legacy 감사 값으로 API·데이터 계약에 남김 | `AdminAiVideoExtractionControllerTest` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `PLACE_NOT_FOUND` 또는 API 계약상 허용되지 않은 `matchedBy` 값으로 해석될 위험.
- 발생 환경: PR #279 브랜치, Java 21, Spring Boot 설정, `ai_registration_unit.place_decision` JSONB 조회.
- 재현 조건:
  - AI 상호명이 `우래옥 본점 `처럼 끝 공백을 포함한다.
  - AI 상호명이 `우래옥 (본점)`처럼 괄호 지점 표기를 포함한다.
  - PR #278에서 생성된 `NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY` JSON을 관리자 상세 API로 조회한다.
- 실제 결과: 접미사 정규화 전 입력이 공백을 포함하면 기본 상호명 2차 검색이 누락될 수 있었고, 문서가 새 `matchedBy` 값만 설명하면 과거 감사 JSON과 계약이 어긋났다.
- 기대 결과: 입력 표기 변형은 기본 상호명 검색으로 수렴하고, 기존 감사 JSON은 데이터 변경 없이 조회 가능해야 한다.
- 영향 범위: AI 자동 장소 확정, 관리자 상세 조회, 과거 판정 감사와 클라이언트의 `matchedBy` 해석.

## 4. 근본 원인

1. `ResolvePlaceIdentityService.stripBranchSuffix`가 원문을 정규화하기 전에 정규식을 적용했다.
2. PR #279에서 새 `matchedBy` 문자열을 도입했지만, PR #278의 JSONB 감사 값은 별도 마이그레이션 없이 그대로 남아 있었다.
3. 운영 기본값 리뷰는 전체 AI 자동 등록의 Release 품질 게이트와 장소명 완화 매칭의 현재 운영 정책을 같은 flag로 해석한 문서 경계 문제였다. 현재 요구사항은 장소명 완화 매칭 기본값 `true`, 긴급 차단 `false`를 요구하므로 코드 값을 변경하지 않고 평가 문서를 두 게이트로 명확히 구분했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `ResolvePlaceIdentityService`와 기존 지점 표기 테스트 대조 | 끝 공백과 괄호 입력에 대한 직접 회귀가 없었다 | 정규화 시점을 접미사 판정 전으로 이동하고 두 입력을 테스트 |
| PR #278의 `NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY` 저장 경로와 관리자 상세 응답 대조 | 조회 경로는 JSON을 그대로 `JsonNode`로 반환하고 데이터 마이그레이션은 없다 | legacy 값을 조회 계약에 남기고 응답 보존 테스트 추가 |
| 요구사항·PRD·ADR·평가·테스트 매트릭스의 운영 활성화 문장 대조 | 품질 게이트와 런타임 flag가 혼용될 여지가 있었다 | 현재 기본값 `true` 정책과 전체 AI 자동 등록 go/no-go를 문서에서 분리 |
| 기존 `독점`, `우래옥 본점`, `우래옥 지점` 회귀 테스트 실행 | 명시적 지점 표기만 제거되고 일반 단어는 보존됨 | 접미사 정규식 범위를 유지 |

## 6. 최종 해결

- 변경 내용:
  - 상호명 끝 공백을 제거한 뒤 지점 접미사를 판정한다.
  - 괄호 지점 표기와 끝 공백의 기본명 검색 회귀 테스트를 추가했다.
  - 기존 `NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY`를 legacy 감사 값으로 API·데이터·요구사항 문서에 명시했다.
  - 전체 AI 자동 등록의 품질 게이트와 장소명 완화 매칭 런타임 기본값을 분리해 기록했다.
- 선택 이유: 기존 JSONB를 일괄 변경하지 않아 과거 감사 재현성을 보존하면서 새 실행의 문자열만 정리할 수 있다.
- 변경 파일: `src/main/java/com/masiton/orchestration/application/ResolvePlaceIdentityService.java`, 관련 테스트, 장소 동일성 계약·평가 문서, 이 기록과 인덱스.
- 고려한 대안: JSONB 전체 마이그레이션은 과거 판정 값의 의미를 바꾸므로 적용하지 않았다. 새 값 생성과 legacy 조회 허용으로 전환 경계를 명시했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 패치 공백 오류 없음 |
| `ResolvePlaceIdentityServiceTest` | 통과 | 끝 공백·괄호·지점·독점·중복·flag 경계 검증 |
| `AdminAiVideoExtractionControllerTest` | 통과 | legacy `matchedBy` JSON 상세 응답 보존 검증 |
| `DockerComposeLocalAiContractTest` | 이전 통과 | 긴급 차단 환경 변수 Compose 전달 계약 유지 |
| `.\gradlew.bat test --no-daemon --console=plain` | 통과 | 코드·계약·통합 전체 회귀 검증 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 입력 정규화 경계와 legacy 감사 값 조회 호환을 자동화 테스트·데이터/API 계약에 연결했다.
- 다음 확인: 실제 Release holdout 24건·인간 판정·E3-T13 전체 AI 자동 등록 go/no-go는 품질 평가 담당자가 별도 실행한다. 장소명 flag 기본값 변경 자체를 이 평가 미실행의 근거로 사용하지 않는다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 끝 공백·괄호 지점 표기의 장소 확정률 | 미측정 | 동일한 표기 변형 holdout을 `matchedBy`별로 집계 | 확인 예정 | 이번 처리에서는 회귀 통과만 확인 | AI/restaurant 소유자, Release holdout 완료 시 |
| legacy `matchedBy` 조회 실패율 | 미측정 | 기존 JSONB 감사 행을 관리자 상세 API로 조회해 오류·값 보존을 집계 | 확인 예정 | 데이터 마이그레이션 없이 조회 호환 테스트 통과 | AI 소유자, 다음 관리자 API 검증 시 |

## 10. 남은 사항

- 리뷰어의 Release holdout·인간 판정·E3-T13 전체 품질 게이트 요청은 별도 운영 평가로 남아 있다. 장소명 완화 매칭의 현재 운영 기본값 정책과 충돌하지 않도록 문서에서 범위를 구분했다.
- 최신 수정 커밋을 원격 PR에 반영했고, 다섯 스레드에 처리 결과 답글을 남겼다. 수정 필요 스레드는 해결 처리했으며, 운영 기본값 정책 스레드는 현재 요구사항에 따른 수정 불필요로 처리했다.
