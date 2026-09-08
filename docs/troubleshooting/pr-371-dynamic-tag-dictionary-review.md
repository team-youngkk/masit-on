---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - pr-169-natural-language-search-review.md
  - pr-176-natural-language-review.md
---

# PR #371 리뷰 트러블슈팅: 동적 태그 코드와 중첩 용어 해석

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#371](https://github.com/team-youngkk/masit-on/pull/371) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-09-08 |
| 범위 | 동적 태그 사전의 태그 코드 누락과 긴 용어·접두사 용어 중첩 리뷰 2건 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #169 자연어 검색 입력·조건 경계](pr-169-natural-language-search-review.md)와 [PR #176 자연어 검색 표시·별칭 경계](pr-176-natural-language-review.md)를 확인했다. 기존 단어 경계·결정적 별칭 판정 원칙을 유지하고, 이번에는 동적 사전 조립과 태그 용어 간 점유 구간을 보완했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [긴 태그 용어와 짧은 접두사 중첩](https://github.com/team-youngkk/masit-on/pull/371#discussion_r3954553663) | 긴 용어가 일치한 구간에서 접두사인 짧은 용어를 중복 적용하지 않도록 보완 | 애플리케이션 | 수정 필요 | 길이 내림차순으로 찾은 일치 구간을 점유하고, 뒤의 짧은 용어는 겹치지 않는 별도 출현에서만 적용 | `family dinner place`는 긴 태그만, 같은 문장 뒤 별도 `family` 출현은 두 태그를 적용하는 parser 테스트 통과 |
| [동적 사전의 태그 코드 누락](https://github.com/team-youngkk/masit-on/pull/371#discussion_r3954553668) | 코드·표시명·별칭 계약대로 태그 코드 자체도 검색 용어에 포함 | 애플리케이션 | 수정 필요 | 동적 `tagTerms` 조립도 정적 `tag` 경로처럼 정규화한 `tagCode`를 용어에 포함 | `TAG_FAMILY 맛집`을 `TAG_FAMILY` 조건으로 적용하는 parser 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 자연어 해석 결과에 잘못된 태그가 추가되거나 계약상 지원하는 태그 코드가 누락되는 정확성 문제다.
- 발생 환경: Java 21, Gradle Wrapper 8.14.3, `feature/t-364-dynamic-tag-dictionary` 브랜치의 P1 parser.
- 재현 조건: 서로 다른 활성 태그에 `family`와 `family dinner`가 각각 등록된 상태에서 `family dinner place`를 입력하거나, 활성 태그 코드 `TAG_FAMILY`를 문장에 직접 입력한다.
- 실제 결과: 첫 입력은 두 태그가 모두 적용돼 AND 검색 결과를 누락시키고, 두 번째 입력은 태그가 없는 문장으로 처리됐다.
- 기대 결과: 긴 용어가 차지한 구간에서는 접두사 용어를 중복 적용하지 않고, 활성 태그의 코드·표시명·별칭을 모두 검색 용어로 사용해야 한다.
- 영향 범위: 공개 자연어 검색의 태그 해석과 그 결과를 사용하는 관리자 확정 `VisitTag` AND 조회다. DB와 API Schema 변경은 없다.

## 4. 근본 원인

태그 추출은 용어를 길이순으로 정렬했지만 각 용어의 포함 여부만 독립적으로 검사해, 먼저 찾은 긴 용어의 문자 구간을 이후 후보에서 제외하지 않았다. 따라서 접두 관계인 두 용어가 같은 문자 구간을 근거로 서로 다른 태그를 동시에 추가했다.

동적 사전 조립은 `tagTerms`에서 공통 builder의 `includeValue`를 `false`로 전달했다. 이 경로는 DB snapshot의 표시명·별칭만 등록하고 `tagCode`를 제외했으며, 코드·표시명·별칭으로 사전을 구성한다는 FR-NLSEARCH-004와 BR-NLSEARCH-003 계약 및 정적 `tag()` 경로와 달랐다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 두 리뷰 재현 테스트를 수정 전에 실행 | parser 테스트 22건 중 해당 2건 실패 | 두 의견 모두 현재 코드에서 재현돼 `수정 필요`로 분류 |
| BR-NLSEARCH-003과 ADR-ARCH-005 확인 | 활성 태그의 코드·표시명·별칭 사용과 길이 내림차순 판정이 명시됨 | 계약 변경 없이 누락된 코드와 길이 우선 판정을 구현 |
| 짧은 용어를 사전 전체에서 제거하는 방안 검토 | 문장의 다른 위치에 독립적으로 나온 짧은 용어까지 잃음 | 일치 용어가 아닌 문자 구간 단위 점유를 선택 |
| 중첩이 있으면 tags 전체를 `UNRESOLVED`로 처리하는 방안 검토 | 기존 길이 우선 판정 규칙보다 정상 해석 범위를 불필요하게 줄임 | 긴 용어 우선, 겹치지 않는 출현 허용으로 결정 |

## 6. 최종 해결

- 변경 내용: 동적 태그 사전에 정규화한 태그 코드를 포함하고, 태그 별칭의 모든 일치 구간을 찾아 길이 내림차순으로 점유한 뒤 겹치지 않는 구간만 후속 용어에 허용했다.
- 선택 이유: 기존 P1의 길이 우선·결정적 정렬 계약을 유지하면서 같은 문자 구간의 이중 해석만 제거하고, 문장 안에 별도로 등장한 짧은 용어는 보존하기 때문이다.
- 변경 파일: `NaturalLanguageDictionary.java`, `NaturalLanguageRestaurantParser.java`, `NaturalLanguageRestaurantParserTest.java`, 이 문서와 `docs/troubleshooting/README.md`.
- 고려한 대안: 모든 중첩을 미해석 처리하거나 짧은 용어를 전역 제외하는 방법은 정상 입력까지 제거해 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests 'com.masiton.restaurant.application.naturallanguage.NaturalLanguageRestaurantParserTest' --no-daemon --console=plain` | 통과 | parser 22건. 태그 코드 입력, 같은 구간의 접두사 제외, 별도 위치의 짧은 용어 적용과 기존 자연어 경계 회귀 확인 |
| `.\gradlew.bat clean test --tests 'com.masiton.restaurant.application.naturallanguage.*Test' --tests 'com.masiton.restaurant.infrastructure.persistence.JdbcActiveTagDictionaryAdapterTest' --tests 'com.masiton.architecture.ArchitectureTest' --no-daemon --console=plain` | 통과 | Golden V1 240문장 동적 경로, 사전 연결·cache, 자연어 병합, 정규화와 ArchUnit 회귀 확인 |
| `.\gradlew.bat build -x test --no-daemon --console=plain` 및 `git diff --check` | 통과 | 운영·테스트 소스 컴파일, 패키징과 whitespace 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 동적 사전의 태그 코드 포함과 중첩·비중첩 용어를 같은 회귀 테스트에 고정했다.
- 다음 확인: 원격 브랜치 반영 뒤 PR CI 전체 검증 결과를 이 문서에 추가한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 확정 재현 입력 실패 수 | 2종 중 2종 실패 | 리뷰 재현 입력을 parser 단위 테스트로 실행 | 2종 중 0종 실패 | 확정 재현 결함 2건 해소 | 양성훈 / PR #371 검증 시점 |
| 운영 오류율·처리 시간 | 해당 없음(배포 전 자연어 해석 정확성 수정) | 해당 없음 | 해당 없음 | 자동 회귀 테스트로 대체 | 해당 없음 |

## 10. 남은 사항

- 원격 브랜치의 전체 CI 통과와 리뷰 스레드 답글·해결 처리가 남아 있다.
