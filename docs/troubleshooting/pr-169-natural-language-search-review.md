---
related_documents:
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../05-specs/api/common/identifier-contract.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../06-architecture/security-boundary.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - pr-129-deploy-cutover-and-rate-limit-review.md
  - pr-139-popular-restaurant-security-boundary.md
---

# PR #169 리뷰 트러블슈팅: 자연어 검색 입력·조건·요청 출처 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#169](https://github.com/team-youngkk/masit-on/pull/169) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-11 |
| 범위 | 자연어 검색의 fail-closed 처리, 태그 lifecycle, 직접 필터 병합, 요청 출처, 요청 DTO, Creator 식별자 |
| 주 문제 유형 | 애플리케이션, 인프라, 데이터베이스 |
| 기존 기록 | [PR #129](pr-129-deploy-cutover-and-rate-limit-review.md)의 trusted proxy 경계와 [PR #139](pr-139-popular-restaurant-security-boundary.md)의 공개 경계 검토 결과를 확인하고 `MapClientAddressResolver`를 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [악성 입력 fail-closed](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965120) | 지원 조건이 섞인 악성 입력도 결과를 내지 않도록 처리 | 애플리케이션 | 수정 필요 | 추출 전에 의심 표현을 감지해 `FAILED`, 빈 적용 조건과 빈 결과로 종료 | parser·API 회귀 테스트 통과 |
| [프록시 요청 출처](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965121) | Nginx 뒤에서도 사용자별 rate-limit 분리 | 인프라 | 수정 필요 | trusted proxy만 단일 `X-Forwarded-For`를 사용하도록 기존 resolver 재사용 | 신뢰 프록시 전달 주소 API 테스트 통과 |
| [자연어 태그 lifecycle](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965122) | parser 태그도 ACTIVE인지 검증 | 데이터베이스 | 수정 필요 | 비활성 태그를 적용 조건에서 제거하고 `PARTIAL`/`FAILED` 및 ignored condition으로 표현 | DEPRECATED 태그 API 테스트 통과 |
| [동일 조건 충돌](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965124) | 같은 직접·자연어 조건은 conflict가 아님 | 애플리케이션 | 수정 필요 | scalar는 값 비교, tags는 집합 비교 후 실제 불일치에만 conflict 추가 | 동일 조건 API 테스트 통과 |
| [미지 요청 필드](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965625) | 최상위와 `filters`의 허용 외 필드를 400으로 거부 | 애플리케이션 | 수정 필요 | DTO의 `@JsonAnySetter`에서 즉시 역직렬화 실패를 발생시켜 400으로 매핑 | 두 경계의 미지 필드 API 테스트 통과 |
| [opaque Creator ID](https://github.com/team-youngkk/masit-on/pull/169#discussion_r3754965627) | Creator alias 사전에서 UUID 형식을 가정하지 않음 | 애플리케이션 | 수정 필요 | 사전 초기화의 `UUID.fromString` 검증을 제거하고 문자열을 그대로 연결 | non-UUID Creator ID parser 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 동일 조건 회귀 테스트 중 `No value supplied for the SQL parameter 'foodCategoryIdAND'`가 확인됐다.
- 발생 환경: Windows, Java 21, Gradle Wrapper 8.14.3, Testcontainers 기반 API 테스트, `feature/t-154-natural-language-search` 브랜치.
- 재현 조건: 의심 표현과 지원 조건의 혼합, DEPRECATED 태그 별칭, 동일한 직접·자연어 `category + tags`, 신뢰 프록시 뒤 요청, 미지 JSON 필드, non-UUID Creator ID.
- 실제 결과: 조건이 적용된 검색이 실행되거나, 비활성 태그가 APPLIED로 보이거나, 동일 조건이 conflict가 되거나, 미지 필드가 무시됐고, `category + tags`에서는 SQL 파라미터 결합으로 500이 발생했다.
- 기대 결과: 계약에 따라 의심 입력은 빈 결과로 fail-closed, 비활성 태그는 미적용 처리, 실제 불일치만 conflict, 미지 필드는 400, Creator ID는 opaque 문자열, 정상 조건 조합은 200이어야 한다.
- 영향 범위: 공개 자연어 검색 API의 검색 정확성·요청 제한 공정성·입력 경계이며 데이터 변경은 없다.

## 4. 근본 원인

의심 입력 검사가 추출 이후에 실행돼 이미 만든 조건을 비우지 않았고, 자연어 태그는 직접 필터와 달리 현재 ACTIVE 상태를 확인하지 않았다. 병합 로직은 값의 동등성 대신 자연어 조건 존재 여부만으로 conflict를 추가했다. Controller는 프록시 신뢰 경계를 거치지 않았고 DTO는 전역 ObjectMapper의 unknown property 무시 설정을 그대로 따랐다. Creator alias 사전은 외부 식별자 계약과 달리 UUID 형식을 강제했다. 추가로 tags SQL text block 앞 공백이 없어 `:foodCategoryIdAND`로 파싱됐다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API·ADR·식별자·태그 lifecycle 계약 확인 | 각 리뷰 요청이 계약과 일치 | 계약 변경 없이 구현 경계를 보완 |
| 기존 `MapClientAddressResolver`와 배포 설정 확인 | trusted proxy와 단일 전달 주소 검증이 이미 존재 | 별도 설정을 만들지 않고 재사용 |
| `@JsonIgnoreProperties(ignoreUnknown = false)` 적용 | 전역 ObjectMapper 설정 때문에 미지 필드가 계속 무시됨 | DTO별 `@JsonAnySetter` 거부로 변경 |
| 동일 `category + tags` API 회귀 실행 | `foodCategoryIdAND` SQL 파라미터 오류 재현 | tags SQL 앞 공백을 보완하고 정상 검색 확인 |

## 6. 최종 해결

- 변경 내용: parser 조기 fail-closed, 자연어 태그 ACTIVE 검증, 동등 조건 conflict 제거, trusted proxy resolver 적용, DTO 미지 필드 거부, opaque Creator ID 지원, tags SQL 공백 보완과 회귀 테스트를 추가했다.
- 선택 이유: 기존 공개 API·보안·데이터 계약과 resolver 설정을 유지하면서 요청 경계에서만 최소 수정으로 보장하기 때문이다.
- 변경 파일: `NaturalLanguageRestaurantParser`, `NaturalLanguageSearchService`, `NaturalLanguageSearchController`, `NaturalLanguageSearchRequest`, `NaturalLanguageDictionary`, `RestaurantSearchQueryAdapter` 및 관련 parser/API 테스트.
- 고려한 대안: 전역 ObjectMapper의 미지 필드 정책 변경은 다른 API에 영향을 줄 수 있어 DTO 경계 거부를 선택했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests 'com.masiton.restaurant.application.naturallanguage.NaturalLanguageRestaurantParserTest' --tests 'com.masiton.restaurant.presentation.naturallanguage.NaturalLanguageSearchApiTest' --no-daemon --console=plain` | 통과 | parser 12건, API 14건으로 6개 리뷰 시나리오와 기존 동작을 확인 |
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 운영·테스트 소스 컴파일 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 악성 혼합 입력, DEPRECATED 태그, 동일 scalar/tags, 미지 필드 두 경계, trusted proxy, opaque Creator ID를 회귀 테스트로 추가했다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 운영 오류율·처리 시간 | 해당 없음 | 배포 전 API 정확성·입력 경계 수정으로 동일 운영 기간 비교가 불가 | 해당 없음 | 계약 회귀 테스트로 대체 검증 | 해당 없음 |

## 10. 남은 사항

- 없음.
