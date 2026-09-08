---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-test-matrix.md
  - admin-tag-definition-creation.md
---

# 동적 자연어 태그 사전 구현 계획

## 1. 배경과 목표

이슈 [#364](https://github.com/team-youngkk/masit-on/issues/364)는 자연어 파서에 하드코딩된 초기 18개 태그 때문에 #363에서 새로 만든 활성 태그의 표시명·별칭을 검색 문장으로 사용할 수 없는 문제를 해결한다. WS-14 자연어 검색은 `ACTIVE` 태그 정의를 Application Port로 읽고, 지역·카테고리·유튜버와 기존 목록 Query를 조합한다.

이 변경은 태그 정의를 새로 소유하거나 검색 이력을 저장하지 않는다. WS-15가 소유한 `tag_definition`·`tag_definition_term`을 읽기 전용으로 사용하고 기존 Restaurant·Creator·Visit 공개·생명주기 규칙을 유지한다.

## 2. 확정 범위

- `ACTIVE` `tag_definition`의 `tag_code`, `display_name`, `aliases`를 자연어 태그 사전으로 제공하는 WS-14 Application output Port
- `DEPRECATED` 정의와 자동 검증 전 AI 후보 제외
- #363 `TagTermNormalizer`와 같은 Unicode NFKC·Unicode 공백·ASCII 소문자 정규화
- 결정적인 별칭 판정과 태그 코드 반환 순서
- 별칭의 다중 활성 코드 매핑과 자연어 6개 이상 태그 인식에 대한 전체 `tags` unresolved
- 라이브러리 없는 프로세스 내 read-through cache, TTL 30초, 동시 갱신 단일화
- 최초 적재·만료 갱신 DB 실패의 `NATURAL_LANGUAGE_UNAVAILABLE` 503 fail-closed
- 초기 18개 seed Golden V1, 지역·카테고리·유튜버 조합, 직접 필터 우선, 같은 Visit 태그 AND, 최대 5개 회귀
- 요구사항·PRD·API·데이터·ADR·추적표와 구현·검증 동기화

## 3. 제외 범위

- 공개 활성 태그 선택 목록 API와 직접 `filters.tags` 화면 상수의 동적 전환
- 태그 정의 생성(#363), 수정·비활성화·감사(#365), 병합·VisitTag 이전(#366)
- 기존 지역·카테고리 사전의 DB 이전
- Creator 선택 정보 공급 방식 변경
- 임베딩·RAG·LLM 해석, 오타 교정, 검색 원문·이력 저장
- 새 cache 라이브러리·Redis cache·분산 무효화
- `parserVersion` 증가

## 4. 애플리케이션과 데이터 경계

### 4.1 Port와 snapshot

Restaurant 자연어 검색 Application 계층은 활성 태그 사전 조회를 자신의 output Port로 정의한다. Port 결과는 다음 값만 가진 불변 snapshot이다.

- 활성 태그 코드
- 표시명과 별칭의 정규화 용어
- 같은 요청 안에서 고정된 사전 내용

인프라 Adapter는 `tag_definition`과 `tag_definition_term`을 읽어 `status = ACTIVE`인 정의만 반환한다. Application·Domain은 JDBC, JPA Entity, AI 도메인 저장소를 직접 참조하지 않는다. 조회는 `tag_code`와 정규화 용어의 결정적 순서로 반환하고 자연어 parser는 요청 도중 snapshot을 다시 읽지 않는다.

### 4.2 정규화와 결정성

표시명·별칭은 #363과 같은 순서로 정규화한다.

1. Unicode NFKC
2. 앞뒤 Unicode 공백 제거
3. 연속 Unicode 공백을 ASCII 공백 한 칸으로 축약
4. ASCII `A-Z`를 `a-z`로 변환

별칭 후보는 정규화 문자열 길이 내림차순 후 사전순으로 판정한다. 적용 태그 코드는 `tag_code` 사전순으로 반환한다. DB 물리 순서, JSON 배열 순서, HashMap 순회 순서와 요청 동시성은 응답 순서를 바꾸지 않아야 한다.

`tag_definition_term.normalized_term`의 전역 unique 때문에 정상 데이터에는 한 용어의 다중 정의 매핑이 없다. 그래도 Port fixture나 불일치 snapshot에서 같은 정규화 용어가 둘 이상의 `ACTIVE` 코드에 연결되면 임의 선택하지 않고 자연어 `tags` 조건 전체를 `UNRESOLVED_VALUE`로 처리한다.

### 4.3 태그 수와 필터 병합

- 자연어에서 1~5개 태그를 인식하면 코드를 사전순으로 적용한다.
- 6개 이상을 인식하면 앞의 5개만 적용하지 않고 자연어 `tags` 전체를 unresolved로 처리한다.
- 직접 `filters.tags`가 있으면 기존 직접 필터 우선 규칙에 따라 직접 값을 적용하고 `DIRECT_FILTER_WON` 충돌 정보를 반환한다. 자연어 태그의 모호성·상한 위반을 직접 필터 값으로 추정해 해소하지 않는다.
- 여러 적용 태그는 같은 공개·유효 Visit에 모두 연결돼야 하는 기존 AND 의미를 유지한다.
- 태그 unresolved가 있어도 독립적으로 해석된 지역·카테고리·유튜버 조건은 `PARTIAL` 결과에 사용할 수 있다. 적용 가능한 조건이 하나도 없으면 `FAILED`이며 전체 목록으로 대체하지 않는다.

## 5. Cache와 실패 계약

Application의 동적 태그 사전 공급 Adapter는 외부 라이브러리 없이 프로세스 메모리에 불변 snapshot을 저장한다.

- TTL은 성공한 적재 시점부터 30초다.
- TTL 안에는 같은 snapshot을 반환하므로 태그 생성·수정·폐기 반영이 최대 30초 늦을 수 있다.
- cache가 없거나 만료된 뒤 첫 요청이 DB를 조회한다. 동시 요청은 갱신을 하나로 모으고 같은 성공 snapshot을 공유한다.
- 최초 적재가 실패하면 요청을 `NATURAL_LANGUAGE_UNAVAILABLE` 503으로 종료한다.
- 만료 갱신이 실패해도 stale snapshot과 초기 18개 seed를 반환하지 않고 같은 503으로 종료한다.
- 다음 요청은 다시 갱신할 수 있다. 실패를 새 30초 성공 snapshot으로 간주하지 않는다.
- 자연어 API의 실패는 `GET /api/restaurants`와 상세·관리자 태그 API에 전파하지 않는다.

이 정책은 사전 DB 상태와 다른 검색 해석을 조용히 제공하지 않기 위한 fail-closed 선택이다. 별도 cache 무효화는 #365의 수정·폐기 구현과 결합하지 않고 짧은 TTL로 경계를 유지한다.

## 6. Parser 버전과 호환성

`parserVersion: P1`은 문장 패턴, 조건 추출, 충돌, 최대 태그 수와 정규화 알고리즘의 계약 버전이다. `ACTIVE` 태그 정의의 코드·표시명·별칭 데이터가 바뀌는 것은 같은 알고리즘의 입력 사전 변경이므로 `P1`을 유지한다.

V4 초기 18개 seed의 코드·표시명·별칭은 Golden V1 기준이다. 동적 사전 전환 전후에 기존 문장의 적용 조건·ignored condition·conflict·status가 같아야 한다. 지역·카테고리·유튜버 동시 조합, 직접 필터 우선, 같은 Visit 태그 AND와 최대 5개 계약도 유지한다.

정규화 순서, 단어 경계, 별칭 충돌 의미, 5개 상한 또는 조건 병합 의미가 바뀌면 데이터 변경과 구분해 parserVersion 증가와 새 Golden 버전을 검토한다.

## 7. 구현 순서와 소유 범위

### 단계 1. 문서 계약

- FR-NLSEARCH-004와 BR-NLSEARCH-003에 동적 사전·모호성·상한을 반영한다.
- 자연어 PRD·API·ADR에 cache·503·P1 의미를 동기화한다.
- 데이터 계약과 추적표에서 `tag_definition_term`의 WS-14 읽기 경계를 연결한다.

### 단계 2. Port와 DB Adapter

- Restaurant 자연어 검색 Application output Port와 불변 사전 항목을 추가한다.
- 인프라 Adapter가 `ACTIVE` 정의의 코드와 정규화 표시명·별칭을 읽는다.
- 비활성 제외, 정렬, 빈 활성 목록과 DB 예외를 계약에 맞게 변환한다.

### 단계 3. Cache와 parser 조합

- 30초 read-through cache와 동시 갱신 단일화를 구현한다.
- 자연어 parser가 요청마다 같은 사전 snapshot과 기존 Creator 사전을 조합한다.
- 하드코딩 태그를 운영 fallback으로 사용하지 않되 테스트 전용 명시적 dictionary 생성은 단위 테스트 경계에 유지할 수 있다.
- 모호한 별칭과 6개 이상 태그를 자연어 `tags` 전체 unresolved로 만들고 결정적 순서를 적용한다.

### 단계 4. 오류와 회귀 검증

- 최초 적재·만료 갱신 실패를 `NATURAL_LANGUAGE_UNAVAILABLE` 503과 공통 `traceId`로 연결한다.
- seed 18개 Golden V1과 기존 조건 병합·태그 AND를 회귀한다.
- clean build와 관련 단위·통합·API 테스트 결과를 PR에 기록한다.

## 8. 필수 검증

| 범위 | 시나리오 |
|---|---|
| 활성 상태 | 새 `ACTIVE` 표시명·별칭 인식, `DEPRECATED` 제외, 상태 변경이 TTL 만료 뒤 반영 |
| 정규화 | NFKC 호환 문자, 앞뒤·연속 Unicode 공백, ASCII 영문 대소문자와 #363 corpus 동등성 |
| 결정성 | 별칭 길이 동률 사전순, DB·JSON 입력 순서가 달라도 동일 코드 사전순 응답 |
| 모호성 | 같은 정규화 별칭의 다중 활성 코드 fixture에서 자연어 `tags` 전체 `UNRESOLVED_VALUE`, 다른 조건은 `PARTIAL` 유지 |
| 상한 | 자연어 태그 5개 적용, 6개 전체 unresolved, 직접 `filters.tags` 우선·충돌 정보 유지 |
| cache | 최초 miss 1회 조회, TTL 안 재사용, 만료 뒤 갱신, 동시 만료 요청의 DB 조회 단일화, 성공 snapshot 불변성 |
| 실패 | 최초 DB 실패와 만료 갱신 실패 모두 503·traceId, stale/seed 폴백 0건, 기존 구조화 API 정상 |
| 검색 의미 | 여러 태그 같은 Visit AND, 다른 Visit에 분산된 태그 불일치, 공개·유효 관계만 사용 |
| 회귀 | 초기 18개 seed Golden V1, 지역·카테고리·유튜버 조합, 직접 필터 우선, `parserVersion: P1` |
| 전체 | 관련 단위·JDBC·MockMvc 테스트와 `clean build` 통과 |

DB Adapter 통합 테스트는 운영 seed를 삭제하거나 교체해 통과시키지 않는다. 동시성 테스트는 임의 대기나 실행 순서에 의존하지 않고 제어 가능한 clock과 동기화 지점을 사용한다.

## 9. 완료 조건

- 문서 계약과 구현이 같은 PR에서 일치한다.
- 새 `ACTIVE` 태그의 표시명·별칭이 최대 30초 안에 자연어 검색에 반영되고 `DEPRECATED` 태그는 제외된다.
- 다중 코드 별칭과 6개 이상 자연어 태그를 부분 적용하지 않는다.
- 최초 적재·만료 갱신 DB 실패가 503으로 fail-closed 되고 stale·seed 폴백이 없다.
- 동시 요청이 하나의 불변 갱신 snapshot을 공유한다.
- P1·초기 18개 Golden V1·지역/카테고리/유튜버 조합·직접 필터 우선·같은 Visit 태그 AND가 회귀하지 않는다.

## 10. 구현 중 결정 요청 기준

다음 상황은 계약을 임의로 바꾸지 않고 선택지·영향·추천안을 사용자에게 제시한다.

- #363 정규화와 자연어 문장 정규화 결과가 기존 Golden V1을 동시에 만족할 수 없는 경우
- 현재 데이터에서 서로 다른 활성 정의가 같은 정규화 용어를 가진 것이 발견된 경우
- 30초 TTL 또는 fail-closed 정책이 운영 가용성 목표를 만족하지 못해 stale 제공·명시적 무효화가 필요한 경우
- 기존 API 오류 코드·응답 Schema·parserVersion을 바꿔야 하는 경우
- Application Port를 위해 WS-14가 WS-15 Entity·Repository에 직접 의존해야 하는 구조만 가능한 경우
