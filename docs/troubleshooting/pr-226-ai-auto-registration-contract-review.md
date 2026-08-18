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

## 11. 비교 지표

해당 없음. 문서 계약 변경이며 측정할 런타임 지표가 없다. 자동 등록률·`CATEGORY_UNRESOLVED` 비율 등은 구현 후 [PRD 11절 지표](../04-product/prd/admin/ai-video-information-extraction.md)에서 측정한다. 이 PR 시점에는 기준선을 만들 수 없다.
