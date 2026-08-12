---
related_documents:
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - pr-169-natural-language-search-review.md
---

# PR #176 리뷰 트러블슈팅: 자연어 검색 표시·별칭·평가 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#176](https://github.com/team-youngkk/masit-on/pull/176) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-08-12 |
| 범위 | 미해결 인라인 리뷰 10건의 재현, 수정 또는 수정 불필요 판단 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #169 자연어 검색 리뷰](pr-169-natural-language-search-review.md)를 확인했다. fail-closed와 opaque ID 계약은 재사용했고, 이번에는 화면 표시명·별칭 단어 경계·평가 지표 책임을 추가로 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r3763031610](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031610) | 다중 단어 별칭의 단어 경계 보존 | 애플리케이션 | 수정 필요 | 앞 글자 경계와 별칭 내부 선택 공백을 함께 검사 | parser 회귀 테스트 통과 |
| [r3763031616](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031616) | opaque Creator ID 비노출 | 애플리케이션 | 수정 필요 | 공개 Creator 목록의 ID→채널명 매핑 전달, 미확인 ID는 일반 문구 표시 | 프론트 표시 테스트 통과 |
| [r3763031619](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031619) | 태그 코드 비노출 | 애플리케이션 | 수정 필요 | V4 seed와 같은 18개 사용자용 라벨 매핑 적용 | 프론트 표시 테스트 통과 |
| [r3763031621](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031621) | 단일 미지 태그 토큰 누락 | 애플리케이션 | 수정 필요 | 토큰 수와 무관하게 미지 토큰을 독립 판정 | parser 회귀 테스트 통과 |
| [r3763031624](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031624) | 429 대기시간의 요청 지연 중복 | 애플리케이션 | 수정 필요 | 응답 반영 시각으로 `now` 갱신 | 타입 검사·프론트 회귀 통과 |
| [r3763031627](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031627) | 오적용 지표와 계약 위반 분리 | 애플리케이션 | 수정 필요 | 초과 atom만 오적용 수로 계산하고 상태·무시 문구는 사례별 계약 테스트로 이동 | 기본·holdout 평가 통과 |
| [r3763031629](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031629) | 별칭마다 문장 정규화 반복 제거 | 애플리케이션 | 수정 필요 | 소문자 문장을 parse당 한 번 만들고 하위 추출기에 전달 | parser·골든 평가 통과 |
| [r3763031631](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031631) | 공통 HTTP outcome 추출 | 애플리케이션 | 수정 불필요 | 지도·큐레이션·자연어의 상태 모델과 fallback 정책이 달라 이번 PR의 공통화는 범위를 넓힘 | 세 구현과 계약 비교, 전체 프론트 회귀 통과 |
| [r3763031632](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031632) | stale-request 패턴 통일 | 애플리케이션 | 수정 불필요 | 자연어 검색은 취소 가능한 동일 요청의 controller 동일성, 컬렉션은 여러 요청군의 sequence를 사용해 요구가 다름 | 현재 abort·unmount 가드와 타입 검사 확인 |
| [r3763031633](https://github.com/team-youngkk/masit-on/pull/176#discussion_r3763031633) | 효과 없는 초기 `setNow` 제거 | 애플리케이션 | 수정 필요 | 요청 시작 갱신 제거, 429 outcome 반영 시점에만 갱신 | 타입 검사·프론트 회귀 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 잘못된 조건 적용, 내부 식별자 표시, 재시도 지연과 평가 의미 혼합 문제다.
- 발생 환경: PR #176의 P1 parser와 `/restaurants` 자연어 검색 화면.
- 재현 조건: `알바분위기...`처럼 별칭이 앞 단어에 걸친 입력, 단일 미지 태그와 정상 태그의 혼합, Creator·태그 조건 검색, 지연된 429 응답.
- 실제 결과: `ATMOSPHERE_BAR` 오적용, 미지 태그 무시, opaque ID·태그 코드 노출, 서버 지시보다 긴 재시도 대기, 서로 다른 실패를 한 평가 수치에 합산했다.
- 기대 결과: 단어 경계를 지키고 미지원 조건을 남기며, 화면에는 사용자용 라벨만 표시하고, Retry-After와 평가 지표는 각각 단일 의미를 유지한다.
- 영향 범위: 자연어 해석 정확성, 공개 화면 이해 가능성, 429 복구 시간, 품질 게이트 해석 가능성.

## 4. 근본 원인

별칭 fallback이 문장 전체 공백 제거 후 부분 문자열을 비교해 앞 단어 경계를 잃었다. 미지 태그 판정은 단일 토큰일 때 전체 prefix와 같다는 이유로 건너뛰었다. 화면 요약은 API 계약의 opaque 값과 태그 코드를 표시명으로 변환할 자원을 받지 않았고, 재시도 시각 상태는 요청 시작 시점에만 갱신됐다. 평가기는 오적용 atom과 상태·무시 증거 위반을 같은 counter에 합산했다.

공통 HTTP helper와 stale-request 구현 통일 의견은 결함이 아니라 재사용성 제안이다. 큐레이션은 서버 렌더링 결과와 404 의미를, 지도는 400/429 view 상태를, 자연어는 400/429/503/5xx 재시도 정책을 각각 소유한다. 자연어 요청은 AbortController 하나가 취소와 최신성 증명을 동시에 제공하므로 별도 sequence로 바꾸지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `알바분위기...조용한`과 `바분위기 맛집` parser 테스트 | 수정 전 전자는 BAR 오적용 가능, 후자는 붙여쓰기 지원 필요 | 앞 경계 + 내부 `\s*` 패턴 채택 |
| `이상한거 태그 매운맛도 알려줘` parser 테스트 | 미지 단일 토큰이 누락될 수 있음 | 토큰 수 예외 제거 |
| V4 `tag_definition` seed 확인 | 18개 확정 한국어 label 존재 | 동일 표시명 상수 사용 |
| 공개 Creator 목록 확인 | 페이지가 ID와 channelName을 이미 보유 | 별도 API 없이 map을 component에 전달 |
| 큐레이션·지도·자연어 HTTP client 비교 | 오류 union과 상태별 정책이 서로 다름 | 공통화하지 않음 |
| CollectionAddControl과 자연어 검색 비교 | 전자는 복수 요청 sequence, 후자는 단일 abortable 요청 | 현재 controller 가드 유지 |

## 6. 최종 해결

- 변경 내용: 경계 보존 별칭 매칭, 미지 태그 판정, 표시명 변환, 응답 기준 Retry-After 타이머, 평가 책임 분리와 회귀 테스트를 반영했다.
- 선택 이유: API·DB 계약을 바꾸지 않고 각 재현 조건을 가장 가까운 소유 계층에서 차단한다.
- 변경 파일:
  - `src/main/java/com/masiton/restaurant/application/naturallanguage/NaturalLanguageRestaurantParser.java`
  - `src/test/java/com/masiton/restaurant/application/naturallanguage/NaturalLanguageRestaurantParserTest.java`
  - `src/test/java/com/masiton/restaurant/application/naturallanguage/NaturalLanguageEvaluationGoldenV1Test.java`
  - `frontend/app/restaurants/page.tsx`
  - `frontend/components/restaurants/NaturalLanguageRestaurantSearch.tsx`
  - `frontend/lib/natural-language-search-api.ts`
  - `frontend/lib/natural-language-search-api.test.ts`
- 고려한 대안: 공통 HTTP helper와 공통 request hook 추출은 관련 없는 기존 모듈까지 바꾸고 서로 다른 정책을 하나로 묶으므로 제외했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests com.masiton.restaurant.application.naturallanguage.NaturalLanguageRestaurantParserTest --tests com.masiton.restaurant.application.naturallanguage.NaturalLanguageEvaluationGoldenV1Test` | 통과 | 별칭·미지 태그 회귀와 기본 평가 |
| `.\gradlew.bat '-Dmasiton.eval.releaseHoldout=true' test --tests com.masiton.restaurant.application.naturallanguage.NaturalLanguageEvaluationGoldenV1Test` | 통과 | release holdout 포함 240건 평가 |
| `npm test` | 통과 | 자연어 10건과 기존 프론트 173건 |
| `npm run typecheck` | 통과 | 컴포넌트 props와 formatter 타입 |
| `git diff --check` | 통과 | whitespace 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 다중 단어 붙여쓰기의 정상·오적용 경계, 단일 미지 태그, opaque Creator·태그 표시를 자동 테스트로 추가했다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 확정 재현 입력 실패 수 | 4종 중 4종 실패 가능(별칭 오적용, 미지 태그 누락, 내부값 노출, 429 지연) | 리뷰 재현 입력과 단위 테스트 | 자동 테스트 4종 통과 | 재현 결함 0건 | 양성훈 / PR #176 검증 시점 |
| 운영 오류율·처리시간 | 해당 없음(아직 배포 전이며 이번 수정은 운영 계측 변경이 아님) | 해당 없음 | 해당 없음 | 비교 불가 | 해당 없음 |

## 10. 남은 사항

- 미해결 코드 수정 사항 없음. 두 재사용성 의견은 계약과 구현 차이를 근거로 수정 불필요 답글을 남긴다.
