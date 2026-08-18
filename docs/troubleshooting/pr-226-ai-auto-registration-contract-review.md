---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../04-product/wireframes/third-expansion-wireframes.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/data-traceability.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../08-planning/third-expansion-ai-candidate-loss-analysis.md
  - ../08-planning/third-expansion-test-matrix.md
---

# PR #226 리뷰 트러블슈팅: AI 자동 등록 계약의 미완결 경로와 합의 상태 표기

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#226 AI 영상 추출 자동 등록 계약을 확정한다](https://github.com/team-youngkk/masit-on/pull/226) |
| 이슈 | [#225](https://github.com/team-youngkk/masit-on/issues/225) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-18 |
| 범위 | 문서 계약 PR의 미해결 인라인 리뷰 14건 (`inan0226` 2건, `w00lam` 6건, `jinyp01` 7건 중 중복 제외) |
| 주 문제 유형 | 기타 — 계약 문서 간 정합성. 일부는 `데이터베이스`(제약 조건 충돌·저장 소스 부재) |
| 기존 기록 | [PR #209 AI 후보 등록 입력·비동기·외부 연동 경계](pr-209-ai-candidate-registration-review.md), [PR #204 Prompt P2 계약 동기화](pr-204-ai-prompt-contract-review.md), [PR #220 Prompt 버전 상향의 문서 전파 누락](pr-220-ai-prompt-version-propagation-review.md)을 확인했다. 세 기록 모두 "한 문서를 고치고 대응 문서를 같은 패스로 대조하지 않아 계약이 갈라진다"는 같은 재발 패턴이며, 이번 건도 같은 원인이다. 다만 이번은 신규 계약 설계 단계의 미완결이라 별도 사건으로 기록하고 서로 링크한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [MAX_CANDIDATES 자기모순 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800261061) | 같은 절에서 상한 인상이 결정·미결로 동시 서술됨 | 기타 | 수정 필요 | 1차로 단정 문장을 삭제해 미결로 통일했고, 이후 9절에서 상한 300 인상과 절삭 표시로 재결정해 미결 항목 자체를 해소 | 9절 결정 요약·남은 결정·PR 본문 3곳 대조 |
| [MAX_CANDIDATES 결정 상태 정리 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264157) | 미결 유지 시 상한 초과 예외·대체 경로를 PRD/BR/API에 명시 | 기타 | 수정 필요 |  이후 9절에서 상한을 300으로 올리고 절삭 표시를 계약에 넣는 것으로 재결정. `BR-AIEXTRACT-001`·PRD에 절삭 표시 의무와 미준수 응답의 `SCHEMA` 기각을 명시 | 손실 분석 4.5절의 기존 상한 서술과 대조 |
| [MAX_CANDIDATES 서술 일치 (P3)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296431) | 같은 항목 | 기타 | 수정 필요 | 위 두 건과 같은 수정으로 해소 | 동일 |
| [결정 항목 개수 (P3)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800261064) | "두 항목"이 아래 세 항목 목록과 불일치 | 기타 | 수정 필요 | "결정 C, 복수 후보 처리, 카테고리 매핑 세 항목"으로 항목명을 명시해 개수 의존 서술 제거 | 9절 목록·PR 본문 대조 |
| [보조 입력 요청 계약 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264133) | `requiredSupplements`를 담을 요청 필드가 없음 | 기타 | 수정 필요 | 3.5절 `CONFIRM` 요청에 `supplements` 객체와 사유별 필수·불허 조건, 재검증·오류 계약, 테스트 매핑 추가 | 3.6절 `requiredSupplements` 표와 1:1 대조 |
| [보조 입력 필드 부재 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296408) | 같은 항목 | 기타 | 수정 필요 | 위와 같은 수정으로 해소 | 동일 |
| [등록 응답 값의 저장 소스 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296413) | `creatorId`·`videoId`·`reusedResources`를 읽을 곳이 없음 | 데이터베이스 | 수정 필요 | `ai_registration_unit`에 `registered_creator_id`·`registered_video_id`·`reused_resources` 컬럼을 추가하고, API 응답이 감사 이력이 아니라 이 테이블에서 재구성됨을 명시 | 3.6절 응답 예시 필드와 테이블 컬럼 1:1 대조 |
| [MANUAL_OVERRIDE CHECK 충돌 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296400) | 사후 등록한 `MANUAL_OVERRIDE` 행이 NULL 제약을 위반 | 데이터베이스 | 수정 필요 | `rolled_back_at` 컬럼으로 등록 완료와 롤백 완료를 구분하고 상태·컬럼 조합 표와 CHECK 조건을 재정의 | 상태 4종 × 등록 결과 존재 여부 조합표 작성 |
| [혼합 reviewStatus 요약 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264148) | 부분 성공 작업의 최상위 값이 결정 불가 | 기타 | 수정 필요 | 5단계 우선순위 표를 추가해 혼합 작업을 `AUTO_BLOCKED`로 고정하고 `resultCompleteness`와의 독립 관계를 명시. 새 Enum은 추가하지 않음 | 등록 단위 상태 조합별 최상위 값 도출 |
| [카테고리 매핑 기준정보 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264150) | 매핑 표의 물리·운영 계약 부재 | 데이터베이스 | 수정 필요 | 데이터 계약 5.2절에 `food_category_mapping` 테이블(출처 유형·일치 방식·우선순위·활성·이력)을 정의하고 대조 순서·복수 일치 차단·seed·소유자를 명시. `BR-AIEXTRACT-010`과 추적표·`TST-E3-AI-006`에 반영 | 기존 `food_category` 10개 값 유지 여부와 다대일 관계 확인 |
| [중복 판정 상태 매핑 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296418) | 중복이 종결·복구 가능으로 이중 서술 | 기타 | 수정 필요 | `AUTO_REJECTED` 설명에서 중복을 제거하고 `DUPLICATE_CONFLICT`가 `AUTO_BLOCKED`로 귀결됨을 명시 | 2.1절 표와 `BR-AIEXTRACT-011` 예외 목록 대조 |
| [MISSING_REQUIRED_FIELD 중복 의미 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296426) | 같은 코드가 400 파라미터 누락과 422 후보 부족 두 뜻으로 쓰임 | 기타 | 수정 필요 | `unitId` 누락 전용 코드 `AIEXTRACT_UNIT_ID_REQUIRED`(400)를 신설하고 오류 계약 표에 추가. 두 용법의 차이를 본문에 명시 | 오류 계약 표와 `blockReason` 표 대조 |
| [와이어프레임 잔여 UI (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264156) | 정상 목업에 `[Kakao 확인]`·유사 장소 비교가 남음 | 기타 | 수정 필요 | 정상 목업의 검증 열을 자동 판정 결과로 바꾸고 채택 장소·카테고리 근거·4종 등록 결과 행으로 교체. 장소 후보 비교는 예외 화면으로 이동 | 목업 텍스트와 서술 규칙의 모순 확인 |
| [합의 전 확정 계약 게시 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800264140) | 합의 전 데이터 계약을 확정 상태로 갱신 | 기타 | 수정 필요 | 데이터 계약 1절에 두 신규 테이블의 `합의 대기` 표시와 합의 주체·불발 시 되돌릴 범위를 명시 | CLAUDE.md 7절 대조 |
| [합의 전 Accepted 유지 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800296438) | ADR이 `Accepted`인 채 5.3절을 단정 재작성 | 기타 | 수정 필요 | ADR 1절에 2026-08-18 결정만 `합의 대기`임을 표시하고, 같은 표시를 business-rules·API 계약에 전파 | ADR·BR·API·데이터 4개 문서 표기 일치 확인 |

중복 지적을 제외한 실제 처리 대상은 11개 원인이다. `MAX_CANDIDATES` 3건이 한 원인, 보조 입력 2건이 한 원인, 합의 상태 2건이 한 원인이다.

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 코드 변경이 없는 문서 계약 PR이다.
- 발생 환경: PR #226이 신설한 `BR-AIEXTRACT-009`·`010`·`011`과 그에 대응하는 API·데이터 계약.
- 재현 조건: 신설 규칙에서 출발해 API 요청·응답 필드와 데이터 계약 컬럼까지 한 경로씩 따라 읽는다.
- 실제 결과: 세 유형의 단절이 나타났다.
  1. **경로 미완결** — 예외 사유가 요구하는 보충 입력(`kakaoPlaceUrl`·`foodCategoryId`)을 담을 요청 필드가 없어 예외 보정이 구현 불가능했다. 등록 응답의 `creatorId`·`videoId`·`reusedResources`도 읽어올 컬럼이 없었다.
  2. **상태 정의 공백** — 등록 단위를 도입하면서 작업 최상위 `reviewStatus`의 혼합 규칙을 정의하지 않았고, `MANUAL_OVERRIDE`가 등록 완료와 롤백 완료를 구분 없이 가리켜 CHECK 조건과 충돌했다.
  3. **결정 상태 혼선** — `MAX_CANDIDATES`가 같은 절에서 결정·미결로 동시 서술됐고, 합의 전 계약이 확정 계약처럼 게시됐다.
- 기대 결과: 규칙 → API → 데이터의 모든 경로가 끝까지 이어지고, 각 상태 조합의 값이 하나로 결정되며, 결정·미결·합의 대기 상태가 문서마다 같아야 한다.
- 영향 범위: 이 계약으로 진행할 구현 PR 전체. 특히 예외 보정 경로와 `ai_registration_unit` 마이그레이션.

## 4. 근본 원인

**규칙을 먼저 쓰고 그 규칙이 만드는 새 상태·새 값의 소비 지점을 역방향으로 검증하지 않았다.**

`BR-AIEXTRACT-011`은 "예외에서만 보조 입력으로 전환한다"는 규칙과 예외 목록까지 정의했지만, 그 보조 입력이 실제로 어느 요청 필드로 들어오는지는 규칙의 관심사가 아니어서 API 계약에 반영되지 않았다. 마찬가지로 등록 단위 도입은 "단위별 판정"이라는 규칙 층위에서는 완결됐지만, 그 결과 작업 최상위 상태가 여러 단위의 요약값이 된다는 파생 효과를 상태 정의로 되돌려 반영하지 않았다.

`MANUAL_OVERRIDE` CHECK 충돌도 같은 형태다. 기존 상태값을 재사용하면서 그 값이 이제 두 가지 사건(사후 등록, 롤백)을 가리킨다는 사실을 제약 조건 설계에 반영하지 않았다.

결정 상태 혼선은 원인이 다르다. 결정 요약을 쓸 때 "복수 후보를 통과시키려면 상한도 올려야 한다"는 **필요성 확인**을 **결정**으로 서술했고, 같은 절 아래 미결 목록을 같은 패스에서 대조하지 않았다.

합의 전 게시는 절차 인식 문제다. PR 본문에는 "합의 전"이라고 정확히 적었으나 문서 본문에는 그 상태를 남기지 않아, PR 맥락 없이 문서만 읽는 사람에게는 확정 계약으로 보였다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `requiredSupplements` 표의 각 값을 3.5절 요청 스키마에서 역검색 | `kakaoPlaceUrl`·`foodCategoryId` 모두 없음. 지적이 정확 | `supplements` 객체 신설. 사유별 필수·불허를 표로 고정 |
| 3.6절 응답 필드를 `ai_registration_unit` 컬럼과 1:1 대조 | 4개 중 2개(`creatorId`·`videoId`)와 `reusedResources`에 대응 컬럼 없음 | 참조 컬럼 3개 추가. "감사 이력에만 남긴다"는 서술이 원인이므로 함께 수정 |
| `review_status` 4종 × 등록 결과 존재 여부 조합표 작성 | `MANUAL_OVERRIDE`만 두 경우로 갈림 | `rolled_back_at`으로 구분. 별도 상태값 신설은 기존 Enum 소비자에 영향이 커서 제외 |
| 등록 단위 상태 조합에서 최상위 값 도출 시도 | 기존 Enum만으로 혼합 작업 표현 불가 확인 | `MIXED`/`PARTIAL` 신설 대신 우선순위 규칙 채택. Enum 추가는 기존 목록 필터·클라이언트 계약을 함께 바꿔야 해서 범위가 커진다 |
| 매핑 표를 `food_category`에 흡수 가능한지 검토 | 한 카테고리에 여러 표현이 대응하는 다대일이고 표현마다 출처·일치 방식이 다름 | 별도 기준정보 테이블로 분리 |
| 와이어프레임 목업 텍스트와 서술 규칙 대조 | 서술은 "후보 선택 없음", 목업에는 `[Kakao 확인]`·`유사 장소 1건 보기` 잔존 | 목업을 자동 판정 결과 표시로 교체 |
| 손실 분석 9절·PR 본문·PRD의 `MAX_CANDIDATES` 서술 대조 | 3곳 중 1곳만 결정으로 서술 | 단정 문장 제거로 통일. 상한 초과 시 동작을 BR·PRD에 명시 |
| CLAUDE.md 7절 확인 | API·테이블 변경은 소유자 합의가 선행 | 문서를 되돌리는 대신 `합의 대기` 표시를 4개 문서에 전파. 합의 불발 시 되돌릴 범위를 ADR에 명시 |

## 6. 해결 방법

### 6.1 미완결 경로 연결

- `CONFIRM` 요청에 `supplements` 객체를 추가하고, `blockReason`별 필수·불허 필드와 재검증·오류 코드를 표로 고정했다. 보충값도 기존 Kakao 장소 동일성 검증을 다시 거친다.
- `ai_registration_unit`에 `registered_creator_id`·`registered_video_id`·`reused_resources`를 추가하고, 등록 API 응답이 이 컬럼들에서 재구성됨을 API·데이터 양쪽에 명시했다.

### 6.2 상태 정의 보강

- `rolled_back_at`을 추가해 `MANUAL_OVERRIDE`의 두 사건을 구분하고, 상태·컬럼 조합표와 CHECK 조건을 재정의했다.
- 작업 최상위 `reviewStatus` 요약 규칙을 5단계 우선순위로 고정했다. 혼합 작업은 `AUTO_BLOCKED`이며, 클라이언트는 최상위 값만으로 등록 성공을 판단하지 않는다.
- `AUTO_REJECTED`에서 중복을 제거하고 `DUPLICATE_CONFLICT`를 `AUTO_BLOCKED`로 귀결시켰다.
- `unitId` 누락 전용 오류 코드 `AIEXTRACT_UNIT_ID_REQUIRED`를 신설해 `MISSING_REQUIRED_FIELD`의 이중 의미를 해소했다.

### 6.3 기준정보 물리 계약

`food_category_mapping` 테이블을 정의했다. `source_type`(Kakao 분류·메뉴 표현), `pattern`, `match_type`(완전·부분), `priority`, `active`를 갖고 대조 순서는 출처 → 일치 방식 → 우선순위다. 같은 순위에서 서로 다른 카테고리로 일치하면 `CATEGORY_UNRESOLVED`로 차단한다. seed는 기존 고정 키워드 이관에서 출발한다.

### 6.4 합의 상태 표기

되돌리는 대신 `합의 대기`를 명시했다. ADR 1절이 원문이고 business-rules·API·데이터 계약이 이를 참조한다. 합의 불발 시 되돌릴 범위(BR 3건, API 3항목, 테이블 2개)를 ADR에 열거해 롤백 단위를 확정했다.

## 7. 변경 파일과 검증

| 파일 | 변경 |
|---|---|
| [business-rules.md](../01-requirements/business-rules.md) | 상한 초과 시 동작, 카테고리 대조 순서·복수 일치 차단, `합의 대기` 표시 |
| [ai-video-information-extraction.md](../04-product/prd/admin/ai-video-information-extraction.md) | 상한 초과 시 복구 경로 |
| [third-expansion-wireframes.md](../04-product/wireframes/third-expansion-wireframes.md) | 정상 목업의 관리자 검증 UI 제거, 예외 화면으로 이동 |
| [ai-video-extraction-api.md](../05-specs/api/admin/ai-video-extraction-api.md) | `supplements`, 최상위 요약 규칙, 중복 매핑, 오류 코드 신설, 응답 소스 명시, `합의 대기` |
| [third-expansion-ai-video-data-contract.md](../05-specs/data/third-expansion-ai-video-data-contract.md) | 참조 컬럼 3개, `rolled_back_at`, 조합표·CHECK 재정의, `food_category_mapping`, `합의 대기` |
| [data-traceability.md](../05-specs/data/data-traceability.md) | 두 테이블 행 갱신·추가 |
| [ai-001-video-extraction-candidate-boundary.md](../07-adr/integration/ai-001-video-extraction-candidate-boundary.md) | 1절 `합의 대기`와 롤백 범위 |
| [third-expansion-ai-candidate-loss-analysis.md](../08-planning/third-expansion-ai-candidate-loss-analysis.md) | 결정 항목명 명시, 상한 단정 문장 제거 |
| [third-expansion-test-matrix.md](../08-planning/third-expansion-test-matrix.md) | `TST-E3-AI-006`·`007` 검증 항목 보강 |

검증은 문서 대조로 수행했다. 실행 가능한 빌드·테스트 대상이 없다.

```
계약 경로 역추적 (규칙 → API → 데이터)
- requiredSupplements 3종 → CONFIRM supplements 필드: 대응 확인
- 3.6절 응답 필드 5종 → ai_registration_unit 컬럼: 대응 확인
- review_status 4종 × rolled_back_at → 등록 결과 컬럼 조합: 4개 행 모두 정의됨
- 등록 단위 상태 조합 → 최상위 reviewStatus: 5개 우선순위로 전부 결정됨
- BR-AIEXTRACT-010 대조 순서 → food_category_mapping 컬럼: 대응 확인

합의 대기 표기 전파
- ADR 1절 / business-rules BR-009 앞 / API 1절 / 데이터 계약 1절: 4곳 확인

2차 반영: 새 표의 입력 공간 전수 확인
- 최상위 reviewStatus: 등록 단위 상태 4종의 조합 5가지를 나열해 전부 하나로 수렴
- unitId 처리: 등록 단위 0개·1개·2개 이상 + 타 작업 단위 지정 4가지 모두 정의
- 등록 결과 식별자: 상태 4종 × 식별자 존재 여부, 함께 존재하거나 함께 null
- reused_resources: 자원 4종 중 실제 재사용 가능한 2종만 허용값에 남김

6차 반영: 개념별 전 문서 기계 대조
- 차단 사유 고유값 수 (BR / API / PRD): 7 / 7 / 7 일치
- 단수 `recoveryPath` 잔존: 계약 문서 0건 (기록 문서의 인용만 남음)
- supplements 예시의 null 키: 0건
- 최상위 null 근거: API 요약 규칙 1순위와 데이터 계약 계산 규칙 양쪽에 존재
- 롤백 완료 재등록 안내: 잘못된 CONFIRM 안내 제거 확인
- review decision 4종 × 허용 상태: 표로 전부 정의됨
```

## 8. 재발 방지와 다음 확인

- **적용함** — 신규 계약을 쓸 때 규칙 → API → 데이터의 정방향뿐 아니라, 새로 만든 필드·상태값을 소비 지점에서 역방향으로 되짚는 대조를 이번 수정에 사용했다. 7절 검증 블록이 그 결과다.
- **적용함** — 상태값을 재사용할 때 그 값이 가리키는 사건이 하나인지 확인한다. 이번에 `MANUAL_OVERRIDE`가 둘이었고 `rolled_back_at`으로 분리했다.
- **적용함** — 리뷰 반영 후 두 미결 항목을 추가로 결정했다. 9절에 기록한다.
- **추적 대상** — `합의 대기` 표시 제거는 이 PR의 소유자 승인 직후 병합 직전 커밋에서 수행한다. 담당자 양성훈, 추적은 이슈 [#225](https://github.com/team-youngkk/masit-on/issues/225).
- **추적 대상** — Prompt `P8`·결과 Schema `S2` 라벨 상향과 다섯 대상 전파는 구현 PR에서 수행한다. 이 PR은 문서 확정 범위이므로 라벨을 올리지 않았다. 담당자 김인안.

## 9. 후속 결정: 후보 수 상한과 합의 절차

리뷰 반영 과정에서 `MAX_CANDIDATES` 관련 스레드 3건을 처리하며 상한 초과의 실제 동작을 다시 확인했고, 그 결과 미결로 두려던 판단 자체를 바꿨다.

### 9.1 상한 초과는 기각이 아니라 조용한 절삭이었다

`GeminiHttpVideoExtractionAdapter`의 시스템 지시가 이미 "영상이 상한보다 많은 장소를 다루면 근거가 가장 강한 후보만 남기고 나머지는 생략하라"를 포함하고 있었다. 따라서 P2 이후 상한 초과의 정상 동작은 응답 기각이 아니라 모델의 자체 절삭이며, `SCHEMA` 기각은 모델이 지시를 어겼을 때만 발생한다. 실측 131건은 이 지시가 없던 P1 응답이다.

리뷰 반영 1차에서 "100건 초과는 `SCHEMA`로 기각된다"고 적은 것은 이 지시를 확인하지 않은 서술이었다. 세 스레드 모두 결정 상태의 자기모순을 지적했고, 그 지적을 처리하며 코드를 읽는 과정에서 드러났다.

이 사실은 문제의 성격을 바꾼다. 다장소 영상의 위험은 "작업 실패"가 아니라 **일부 맛집이 조용히 누락되고 관리자가 알 수 없다**는 것이다. 등록 단위별 독립 자동 등록을 도입해도 후보로 올라오지 않은 맛집은 등록되지 않는다.

### 9.2 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 상한 값 | 100 → 300 | 실측 최대 131건에 2배 이상 여유. 131건 응답은 이미 정상 처리된 전례가 있다 |
| 절삭 표시 | 결과와 관리자 화면에 노출 | 조용한 누락을 계약으로 금지한다 |
| 표시 신뢰 | 모델 표시 + 후보 수가 상한과 같으면 절삭 가능으로 취급 | 모델 자체 보고만 믿지 않는다 |

버전 영향은 Prompt `P7` → `P8`, 결과 Schema `S1` → `S2`다. `ux_ai_job__idempotency`가 두 버전을 포함하므로 기존 영상이 재추출 대상이 되고 무료 quota를 한 번 더 소모한다.

**라벨은 이 PR에서 올리지 않았다.** 코드가 바뀌지 않은 상태에서 문서만 `P8`·`S2`로 바꾸면 배포되지 않은 상태를 배포된 것처럼 기록하게 된다. [PR #220](pr-220-ai-prompt-version-propagation-review.md)이 같은 유형의 전파 사고 기록이다. 구현 PR이 코드와 함께 올리고 시스템 지시·송신 Schema·수신 검증기·`ai_candidate_snapshot`·관리자 응답 다섯 대상을 같은 PR에서 갱신한다. 검증은 `TST-E3-AI-008`이다.

### 9.3 합의 절차

`합의 대기` 표시를 언제 어떻게 제거할지가 정의돼 있지 않아 표시가 무기한 남을 수 있었다. 합의를 이 PR의 소유자 승인으로 갈음하기로 정했다. 계약 소유자 세 명이 모두 이 PR의 리뷰어이고 ruleset이 2명 승인을 강제하므로 승인 기록이 그대로 합의 근거가 된다. 승인이 달리면 병합 직전 커밋에서 네 문서의 표시를 함께 제거하고, 승인 없이는 병합하지 않는다.

## 10. 2차 리뷰 처리

1차 반영 뒤 새로 올라온 미해결 스레드 7건이다. 모두 1차 반영이 **새로 만든** 계약의 빈틈이며, 지적 유형이 1차와 같다. 규칙·표를 추가하면서 그 표가 덮지 못하는 조합과 경계값을 함께 확인하지 않았다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [혼합 최상위 상태 미정의 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800416042) | 확정 + 거부 조합이 어느 순위에도 걸리지 않음 | 기타 | 수정 필요 | 4순위를 "`AUTO_CONFIRMED`가 하나라도 있다", 5순위를 "그 밖의 경우"로 바꿔 전 조합을 덮고, 조합별 결과표와 우선순위 근거를 추가 |
| [혼합 조합 완결 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800423755) | 같은 항목 | 기타 | 수정 필요 | 위와 같은 수정. 다섯 조합을 계약 테스트로 고정 |
| [작업 상세의 4종 식별자 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800423760) | 상세 응답의 등록 단위에 맛집 식별자만 있음 | 기타 | 수정 필요 | `registrationUnits[]`에 `registeredCreatorId`·`registeredVideoId`·`registeredVisitId`·`reusedResources` 추가, 상태별 null 규칙 명시 |
| [`unitId` 경계값 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800416046) | 등록 단위 0개·1개일 때 동작 미정의 | 기타 | 수정 필요 | 단위 수별 처리표 추가. 0개는 `422`, 1개는 생략 허용, 타 작업 단위 지정은 `404` |
| [동시성·업무 중복 코드 혼동 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800416057) | `AIEXTRACT_DUPLICATE_CONFLICT`와 `DUPLICATE_CONFLICT` 이름 충돌 | 기타 | 수정 필요 | 동시성 충돌에 `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`를 신설하고 두 코드의 차이를 명시. 기존 Accepted 코드는 건드리지 않음 |
| [매핑 이력 보존 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800423763) | UPDATE하면 과거 매핑을 재현할 수 없음 | 데이터베이스 | 수정 필요 | 활성 행 부분 unique로 바꾸고 업무 컬럼을 append-only로 운영. `category_decision`에 매핑 행 식별자와 그 시점 카테고리 값을 함께 저장 |
| [`reused_resources` 허용값 (P3)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800416052) | CHECK가 실제 로직보다 넓음 | 데이터베이스 | 수정 필요 | 허용값을 `creator`·`video`로 좁힘. 맛집·방문 관계는 `DUPLICATE_CONFLICT`로 차단되어 재사용이 발생할 수 없다는 근거를 본문에 명시 |

### 10.1 반복된 원인

1차와 2차의 지적이 형태만 다르고 원인은 같다. **새 표·새 규칙을 추가할 때 그 표가 덮는 입력 공간을 전부 나열해 확인하지 않았다.**

- 최상위 요약 규칙은 5개 순위를 썼지만 등록 단위 상태 4종의 조합을 전부 대입해 보지 않았다. 확정 + 거부 조합이 빠졌다.
- `unitId` 필수 조건은 "둘 이상"만 썼고 0개·1개를 확인하지 않았다.
- `reused_resources` CHECK는 자원 4종을 기계적으로 나열했고, 그중 둘이 실제로 발생 가능한지 확인하지 않았다.

1차 반영에서 "상태 조합표를 만들어 검증한다"는 방법을 `MANUAL_OVERRIDE` 건에는 적용했지만 다른 표에는 적용하지 않았다. 2차에서는 새로 만든 모든 표에 같은 방법을 적용했고, 7절 검증 블록에 조합 나열 결과를 남겼다.

### 10.2 3차 리뷰 처리

2차 반영 뒤 3건이 더 올라왔다. 두 건은 2차와 같은 유형(코드 이름 겹침, 상태별 동작 미정의)이고, 한 건은 예외 목록과 요청 계약이 어긋난 새 지적이다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`AIEXTRACT_JOB_NOT_FOUND` 재사용 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800486751) | 작업 없음과 단위 없음에 같은 코드 사용 | 기타 | 수정 필요 | `AIEXTRACT_UNIT_NOT_FOUND`(404) 신설, 두 코드의 대응 동작 차이를 명시 |
| [`MISSING_REQUIRED_FIELD` 보충 경로 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800487731) | 예외는 보조 입력 대상인데 제출할 필드가 없음 | 기타 | 수정 필요 | 보조 입력을 판정 선택으로 한정하고, 후보 값이 부족한 예외를 보조 입력 대상에서 제외해 재추출·수동 등록으로 보냄. BR·PRD·API 동시 수정 |
| [등록 엔드포인트 상태별 허용 범위 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800487733) | 5개 상태 중 1개만 정의 | 기타 | 수정 필요 | 상태별 허용·거절·멱등 결과표 추가. 종결 상태와 롤백 완료는 거절 |

`MISSING_REQUIRED_FIELD` 건은 단순 누락이 아니라 **경계선 자체가 잘못 그어진 경우**였다. `BR-AIEXTRACT-011`의 예외 목록을 만들 때 "자동 등록이 멈추는 사유"와 "관리자 입력으로 복구 가능한 사유"를 구분하지 않고 한 목록에 넣었다. 두 개념을 분리하고 다음 기준을 세웠다.

> 보조 입력은 **판정 선택**만 받고 **후보 값 생성**은 받지 않는다.

Kakao 장소와 카테고리는 관리자가 외부 기준정보 중 하나를 고르는 것이라 AI 근거를 대체하지 않는다. 맛집명·주소·방문 근거는 관리자가 값을 만들어 넣는 순간 영상 근거 없는 데이터가 등록되므로 재추출 또는 기존 수동 등록으로 보낸다.

### 10.3 4차 리뷰 처리

3차 반영 뒤 1건이 남았다. 박진영은 새 지적이 없음을 확인했다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`DUPLICATE_CONFLICT` 상태 전이 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800536851) | 사용자 흐름 상태도가 중복을 `AUTO_REJECTED`로 매핑 | 기타 | 수정 필요 | 상태도의 `AUTO_REJECTED` 전이에서 중복을 빼고 `AUTO_BLOCKED` 전이에 업무 중복을 명시. 접수 단계 중복과 업무 중복을 구분해 서술하고 PRD·와이어프레임·테스트 매트릭스를 같은 매핑으로 맞춤 |

2차 반영에서 API 2.1절의 `AUTO_REJECTED` 설명에서 중복을 제거했지만, 같은 개념을 서술하는 사용자 흐름 상태도를 함께 보지 않았다. 계약 문서(API·BR)와 제품 문서(사용자 흐름·와이어프레임)를 서로 다른 패스에서 고친 것이 원인이다. 이번에는 상태 매핑을 바꾸면서 그 매핑이 등장하는 네 문서를 한 번에 확인했다.

### 10.4 5차 리뷰 처리

김인안과 박진영이 승인했고 이우람이 7건을 남겼다. 중복 2건을 제외하면 6개 항목이며, 전부 3~4차 재분류가 만든 잔여 불일치다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`AUTO_REJECTED` 복구 가능 여부 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800587050) | FR은 사후 보정·롤백 허용, API·흐름은 종결로 정의 | 기타 | 수정 필요 | `FR-AIEXTRACT-003`을 `AUTO_BLOCKED` 사후 보정과 `AUTO_CONFIRMED` 롤백으로 분리하고 `AUTO_REJECTED`는 재추출만 가능하다고 명시 |
| [보완 버튼 노출 (P2, 2건)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800563363) | 후보 값 부족 예외 목업에 `[허용된 값 보완]` 잔존 | 기타 | 수정 필요 | 목업에서 보완 버튼을 제거하고 재추출·수동 등록만 남김. 장소·카테고리 차단은 보조 입력을 받는 별도 목업으로 분리 |
| [최상위 `AUTO_CONFIRMED` 의미 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800587048) | 상태 표는 "모든 단위 확정", 요약 규칙은 혼합도 포함 | 기타 | 수정 필요 | 상태 표 설명을 요약 규칙에 맞추고, 최상위 값의 의미를 "처리할 예외가 남지 않았고 등록된 단위가 있다"로 명시 |
| [`AIEXTRACT_VALIDATION_CONFLICT` 복구 구분 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800587052) | 같은 422가 복구 가능·불가를 모두 가리킴 | 기타 | 수정 필요 | 오류 계약을 "검증 충돌"로 일반화하고 응답에 `recoveryPath`(6종)를 추가해 클라이언트가 다음 화면을 결정하게 함 |
| [와이어프레임 버전 라벨 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800587057) | 목록 목업이 `M3/P7/S2` 표시 | 기타 | 수정 필요 | `M3/P7/S1`로 정정. `P8`·`S2`는 구현 PR에서 갱신할 미래 라벨 |
| [PR 본문 서술 (P3)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800559420) | 본문이 7가지 모두 보조 입력이라고 서술 | 기타 | 수정 필요 | PR 본문을 3 vs 4 분류로 수정 |

와이어프레임 버전 라벨은 특히 아프다. 9.1절에서 "문서만 `P8`·`S2`로 올리면 배포되지 않은 상태를 배포된 것처럼 기록한다"고 써 놓고, 정작 같은 문서의 목록 목업에는 기존에 `S2`가 들어가 있었다. 새로 쓴 문장만 보고 기존 서술을 확인하지 않았다.

### 10.5 6차 리뷰 처리와 연쇄 불일치 전수 점검

16건이 올라왔고 중복을 빼면 8개 항목이다. 전부 3~5차 수정이 만든 연쇄 불일치다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`BR-AIEXTRACT-002` 원자성 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668275) | 작업 전체 0건으로 읽혀 등록 단위 원자성과 충돌 | 기타 | 수정 필요 | 등록 단위 기준으로 개정하고, 작업 전체 0건이 되는 두 경우(추출 실패·등록 단위 0개)를 별도 명시 |
| [등록 단위 0개의 최상위 상태 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668283) | API는 `null`, DB 컬럼은 NN이라 표현 불가 | 데이터베이스 | 수정 필요 | 요약 규칙을 6순위로 재정의. `null`은 Snapshot 부재(판정 전)로 한정하고, 단위 0개는 Snapshot 자체 판정값을 쓴다. 데이터 계약에 저장·계산 경계 명시 |
| [롤백 완료 재등록 경로 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668286) | `CONFIRM` 안내가 실제 허용 상태와 불일치 | 기타 | 수정 필요 | `review`의 `decision`별 허용 상태표를 추가하고, 롤백 완료는 어떤 `decision`도 허용하지 않음을 명시. 잘못된 안내 제거 |
| [복수 복구 경로 (P2, 2건)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800644632) | 단일 Enum이 두 동작을 표현 못 함 | 기타 | 수정 필요 | `recoveryPath` → `recoveryPaths` 배열로 변경. 7개 예외와 3개 거절의 매핑을 전부 고정 |
| [카테고리 보정 API 경로 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668293) | 화면·규칙은 허용하는데 호출 계약 없음 | 기타 | 수정 필요 | `ADJUST_CATEGORY` decision 신설. 등록 결과·공개 상태 유지, 이전 값 감사 이력 |
| [보완 범위 구분 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668297) | 후보 값 직접 보완처럼 읽힘 | 기타 | 수정 필요 | 보완 경로를 `supplementText` 재추출과 `review` 보충 입력 둘로 나눠 서술 |
| [절삭과 상한 위반 구분 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668304) | `PR-AIEXTRACT-007`이 상한 초과를 수동 등록으로 서술 | 기타 | 수정 필요 | `PR-AIEXTRACT-007`을 비용 상한으로 좁히고 `016`(정상 수용·절삭 표시)·`017`(Schema 위반 기각) 신설 |
| [`supplements` null 키 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800668306) | 예시가 바로 아래 불허 규칙을 위반 | 기타 | 수정 필요 | 장소·카테고리·`ADJUST_CATEGORY` 예시를 분리하고, `null`을 미전송으로 취급하지 않는 직렬화 규칙 명시 |

#### 전수 점검 결과

지적된 8개를 고친 뒤, 이번 PR이 만든 개념이 등장하는 모든 문서를 기계적으로 대조했다. 지적에 없던 연쇄 불일치 4건을 추가로 찾아 함께 고쳤다.

| 추가 발견 | 조치 |
|---|---|
| PRD `AUTO_BLOCKED` 행이 차단 사유 3종만 열거 | 7종 전부로 맞추고 허용 동작을 사유별 복구 경로로 서술 |
| `FR-AIEXTRACT-003`에 카테고리 보정 권한 없음 | 등록 완료 단위의 카테고리 보정을 요구사항에 추가 |
| 데이터 계약 상태표가 `MANUAL_OVERRIDE`를 사후 등록으로만 설명 | 카테고리 보정도 같은 행에 해당함을 명시 |
| 와이어프레임이 노출 동작의 근거를 서술하지 않음 | `recoveryPaths` 배열을 그대로 따르고 배열에 없는 동작을 두지 않는다고 명시 |
| API 추적표의 `BR-AIEXTRACT-011` 행이 신규 요소 미반영 | `recoveryPaths`·`ADJUST_CATEGORY` 반영 |

기계 점검 항목과 결과는 7절 검증 블록에 있다.

### 10.6 7차 리뷰 처리

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`DISCARD` 상태 표현 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800758086) | 폐기 결과를 상태·컬럼 조합으로 표현할 수 없고 설명도 롤백과 뒤섞임 | 데이터베이스 | 수정 필요 | `discarded_at` 컬럼을 추가해 `MANUAL_OVERRIDE`의 세 하위 상태(등록 유지·롤백 완료·폐기 완료)를 구분. `DISCARD`가 롤백이라는 잘못된 설명을 고치고 폐기 완료를 종결 상태로 명시 |
| [`ADJUST_CATEGORY` 단위 선택 규칙 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800855601) | 처리표가 세 `decision`만 열거 | 기타 | 수정 필요 | 네 `decision`에 같은 `unitId` 규칙을 적용한다고 명시 |
| [상태도의 `MANUAL_OVERRIDE` 세분 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800855606) | 종결처럼 표시되지만 API는 후속 전이 허용 | 기타 | 수정 필요 | 상태도에 세 하위 상태와 후속 전이를 표현하고, Enum이 아니라 저장 컬럼으로 구분됨을 표로 명시 |
| [요약 규칙 분기 테스트 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3800855609) | 3개 분기 중 2개가 테스트에서 빠짐 | 기타 | 수정 필요 | `TST-E3-AI-007`을 3개 분기로 재서술하고 등록 단위 0개 경계를 별도 케이스로 추가 |

`DISCARD` 건은 이번 PR에서 처음 만든 것이 아니라 **기존 계약에 있던 동작을 새 상태 모델이 표현하지 못하게 된 경우**다. 새 개념을 추가할 때 그 개념이 기존 동작을 모두 담는지 확인하지 않으면, 손대지 않은 기능이 표현 불가 상태가 된다는 점을 확인했다.

### 10.7 합의 대기 표시의 누락 범위

`inan0226`과 `w00lam`이 같은 지적을 각각 남겼다. `BR-AIEXTRACT-001`에 추가한 등록 단위 분해와 후보 수 상한·절삭 표시는 기존 Accepted 규칙에 얹혔는데 `합의 대기` 표시가 없었고, ADR의 되돌림 목록에도 이 위치와 PRD·와이어프레임·`TST-E3-AI-008`이 빠져 있었다.

표시를 네 곳에만 붙이고 "그 문서 전체"라고 생각한 것이 원인이다. 실제로는 기존 Accepted 문서 안에 새 항목을 끼워 넣은 위치가 더 있었다.

- `BR-AIEXTRACT-001`, PRD 문서 머리말, 와이어프레임 4절, 테스트 매트릭스 3절에 표시를 추가했다.
- ADR 1절의 되돌림 범위를 문서군별 표로 바꿔 요구사항·제품·API·데이터·계획 다섯 갈래를 전부 열거했다.

### 10.8 8차 리뷰 처리

박진영이 1건을 남겼고, 이우람은 재교차 대조 후 새로 발견한 문제가 없음을 확인했다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [`MANUAL_OVERRIDE` 하위 상태 판별 불가 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801189874) | 저장 계층과 상태도는 세 하위 상태를 구분하는데 API 응답에 판별 필드가 없음 | 기타 | 수정 필요 | `manualOverrideType`(`null`·`ROLLED_BACK`·`DISCARDED`) 추가, 최상위 요약 규칙 3순위 조건 확장, null 규칙에 폐기 완료 포함 |

7차에서 저장 컬럼(`rolled_back_at`·`discarded_at`)과 상태도는 고쳤지만 그 두 컬럼을 API 응답 필드로 노출하는 것을 놓쳤다. 계층을 하나 고치면 그 결과를 소비하는 다음 계층까지 반드시 확인해야 한다는 이번 PR 전체의 반복 패턴과 같다.

### 10.9 9차 리뷰 처리

박진영이 승인했다. 이우람이 3건을 남겼고, 그중 1건(P1)은 잘못된 동작이 실제로 호출되면 422가 나는 문제였다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [최상위 조합표에 폐기 완료 누락 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801221528) | 3순위 조건은 넓혔는데 조합표·상태 설명은 그대로 | 기타 | 수정 필요 | 상태 설명과 조합표를 `MANUAL_OVERRIDE 단위(세 하위 상태 포함)`로 통일 |
| [PRD 검증 충돌 행이 낡은 서술 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801221535) | "후보 수정·재검수·폐기"가 현재 `recoveryPaths`·`review` 계약과 다름 | 기타 | 수정 필요 | 행을 사유별 `recoveryPaths`와 `CONFIRM`·`DISCARD`로 재작성 |
| [예외 화면에 미허용 롤백 버튼 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801221539) | `AUTO_BLOCKED` 화면에 `[비공개·롤백]` 노출. 호출 시 422 | 기타 | 수정 필요 | 롤백 버튼 제거, 두 예외 목업이 중복이라 하나로 병합하고 "등록 결과 없는 화면은 롤백 없음" 근거 추가 |

세 번째 건을 처리하며 같은 `PLACE_AMBIGUOUS` 목업이 \`AI-EXTRACT-EXCEPTION\`과 \`AI-EXTRACT-VERIFY-CONFLICT\` 두 곳에 중복돼 있던 것을 확인했다. 병합 과정에서 만들어진 화면 이름 두 개가 실제로는 같은 화면이었다. 하나로 합치고 뒤쪽 절은 참조만 남겼다.

### 10.10 리뷰 대기 없이 선제 전수 재검증

9차 반영 뒤 다음 리뷰를 기다리지 않고, 5개 관점(상태 Enum·경계값, 차단 사유·복구 경로·오류 코드, 후보 상한·버전, 합의 대기 표시 범위, 데이터·API·추적표 상호 대응)으로 나눠 전체 계약 문서를 병렬 재검증했다. 발견한 항목을 심각도순으로 정리하고 전부 반영했다.

| 항목 | 심각도 | 문제 | 처리 |
|---|---|---|---|
| `FR-AIEXTRACT-003` 본문에 `DISCARD` 누락 | 기타 | 절 제목은 "자동 확정·예외 보정·**폐기**"인데 본문에 `DISCARD`·폐기 완료 언급이 전혀 없음 | 폐기 동작과 폐기 완료 종결 상태를 본문에 추가 |
| 와이어프레임 `AUTO_CONFIRMED` 결과 화면에 카테고리 보정 버튼 누락 | 기타 | PRD·API는 `ADJUST_CATEGORY`를 이 화면의 허용 동작으로 정의하는데 목업 버튼 행에는 없음 | `[카테고리 보정]` 버튼 추가 |
| API 스펙 두 `recoveryPaths` 표 불일치 | 기타 | 3.6절 정규 표는 `VISIT_EVIDENCE_REQUIRED`·`EXTERNAL_SERVICE_ERROR`에 `MANUAL_REGISTRATION`을 포함하는데, 3.5절 산문 표는 그 경로를 빠뜨림 | 두 사유의 복구 경로 열에 "또는 기존 수동 등록" 추가, 두 표가 같은 의미임을 명시 |
| 고아 오류 코드 `AIEXTRACT_DUPLICATE_CONFLICT` | 데이터베이스 | 문서 어디서도 인용되지 않고, 업무 중복·동시성 두 개념을 뒤섞은 설명 | 3.1절 URL 충돌 전용임을 명시하고 `blockReason`의 `DUPLICATE_CONFLICT`·`AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`와 다르다고 교차 참조 |
| 3.5절 "동시 검수 충돌" 서술이 코드명 없음 | 기타 | 어떤 오류 코드인지 밝히지 않아 위 고아 코드와 혼동 여지 | `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`로 명시 |
| 테스트 매트릭스 "recoveryPaths 10개" | 기타 | 실제 표는 7개 사유 + 3개 거절 = 10행이 아니라 등록 단위 0개 거절까지 포함해 실제로는 사유 7 + 거절 상황 표기가 갱신 안 됨 | "11개 상황별 매핑"으로 정정 |
| 데이터 계약 `block_reason` 예시 목록 불완전 | 기타 | 7종 중 3종만 나열하고 나머지를 "기존 검증 실패 코드"로 뭉뚱그림 | 7종 전부 명시 |
| PRD 화면 상태표에 롤백완료·폐기완료 행 없음 | 기타 | 와이어프레임은 세 하위 상태를 구분하는데 PRD 표는 등록 완료 행 하나뿐 | 롤백 완료·폐기 완료 행 추가 |
| `BR-AIEXTRACT-002` 등록 단위 원자성 개정에 합의 대기 표시 없음 | 기타 | ADR 되돌림 표는 이 개정을 합의 대기로 지정하는데 BR-002 본문에 표시가 없음 | 표시 추가 |
| `FR-AIEXTRACT-003`에 합의 대기 표시 없음 | 기타 | 같은 이유 | 표시 추가 |
| 사용자 흐름(`third-expansion-user-flows.md`)에 합의 대기 표시 없음 | 기타 | ADR 되돌림 표가 "사용자 흐름의 등록 단위 판정 서술"을 명시하는데 그 문서 자체에는 표시가 없음 | 3절 머리말에 표시 추가 |
| 손실 분석 9·9.1절에 합의 대기 표시 없음 | 기타 | frontmatter가 `status: ACCEPTED`이고 9절도 표시 없이 결정을 서술 | frontmatter는 1~8절이 실제로 Accepted이므로 유지하고, 9절 머리말에 "9절과 9.1절 전체는 합의 대기, 1~8절은 Accepted 유지"를 명시 |
| API 스펙 자체 머리말의 합의 대기 범위가 ADR보다 좁음 | 기타 | ADR 되돌림 표는 `manualOverrideType`·`ADJUST_CATEGORY`·요약 규칙·`recoveryPaths`·오류 코드 3종까지 명시하는데 API 문서 머리말은 그중 일부만 나열 | ADR과 동일한 전체 목록으로 확장 |

**패턴**: 12건 중 다수가 여전히 "규칙을 고치면서 그 규칙이 등장하는 다른 위치를 다 훑지 않는다"는 이 PR 전체의 재발 패턴이었다. 합의 대기 표시 3건은 특히 트러블슈팅 10.7절이 지적한 것과 같은 원인 — ADR의 되돌림 목록이 권위 있는 마스터 목록인데, 개별 문서의 표시는 그 목록을 따라가지 못하고 뒤처졌다.

스테일 앵커 2건(`BR-AIEXTRACT-004`, `BR-AIEXTRACT-008`의 수동 `<a id>` 태그가 과거 제목과 불일치)도 발견했으나, `git log -S`로 확인한 결과 이 PR 이전 PR #167(초기 3차 확장 구축)부터 있던 문제이고 실제 링크는 GitHub 자동 생성 앵커로 정상 작동해 이번 PR 범위에서 다루지 않았다.

### 10.11 10차 리뷰 처리

10.10절 선제 전수 재검증 커밋(`d5f0931`) 직후 `w00lam`이 3건을 남겼다. 이 커밋이 push된 지 2~3분 뒤에 작성된 코멘트인데, 그중 2건은 실제로는 그 커밋 이전 커밋(`47067fb`, 9차 반영)에서 이미 고쳐진 내용을 가리키고 있었다. 코드를 먼저 확인한 뒤 처리 판단을 내렸다.

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [최상위 조합표 폐기 완료 재확인 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801306147) | 조합표가 "사후 보정·롤백 포함"만 적어 폐기 완료가 빠진 것처럼 읽힘 | 기타 | 이미 해결 | 조합표(2.1절)는 9차 반영(`47067fb`, 커밋 시각 05:18:54Z)에서 이미 `MANUAL_OVERRIDE 단위(등록 유지·롤백 완료·폐기 완료 어느 하위 상태든) 포함 어떤 조합`으로 세 하위 상태를 전부 명시했다. 리뷰 코멘트 시각(05:36:44Z)이 그보다 늦어 코멘트가 반영 전 버전을 가리킨 것으로 확인했다 |
| [3.5·3.6절 복구 경로 표 재불일치 (P2)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801306155) | `DUPLICATE_CONFLICT`를 3.6절은 `EXISTING_RESOURCE`, 3.5절은 "없음"으로 서로 다르게 적고, 요약 문장은 "세 사유 모두 재추출·재실행"이라고 해 중복 사유에도 재추출 경로가 있는 것처럼 서술 | 기타 | 수정 필요 | 3.5절 표의 `DUPLICATE_CONFLICT` 행을 "`EXISTING_RESOURCE`. 이미 등록된 자원으로 이동해 확인한다"로 바꿔 3.6절과 맞추고, 요약 문장을 `MISSING_REQUIRED_FIELD`·`VISIT_EVIDENCE_REQUIRED`·`EXTERNAL_SERVICE_ERROR` 세 사유만 명시하도록 좁히고 `DUPLICATE_CONFLICT`는 재추출·재실행·수동 등록 우회 경로가 없다고 별도로 적었다 |
| [`AUTO_BLOCKED` 검증 충돌 목업 롤백 버튼 재확인 (P1)](https://github.com/team-youngkk/masit-on/pull/226#discussion_r3801306161) | `[비공개·롤백]` 버튼이 `AUTO_BLOCKED`에서 호출 시 422가 되므로 제거 필요 | 기타 | 이미 해결 | 이 버튼은 9차 반영(`47067fb`)에서 이미 제거됐다. `AI-EXTRACT-VERIFY-CONFLICT` 절은 현재 목업 자체를 두지 않고 `PLACE_AMBIGUOUS` 목업(롤백 버튼 없음)을 참조만 하며, "등록 결과가 없고 롤백 동작을 두지 않는다"는 서술만 남아 있다. `git blame`으로 제거 커밋과 시각을 확인했다 |

**원인 확정에 실제로 재현한 문제(2번째 스레드)**: 10.10절 선제 재검증 커밋이 3.5절에 "이 표의 복구 경로 열은 3.6절 `recoveryPaths` 배열과 같은 의미이며, 세 사유 모두 재추출·재실행 뒤에도 관리자가 기존 수동 등록으로 우회할 수 있다는 점은 동일하다"는 요약 문장을 새로 추가하면서, 바로 위 `DUPLICATE_CONFLICT` 행의 "없음"이라는 기존 서술과 대조하지 않았다. `DUPLICATE_CONFLICT`는 `EXISTING_RESOURCE`만 갖고 재추출·재실행 경로 자체가 없으므로, 새로 쓴 요약 문장이 표 전체(7행)를 가리키는 것으로 읽히면 사실과 어긋난다. **선제 전수 재검증 커밋 자체가 표를 요약하면서 새로운 불일치를 만든 경우**로, 10절 전체가 반복해서 보여준 "표를 고치면서 그 표 전 행을 대조하지 않는다" 패턴이 자기 자신을 재검증하는 커밋에서도 재발했다.

**남은 2건이 이미 해결로 처리된 이유**: `git blame`과 커밋 시각(9차 반영 `47067fb` 05:18:54Z, 이번 리뷰 코멘트 05:36:44Z)을 대조해 리뷰가 남겨질 때 이미 코드에 반영돼 있었음을 확인했다. 코멘트 자체를 근거 없이 기각하지 않고 파일 히스토리로 재현을 시도한 뒤 판단했다.

| 파일 | 변경 |
|---|---|
| [ai-video-extraction-api.md](../05-specs/api/admin/ai-video-extraction-api.md) | 3.5절 `DUPLICATE_CONFLICT` 복구 경로 행과 요약 문장을 3.6절 정규 표와 1:1로 맞춤 |

검증: 3.6절 `recoveryPaths` 표(`DUPLICATE_CONFLICT` → `["EXISTING_RESOURCE"]`)와 3.5절 산문 표·요약 문장을 대조해 세 사유(`MISSING_REQUIRED_FIELD`·`VISIT_EVIDENCE_REQUIRED`·`EXTERNAL_SERVICE_ERROR`)와 `DUPLICATE_CONFLICT`가 서로 다른 경로를 갖는다는 서술이 두 절에서 일치하는 것을 확인했다. 문서만 변경해 빌드·테스트 대상은 없다.

## 11. 비교 지표

해당 없음. 문서 계약 변경이며 측정할 런타임 지표가 없다. 자동 등록률·`CATEGORY_UNRESOLVED` 비율 등은 구현 후 [PRD 11절 지표](../04-product/prd/admin/ai-video-information-extraction.md)에서 측정한다. 이 PR 시점에는 기준선을 만들 수 없다.
