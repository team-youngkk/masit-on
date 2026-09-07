---
related_documents:
  - README.md
  - ../01-requirements/business-rules.md
  - ../05-specs/api/admin/tag-definition-api.md
  - ../05-specs/data/migration-plan.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../08-planning/admin-tag-definition-creation.md
  - pr-168-ai-schema-verification-review.md
  - pr-361-visit-tag-ci-review.md
---

# PR #368 리뷰 트러블슈팅: 태그 코드 전진 적용과 정규화 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#368 관리자 태그 정의 생성](https://github.com/team-youngkk/masit-on/pull/368) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-09-07 |
| 범위 | V9 legacy 태그 코드 전진 적용, Java/PostgreSQL 정규화 동등성, 정규화 후 길이, AI 승인 태그 비교, 프론트 저장 안내 |
| 주 문제 유형 | 데이터베이스, 애플리케이션 |
| 기존 기록 | [PR #168 기록](pr-168-ai-schema-verification-review.md)의 물리 계약 직접 검증과 [PR #361 기록](pr-361-visit-tag-ci-review.md)의 실제 이전 버전 fixture 검증 원칙을 재사용했다. 같은 태그 정규화 경계 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [V9 legacy 코드와 V10 CHECK](https://github.com/team-youngkk/masit-on/pull/368#discussion_r3946744258) | V9가 허용한 연속·끝 밑줄 코드 때문에 V10 적용이 막히는 문제 | 데이터베이스 | 수정 필요 | 유일하게 정리 가능한 `AI_AUTO` 코드만 밑줄을 축약·제거하고 나머지는 DDL 전에 실패 | 실제 V9 `MENU__KIMBAP_` fixture의 V10 전진 적용 통과 |
| [Java와 PostgreSQL 소문자화](https://github.com/team-youngkk/masit-on/pull/368#discussion_r3946744262) | DB locale에 따라 정규화 결과가 달라질 수 있는 문제 | 데이터베이스 | 수정 필요 | 양쪽 모두 ASCII `A-Z`만 `a-z`로 바꾸고 Unicode corpus 동등성 테스트 추가 | `TagDefinitionIntegrationTest` 통과 |
| [자연어 검색 반영 안내](https://github.com/team-youngkk/masit-on/pull/368#discussion_r3946744267) | #364 범위인 동적 사전 반영을 현재 화면이 완료된 기능처럼 안내 | 애플리케이션 | 수정 필요 | 저장 완료 문구를 방문 태그 저장 사실만 알리도록 수정 | 프론트 테스트·타입 검사·프로덕션 빌드 통과 |
| [NFKC 확장 뒤 200자 초과](https://github.com/team-youngkk/masit-on/pull/368#discussion_r3946744273) | 100자 원문이 정규화 뒤 DB 열 상한을 넘으면 409로 오분류될 수 있는 문제 | 애플리케이션 | 수정 필요 | ADMIN은 정규화 뒤 200자 상한을 필드 400으로 검증하고 AI 후보는 생성 제외 | 확장 문자 100자 회귀 테스트 통과 |
| [AI 태그 정규화 불일치](https://github.com/team-youngkk/masit-on/pull/368#discussion_r3948500891) | AI 경로가 내부 공백을 제거해 다른 용어를 같은 태그로 승인하는 문제 | 애플리케이션 | 수정 필요 | `AiTagPolicy`가 공통 `TagTermNormalizer`를 사용하도록 통합 | NFKC·Unicode 공백·내부 공백 구분 테스트 통과 |

## 3. 문제 현상과 발생 조건

- V9에서는 `[A-Z0-9_]{1,64}` 범위의 `AI_AUTO` 코드가 저장될 수 있어 `MENU__KIMBAP_` 같은 값이 V10의 강화된 CHECK를 통과하지 못했다.
- Java의 `Locale.ROOT` 소문자화와 PostgreSQL `lower()`는 실행 환경의 locale·collation에 따라 비ASCII 문자의 결과가 달라질 수 있었다.
- 표시명·별칭은 원문 100자만 검사해 NFKC로 길이가 크게 늘어나는 입력이 `varchar(200)` INSERT에서 실패할 수 있었다. 저장소 예외는 중복 오류로 변환돼 잘못된 409가 될 수 있었다.
- AI 승인 비교는 모든 공백을 삭제하는 별도 정규화를 사용해 `갈 비`와 `갈비`를 같은 값으로 판정했다.
- 방문 태그 저장 안내는 아직 구현하지 않은 #364 동적 자연어 사전 반영을 약속했다.

## 4. 근본 원인

V10 계약을 설계할 때 새 작성 규칙만 검증하고 V9 작성자가 허용한 코드 집합을 실제 이전 버전 fixture로 대조하지 않았다. 정규화 알고리즘도 Java, PostgreSQL, AI 정책에 각각 구현해 locale, 공백, 길이 경계가 서로 달라졌다. 프론트 문구는 현재 이슈의 저장 경계를 후속 검색 사전 범위와 구분하지 못했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| V9와 V10 코드 제약 대조 | V9 유효 집합이 V10 집합보다 넓음 | 실제 V9 fixture로 전진 적용 정책 검증 |
| 모든 legacy 코드를 자동 변환하는 방안 | 수동 코드 의도와 충돌을 임의 변경할 수 있음 | `AI_AUTO`이며 결과가 유일한 경우에만 보정하고 나머지는 fail-closed |
| `lower()`를 양쪽에서 유지하는 방안 | DB locale 의존성을 제거하지 못함 | 제품 계약이 영문 소문자이므로 ASCII 변환으로 한정 |
| `normalized_term`을 `text`로 바꾸는 방안 | 유일 인덱스 키의 명시적 상한을 잃음 | 200자 계약을 유지하고 저장 전에 정규화 결과를 검증 |
| PR #168·#361 기록 확인 | 이전 버전 실제 데이터와 물리 계약을 직접 검증하는 원칙 확인 | V9 fixture와 PostgreSQL 함수 corpus 테스트에 적용 |

## 6. 최종 해결

- V10은 `AI_AUTO` legacy 코드의 연속·끝 밑줄만 축약·제거하며 현재 형식, 유형 접두사, 전역 유일성을 적용 전에 검사한다. ID와 참조는 유지하고 수동 출처·복구 불가·충돌 값은 마이그레이션을 중단한다.
- 공통 정규화는 NFKC, Unicode 공백 축약, ASCII 소문자화 순서로 고정했고 Java와 PostgreSQL corpus 결과를 비교한다.
- ADMIN 생성은 정규화 결과가 200자를 넘으면 해당 필드의 400 `INVALID_FIELD_VALUE`를 반환한다. AI는 같은 입력을 신규 태그 후보에서 제외한다.
- AI 승인 태그 비교가 `TagTermNormalizer`를 사용해 내부 공백의 의미를 보존한다.
- 방문 태그 저장 성공 문구는 `방문 태그를 저장했습니다.`로 범위를 한정했다.
- 요구사항, API, 데이터 계약, 마이그레이션 계획과 구현 계획을 같은 기준으로 갱신했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| 관련 백엔드 테스트 6개 클래스 | 통과, 23건 | V9→V10, ADMIN·AI 정규화·길이·원자성 |
| `.\gradlew.bat clean build --no-daemon --console=plain` | 통과, 1,463건 중 1,461건 통과·2건 조건부 제외 | 전체 백엔드 컴파일·테스트·계약 검사 |
| `npm run build` | 통과, 357건 | 프론트 테스트·TypeScript·Next.js 프로덕션 빌드 |
| `git diff --check` | 통과 | 공백·패치 형식 |

## 8. 재발 방지 및 다음 확인

- 새 마이그레이션이 기존 CHECK를 강화할 때 직전 버전이 허용한 경계값을 실제 전진 적용 fixture로 넣는다.
- Java와 DB가 공유하는 정규화는 locale 비의존 알고리즘과 경계 corpus로 동등성을 검증한다.
- 원문 길이와 정규화된 저장 키 길이를 별도 계약으로 검사한다.
- 다음 확인: #364에서 새 용어를 자연어 검색 동적 사전으로 반영할 때 저장 성공과 검색 반영 시점의 사용자 안내를 다시 정의한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 확인된 리뷰 결함 | 5건 | PR #368 미해결 리뷰 스레드 | 수정 후 회귀 테스트 실패 0건 | 5개 원인 모두 자동 검증 또는 빌드로 고정 | 양성훈, PR #368 리뷰 반영 시점 |
| 정규화 구현 | Java·DB·AI 3개 경로가 서로 다름 | 구현과 corpus 대조 | 공통 계약 1개, Java↔DB corpus 일치 | locale·공백·길이 분기 제거 | 양성훈, PR #368 리뷰 반영 시점 |
| 운영 오류율 | 해당 없음 | 배포 전 기능이라 운영 표본 없음 | 해당 없음 | 운영 지표 대신 전진 마이그레이션·회귀 테스트로 검증 | #363 배포 전 |

## 10. 남은 사항

- #364 동적 자연어 사전 구현은 별도 이슈 범위다.
