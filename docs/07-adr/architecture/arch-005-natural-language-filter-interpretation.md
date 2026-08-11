---
id: ADR-ARCH-005
title: 자연어 조건 해석과 기존 필터 조회 경계
status: Accepted
decision_date: 2026-08-10
owners:
  - 양성훈
related_requirements:
  - FR-NLSEARCH-001
  - FR-NLSEARCH-002
  - FR-NLSEARCH-003
  - BR-NLSEARCH-001
  - BR-NLSEARCH-002
  - NFR-ACCURACY-001
  - NFR-PERFORMANCE-007
  - NFR-SECURITY-007
related_documents:
  - ../../02-analysis/third-expansion-domain-boundaries.md
  - ../../02-analysis/third-expansion-workstreams.md
  - ../../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
  - ../architecture/arch-001-domain-monolith.md
  - ../architecture/arch-002-external-ports-adapters.md
  - ../adr-backlog.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-ARCH-005 자연어 조건 해석과 기존 필터 조회 경계

## 1. 상태

Accepted. 2026-08-10 팀 결정으로 P1의 지원 필드·태그·규칙 기반 해석·평가 목표를 확정했다.

## 2. 결정 요약

자연어 검색은 별도 검색 도메인·임베딩 색인·RAG 저장소를 만들지 않고, [WS-14](../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색)의 조회 애플리케이션에서 자연어를 기존 맛집 조건으로 해석한다. 해석 결과는 요청 범위의 구조화 조건으로만 취급하고 Restaurant·Creator·Visit의 기존 조회 Port를 통해 목록을 반환한다.

해석 실패·미지원 조건·후보 충돌은 전체 목록으로 조용히 대체하지 않는다. 직접 지정 필터가 있으면 같은 조건 종류에서 직접 지정 필터를 우선하며, 실제 적용 조건은 요약 정보로 반환한다.

## 3. 배경

3차 확장의 자연어 검색 범위는 자유 형식 챗봇이나 의미 유사도 검색이 아니라 기존 필터 기반 목록 조회다. 따라서 자연어 입력을 새로운 검색 결과 모델로 확장하면 기존 공개 상태·Visit 관계·정렬·페이지 계약이 중복되고 품질 평가 범위가 불필요하게 커진다.

현재 서비스는 Restaurant가 이름·지역·카테고리 조회를, Visit와 Creator가 유튜버 관계와 공개 상태를 소유한다. 자연어 기능이 이 Entity를 직접 조회·변경하거나 자체 검색 색인을 소유하면 도메인 경계가 중복된다.

## 4. 결정 문제

자연어 문장을 어떤 경계에서 기존 검색 조건으로 바꾸고, 실패·충돌·미지원 표현을 어떻게 격리할 것인가.

결정은 다음 제약을 만족해야 한다.

- 기존 목록의 공개·활성 상태와 Visit 유효성 규칙을 우회하지 않는다.
- 검색 결과의 사실과 순위를 AI가 새로 생성하지 않는다.
- 입력 원문·비밀정보가 로그와 평가 자산으로 유출되지 않는다.
- 초기 단일 EC2와 기존 탐색 기능을 보호한다.

## 5. 고려한 선택지

- **새 자연어 검색 도메인과 영속 검색 인덱스**: 해석·색인·재생성 생명주기를 독립적으로 관리할 수 있지만, 초기 범위에 없는 임베딩·색인·동기화 비용과 새 데이터 소유권을 만든다.
- **RAG 또는 챗봇으로 결과 설명 생성**: 사용자 대화 경험은 확장되지만, 초기 범위의 기존 목록 반환을 벗어나며 근거 없는 답변·개인정보·저작권 위험이 증가한다.
- **WS-14 조회 애플리케이션에서 구조화 조건으로 해석**: 기존 조회 Port와 공개 정책을 재사용하고 실패를 조건 해석 단계에서 격리할 수 있다. 해석 방식 자체의 품질 평가와 오류 계약은 별도로 필요하다.

## 6. 결정

세 번째 선택지를 Accepted 기준으로 채택한다.

팀은 자연어 검색 P1을 외부 LLM·임베딩·RAG·챗봇 없이 규칙·사전 기반 기존 필터 해석으로 확정했다. 지원 조건 밖의 표현은 조용히 추정하지 않고 `PARTIAL` 또는 `FAILED`로 반환한다. LLM 도입은 현재 결정의 후속 구현이 아니라 별도 범위·ADR·비용·개인정보 승인 대상이다.

- 입력은 요청 처리 중 해석하고, 초기에는 자연어 원문과 해석 결과를 장기 영속하지 않는다.
- P1 해석기는 지원 사전·문장 패턴·정규화 규칙을 사용하고 `parserVersion: P1`을 반환한다.
- 출력은 `restaurantName`, `district`, `category`, `creator`, `tags`에 해당하는 기존 조건의 구조화 값과 해석 상태로 제한한다. 태그는 데이터 계약의 초기 18개 seed만 사용한다.
- 구조화 조건은 기존 목록 조회의 조건 조합·페이지·정렬·공개 상태 계약을 사용한다.
- 유튜버 조건은 Creator 선택 정보와 Visit의 유효 관계를 기존 계약으로 조합한다.
- 같은 조건 종류에서 자연어와 직접 필터가 충돌하면 직접 필터가 우선한다.
- 해석 실패·미지원 조건·낮은 확신·복수 후보는 명시적인 상태로 반환하며 전체 목록으로 대체하지 않는다.
- 태그는 모두 AND로 조합하고, 별칭이 여러 코드와 충돌하면 임의 선택하지 않고 `UNRESOLVED`로 반환한다.
- 임베딩·pgvector·RAG·대화 메모리·새 추천 순위는 도입하지 않는다. 관련 후보인 [ADR-SEARCH-002](../adr-backlog.md#adr-search-002-pgvector-자연어-검색rag)는 Post-MVP 상태를 유지한다.

## 7. 강제 규칙

- WS-14는 Restaurant·Creator·Visit Entity와 Repository를 직접 변경하지 않는다.
- 해석 결과를 통해 비공개·삭제 맛집을 노출하지 않는다.
- 해석 결과에 없는 메뉴·분위기·가격·영업 여부를 기존 조건으로 추정하지 않는다.
- 입력 원문·자막·외부 API 키·인증정보를 애플리케이션 로그에 기록하지 않는다.
- 규칙 해석 결과는 구조화 Schema 검증과 허용 값 정규화를 통과한 경우에만 목록 조회에 전달한다.
- Prompt Injection 또는 구조화 출력 이탈은 해석 실패로 처리하고 검색 결과를 반환하지 않는다.

## 8. 트레이드오프

이 결정은 초기 자연어 표현 범위와 확장성을 제한한다. 대신 기존 검색과 공개 정책의 회귀 위험, 새 색인 운영 비용, 사용자에게 근거 없는 설명을 제공할 위험을 줄인다. 해석 품질이 충분하지 않으면 규칙을 확장하기 전에 지원 표현을 줄이거나 직접 필터 입력으로 복구할 수 있다.

## 9. 검증 방법과 실행 게이트

- `EVAL-NL-*` 골든 Dataset에서 필드 추출·정규화·직접 필터 우선·실패 처리를 평가한다.
- 기존 이름·지역·카테고리·유튜버 필터와 페이지·정렬·공개 상태 회귀 테스트를 실행한다.
- Prompt Injection·악성 입력·Schema 이탈·입력 원문 로그 노출을 검증한다.
- 자연어 검색 응답 시간과 기존 탐색 기능 장애 격리를 측정한다.
- [NFR-ACCURACY-001](../../01-requirements/non-functional-requirements.md#nfr-accuracy-001-자연어-검색-정확도와-평가-데이터)의 240건·60/20/20 분할과 exact match 90%·재현율 95% 목표를 적용한다.

## 10. 확정 운영 규칙

- P1은 지원 사전·문장 패턴·정규화 규칙을 사용하고 모든 규칙·태그 seed 변경은 `parserVersion`을 증가시킨다.
- LLM·임베딩 도입은 현재 범위에 포함하지 않으며 별도 범위 변경·ADR·비용·개인정보 승인 없이는 허용하지 않는다.
- API 오류·응답 Schema와 지연 목표는 [자연어 맛집 탐색 API](../../05-specs/api/discovery/natural-language-restaurant-discovery-api.md) 계약을 따른다.
