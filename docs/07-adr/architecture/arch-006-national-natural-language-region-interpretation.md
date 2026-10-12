---
id: ADR-ARCH-006
title: 전국 자연어 지역 해석 P2와 지역 마스터 경계
status: Proposed
decision_date: 2026-10-12
owners:
  - 양성훈
  - 이우람
related_requirements:
  - FR-NLSEARCH-001
  - FR-NLSEARCH-002
  - FR-NLSEARCH-003
  - FR-RESTAURANT-003
  - BR-NLSEARCH-001
  - BR-NLSEARCH-002
  - NFR-ACCURACY-001
  - NFR-PERFORMANCE-007
  - NFR-SECURITY-007
related_documents:
  - ../README.md
  - ../adr-index.md
  - ../adr-traceability.md
  - arch-005-natural-language-filter-interpretation.md
  - ../../05-specs/api/common/region-contract.md
  - ../../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../../08-planning/issue-400-national-natural-language-regions.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
  - ../../08-planning/third-expansion-test-matrix.md
supersedes: []
superseded_by: null
---

# ADR-ARCH-006 전국 자연어 지역 해석 P2와 지역 마스터 경계

## 1. 상태와 결정 근거

`Proposed`. [#400](https://github.com/team-youngkk/masit-on/issues/400)의 구현을 검토하는 독립 ADR이다. API·지역 응답 계약은 김인안이 [2026-10-11 승인](https://github.com/team-youngkk/masit-on/pull/403#issuecomment-6107809352)했다. 이우람은 [2026-10-12 결정](https://github.com/team-youngkk/masit-on/pull/403#issuecomment-6116173635)에서 기존 ADR-ARCH-005 §11 확정 대신 새 ADR 분리와 인덱스·추적표·구현 계획 갱신을 요청했다. 새 문서 변경과 해당 커밋의 CI 확인 후 아키텍처 소유자가 승인할 예정이며, 아직 `Accepted`로 간주하지 않는다.

ADR-ARCH-005의 운영 지역 조건·파서 버전·P2 평가 경로에 대한 후속 범위를 제안한다. 기존 P1·Golden V1 결정과 과거 회귀 기준은 보존하고, 조회 Application·출력 Port·공개 상태·태그 AND·악성 입력 방어 경계는 이어받는다. 기존 ADR 전체를 대체하는 결정이 아니므로 `supersedes`를 비워 두고 `related_documents`와 본문으로 관계를 연결한다. 승인 시에도 P1 이력을 보존하고 P2에 적용되는 문서 상태·추적표를 갱신한다.

## 2. 배경과 결정 문제

전국 직접 필터와 활성 지역 마스터가 도입됐지만 P1 자연어 지명은 서울 중심이다. `부산 해운대구 한식`을 기존 목록과 같은 지역 범위로 검색하고, `중구`처럼 여러 부모 아래 존재하는 하위를 서울로 임의 선택하지 않아야 한다. 전국 지역 해석을 P1 문장·정답에 섞으면 과거 평가 기준도 달라진다.

활성 마스터를 어떤 경계로 읽고, 시·도/시·군·구 문맥·모호성·직접 필터·실패를 어떤 조건과 버전으로 표현할지 결정한다.

## 3. 고려한 선택지와 제안

| 선택지 | 판단 | 이유 |
|---|---|---|
| 서울 정적 사전에 전국 이름을 추가하고 P1 유지 | 채택하지 않음 | 활성 여부와 부모 문맥이 DB와 분리되고 알고리즘 변경을 과거 평가와 구분할 수 없음 |
| 별도 검색 도메인·색인 또는 외부 LLM 도입 | 범위 제외 | 기존 필터 해석 범위를 넘고 새 저장·비용·개인정보 승인이 필요함 |
| 기존 지역 마스터를 출력 Port로 읽는 P2 | 제안 | 기존 목록 범위와 활성 상태를 재사용하고 P1 회귀 이력을 보존할 수 있음 |

## 4. 지역 마스터와 의존 경계

- `NaturalLanguageParserAdapter`가 Restaurant Application 소유의 `RegionRepositoryPort.findAllActive()`로 마스터를 읽고 요청의 불변 해석 snapshot을 구성한다. `NaturalLanguageRegionResolver`는 전달된 값으로 판정하며 Domain·Application은 JPA·JDBC·HTTP Client를 직접 참조하지 않는다.
- 활성 시·도와 활성 부모 아래 시·군·구만 후보로 사용한다. 비활성 부모 아래 자식도 제외한다. 표시명·확인된 시·도 별칭과 활성 서울 부모·자식에 귀속 가능한 기존 동네 별칭을 사용한다.
- 지역 마스터 조회 실패는 `NATURAL_LANGUAGE_UNAVAILABLE` 503이다. 서울 정적 사전·stale snapshot·전체 목록으로 대체하지 않는다. 별도 지역 cache·라이브러리·마이그레이션을 추가하지 않는다.
- 태그의 기존 30초 read-through cache와 갱신 실패 503·stale/seed 대체 금지는 유지한다. 외부 제공자를 호출하지 않는다.

## 5. 해석과 API 조건

- 부모와 자식을 함께 인식하면 해당 부모 안에서만 찾는다. 하위 이름만 있으면 활성 후보가 하나인 경우만 확정한다. 부모 없는 `중구`·`강서구`, 복수 지역, 부모·자식 불일치는 `UNRESOLVED_VALUE`다.
- 긴 이름이 차지한 구간을 우선하고 단어 경계를 확인한다. 따옴표의 맛집 이름을 지역으로 해석하지 않으며 폐지 이름의 주소 정규화를 검색 별칭으로 무조건 적용하지 않는다.
- 확정한 전국 자연어 지역은 `regionCode`로 변환한다. 직접 `filters.regionCode` 또는 레거시 `filters.district`가 같은 지역 차원의 자연어 조건보다 우선한다. 두 직접 지역 필터의 동시 지정과 미등록·비활성 직접 코드는 400이다.
- 직접/자연어 코드 적용 시 `appliedConditions.regionCode`에 코드, `district`에 `null`을 반환한다. 직접 레거시 district 적용 시에는 district 이름과 null 코드를 반환한다. 실제 적용 지역이 같으면 비충돌, 다르거나 자연어 지역이 미해결이면 `field: regionCode`·`DIRECT_FILTER_WON`으로 설명한다.
- 시·도는 자신과 활성 자식, 하위는 자신만 조회한다. 세종은 자신, 제주는 제주시·서귀포시를 포함한다. 기존 공개 상태·유효 Visit·이름·카테고리·불투명 유튜버 ID·태그 AND·정렬·페이지 계약을 유지한다.
- 미해결 지역뿐이면 `FAILED`·빈 적용 조건·빈 결과, 다른 유효 조건을 적용하면 `PARTIAL`이다. 미해결 지명을 맛집 이름이나 전체 목록으로 대체하지 않는다. 확정 조건의 빈 목록은 정상 응답이다.
- 의심 입력은 유효한 직접 지역도 적용하지 않고 `FAILED`·빈 결과로 처리하되, 조회를 생략하는 경로에서도 직접 필터를 검증한다. 화면 라벨은 공개 마스터 이름 또는 안전한 안내를 사용한다.

## 6. 파서 버전과 평가

운영 알고리즘은 `parserVersion: P2`다. 지역 문맥·모호성·충돌 판정 변경이므로 P1과 구분한다. 태그 정의 데이터 변경만으로는 버전을 증가시키지 않는다.

기존 240건 Golden V1의 문장·정답·60/20/20 분할은 수정하지 않고 역사적 P1 경로로 평가한다. P2는 별도 `nlsearch-national-p2-v1.0.0` 보충 32문장과 현행 마스터 245개 공식 이름의 부모 문맥 검증을 사용한다. P1 점수를 P2 정확도나 운영 완료 근거로 보고하지 않는다.

## 7. 검증과 승인 게이트

1. 정상 지역·동명·불일치·폐지·비활성·세종·제주, 직접 필터 우선/동일 지역 비충돌, 의심 입력의 직접 검증, DB 장애 503을 parser·Service·MockMvc·PostgreSQL 테스트로 확인한다.
2. 기존 P1 Golden V1·동적 태그 경계·ArchUnit·이름/카테고리/유튜버/태그 AND와 공개 조회 범위를 확인한다. UI의 지역 라벨·충돌 안내·URL 직접 필터 변경 초기화를 확인한다.
3. 본 문서·ADR 인덱스·추적표·기술 정책·구현 계획을 동기화하고, 수정 커밋의 PR CI 결과를 확인한 후 아키텍처 소유자가 승인한다. 이 시점에 새 ADR 상태를 Accepted로 전환하고 상태 문서를 함께 갱신한다.
4. 실제 백엔드·브라우저 전국 통합 인수는 #401이다. 실사용 부하·운영 완료와 아키텍처 승인 여부는 각각 증거로 판정한다.

[develop 동기화 후 CI](https://github.com/team-youngkk/masit-on/actions/runs/38127809534)는 기존 구현 기준으로 전체 통과했다. 로컬 전체 빌드의 기존 WireMock HTTP 연결 오류 12건은 단독 재실행과 이 CI에서 재현되지 않았다. 이 기록은 이번 ADR 문서 수정 커밋의 CI를 대신하지 않는다.

## 8. 트레이드오프와 재검토 조건

요청마다 활성 지역을 읽으므로 지역 DB 장애에 의존하고, 모호한 하위 이름은 사용자가 부모를 추가해야 한다. 활성 마스터와 검색 결과의 범위를 일치시키기 위한 선택이다. cache가 필요할 정도의 병목, 마스터 계층 변경, 지역별 별칭 확장 또는 외부 해석기 도입은 별도 근거와 계약 검토를 거친다.
