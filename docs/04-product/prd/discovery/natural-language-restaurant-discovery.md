---
id: PRD-DISCOVERY-005
title: 자연어 맛집 탐색
status: approved
workstream: WS-14
owner: 양성훈
reviewers:
  - 이우람
related_requirements:
  - FR-NLSEARCH-001
  - FR-NLSEARCH-002
  - FR-NLSEARCH-003
  - FR-NLSEARCH-004
related_business_rules:
  - BR-NLSEARCH-001
  - BR-NLSEARCH-002
  - BR-NLSEARCH-003
related_nfr:
  - NFR-ACCURACY-001
  - NFR-SECURITY-007
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-PERFORMANCE-007
  - NFR-AVAILABILITY-003
  - NFR-OBSERVABILITY-005
  - NFR-TEST-006
related_documents:
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../08-planning/third-expansion-scope-and-terminology.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - restaurant-discovery.md
  - ../../user-flows/third-expansion-user-flows.md
  - ../../wireframes/third-expansion-wireframes.md
---

# 자연어 맛집 탐색 PRD

## 1. 목적과 사용자 문제

사용자가 서비스의 필터 이름과 조합 방식을 먼저 학습하지 않아도 “성수에서 냉면 태그가 있고 백종원이 방문한 한식집”처럼 원하는 조건을 문장으로 표현해 기존 공개 맛집 목록을 찾게 한다. 이 기능은 의미가 비슷한 맛집을 새로 추천하거나 질문에 답하는 챗봇이 아니라, 문장을 기존 탐색 조건과 관리자 확정 태그로 변환하는 입력 보조 기능이다.

## 2. 대상 사용자와 선행 조건

- 대상: 로그인 여부와 관계없이 공개 맛집을 찾는 일반 사용자
- 선행 조건: 기존 맛집 목록·이름 검색·서울 자치구·음식 카테고리·유튜버 필터, 활성 `TagDefinition`·확정 `VisitTag`와 공개·생명주기 판정
- 제품 의존성: [맛집 탐색 PRD](restaurant-discovery.md)의 목록·페이지·정렬·빈 결과 계약
- 구현은 [WS-14](../../../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색)가 담당하며 최종 책임자는 양성훈, 기본 리뷰어는 이우람이다.

## 3. 목표와 성공 기준

- 사용자는 한 문장으로 지원 조건을 입력하고 실제 적용된 조건을 확인한다.
- 직접 지정 필터와 자연어가 충돌해도 사용자가 선택한 필터가 유지된다.
- 결과 없음과 해석 실패를 구분하고 기존 필터로 복구할 수 있다.
- 초기 품질 목표는 평가 문장 200개 이상, 조건 집합 exact match 90% 이상, 지원 조건 재현율 95% 이상이며 평가 결과 후 확정한다.

## 4. 범위

### 포함

- 자연어 문장 하나의 입력
- 맛집 이름·서울 자치구·음식 카테고리·유튜버·관리자 확정 태그 조건 해석
- `MENU`, `TASTE`, `OCCASION`, `ATMOSPHERE` 유형의 허용 태그와 태그별 별칭 해석
- 직접 지정 필터와 해석 조건의 조합
- 실제 적용 조건과 제외된 해석 조건 요약
- 기존 공개·활성 맛집 목록·페이지·정렬
- 정상 빈 결과, 해석 실패와 미지원 조건 상태

### 제외

- 임베딩·벡터 유사도 검색, RAG와 자유 형식 챗봇
- 관리자 확정 태그가 아닌 원문 메뉴·분위기·가격대·감정 표현의 자유 검색과 오타 자동 교정
- 검색 결과에 대한 생성 설명·선정 이유
- 검색 기록 저장·개인화·현재 위치·예약·결제

## 5. 핵심 사용자 흐름

1. 사용자가 자연어 검색 입력에 문장을 작성하고 필요하면 기존 필터를 직접 선택한다.
2. 시스템은 문장을 지원 조건으로 해석하고 같은 종류의 직접 필터를 우선 적용한다.
3. 화면은 적용 조건, 직접 필터 때문에 제외된 조건과 미지원 조건을 구분해 보여준다.
4. 유효한 조건이 있으면 기존 공개 맛집 목록을 표시한다.
5. 결과가 0건이면 적용 조건을 유지한 빈 상태를, 조건을 해석하지 못하면 필터 선택으로 복구하는 실패 상태를 표시한다.

상세 흐름은 [3차 확장 사용자 흐름](../../user-flows/third-expansion-user-flows.md#2-자연어-맛집-탐색)을 따른다.

## 6. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-NLSEARCH-001 | 자연어 문장을 지원하는 기존 조건으로 해석하고 적용 조건 요약과 맛집 목록을 반환한다. | FR-NLSEARCH-001 |
| PR-NLSEARCH-002 | 같은 종류의 자연어 조건과 직접 필터가 충돌하면 직접 필터를 적용한다. | FR-NLSEARCH-002, BR-NLSEARCH-001 |
| PR-NLSEARCH-003 | 서로 다른 조건 종류는 기존 탐색과 같은 AND 의미로 조합한다. | FR-NLSEARCH-002 |
| PR-NLSEARCH-004 | 정상 빈 결과와 해석 실패·미지원 조건을 분리한다. | FR-NLSEARCH-003, BR-NLSEARCH-002 |
| PR-NLSEARCH-005 | 공개·활성 맛집과 공개·유효 관계만 결과 근거로 사용한다. | BR-NLSEARCH-002 |
| PR-NLSEARCH-006 | 관리자 확정 태그를 기존 조건처럼 적용하고 여러 태그는 AND로 조합한다. | FR-NLSEARCH-004, BR-NLSEARCH-003 |

## 7. 화면과 상태

| 상태 | 사용자에게 보여줄 내용 | 다음 행동 |
|---|---|---|
| 초기 | 예시 문장, 자연어 입력, 기존 필터 | 문장 입력 또는 필터 선택 |
| 해석·조회 중 | 입력과 필터를 유지한 진행 상태 | 중복 제출 방지 |
| 정상 결과 | 적용 조건 요약, 결과 수, 기존 맛집 카드·페이지 | 조건 수정, 상세 이동 |
| 빈 결과 | 적용 조건과 일치 결과 0건 | 문장 수정 또는 직접 필터·태그 변경 |
| 해석 실패 | 해석하지 못한 이유, 지원 조건 안내 | 기존 필터 사용·문장 수정 |
| 일부 미지원 | 적용 조건과 미지원 표현을 분리 | 지원 조건만으로 계속 또는 수정 |
| 입력 오류 | 오류 코드별 안내와 잘못된 조건 필드·사유 | 해당 조건 수정 후 재검색 |
| 서버 오류 | 입력을 유지한 오류와 재시도 | 재시도·기존 필터 탐색 |

입력 오류 안내는 오류 본문의 `message`만 쓰지 않고 `code`와 `errors[].field`를 함께 읽는다. 같은 400인 `NATURAL_LANGUAGE_EMPTY`·`INVALID_FIELD_VALUE`·`INVALID_IDENTIFIER`는 서로 다른 안내 문구로 구분하고, `errors[].field`는 사용자용 조건 이름으로 바꿔 사유와 함께 보여준다.

직접 지정 태그 필터(`filters.tags`)는 자연어 검색 영역에서 선택한다. [맛집 탐색 API](../../../05-specs/api/discovery/restaurant-discovery-api.md) 6절이 여러 태그의 AND 조합을 자연어 API의 `filters.tags`로 넘기고 목록 조회는 태그 1개(`tag`)만 받기 때문이다. 선택은 최대 5개이며 같은 종류의 자연어 태그와 충돌하면 PR-NLSEARCH-002에 따라 직접 선택이 적용된다.

태그만으로는 검색하지 않는다. `sentence`가 필수이므로 태그 선택은 다음 문장 검색에 함께 적용되고, 문장 없이 검색하면 문장 입력 안내를 표시한다. 태그 선택을 바꿔도 자동으로 다시 조회하지 않는다는 점은 구조화 필터와 같으며, 선택 영역에 적용 시점과 자연어 태그 대체를 함께 안내한다. URL이 소유한 직접 필터(이름·자치구·음식·유튜버)가 바뀌면 자연어 영역의 문장·태그 선택과 이전 결과를 초기화한다. 구조화 필터 폼 GET 제출과 유튜버 필터 해제 같은 화면 내 이동을 모두 포함한다. 목록 페이지 이동은 직접 필터 변경이 아니므로 초기화하지 않는다.

태그 선택지는 [AI 영상 추출 데이터 계약](../../../05-specs/data/third-expansion-ai-video-data-contract.md)의 확정 태그 seed와 같은 목록을 화면 상수로 유지한다. 활성 태그 목록을 조회하는 공개 API가 없으므로, 태그가 `DEPRECATED`로 바뀌면 화면에는 남고 서버가 `INVALID_FIELD_VALUE`로 거부한다. 태그 lifecycle을 실제로 변경하기 전에 선택지 공급 방식을 함께 결정한다.

화면 구조는 [3차 확장 와이어프레임](../../wireframes/third-expansion-wireframes.md#3-자연어-맛집-탐색)을 따른다.

## 8. 품질·개인정보·비용

- 자연어 입력 원문과 검색 이력을 저장하거나 일반 로그에 남기지 않는다.
- Prompt Injection과 Schema 이탈 입력을 실행하지 않고 허용된 조건만 적용한다.
- 자연어 해석과 목록 조회는 정상 부하에서 내부 처리 p95 800ms 이하, 서버 오류율 1% 미만을 충족한다.
- 신규 외부 유료 호출 비용은 월 0원이며 임베딩 호출·저장은 0건이다.
- 해석 기능 장애는 기존 구조화 필터·목록·상세에 전파하지 않는다.

## 9. 지표와 운영

- 해석 성공률, 조건 집합 exact match, 지원 조건 재현율
- 미지원 조건 비율과 직접 필터 충돌 비율
- 빈 결과율, 해석 실패 뒤 구조화 필터 사용률
- 응답 시간 p95와 오류율
- 원문 대신 요청 식별자·입력 해시·규칙 또는 모델 버전·오류 범주만 관측한다.

## 10. 완료 조건

- [ ] FR-NLSEARCH-001~004와 BR-NLSEARCH-001~003의 정상·빈 결과·충돌·태그 AND·해석 실패가 검증된다.
- [ ] 고정 평가 데이터와 회귀 보고서가 있고 NFR-ACCURACY-001 목표값을 팀이 확정한다.
- [ ] 입력 원문·검색 이력·임베딩 저장 0건과 로그 민감정보 차단이 검증된다.
- [ ] 정상 50명·20 RPS와 최대 200명·80 RPS에서 성능·장애 격리를 검증한다.
- [ ] API·데이터·ADR·Workstream·담당자와 화면 문서가 승인된다.

## 11. 운영 리스크와 변경 게이트

- 운영 표현 변화와 회귀 Dataset 갱신은 `parserVersion`을 증가시키는 변경으로 관리한다.
- 태그 코드·별칭·폐기 정책 변경은 [AI 영상 추출 데이터 계약](../../../05-specs/data/third-expansion-ai-video-data-contract.md)의 seed·lifecycle 규칙을 따른다.
- 지원 범위를 넓히거나 LLM·임베딩을 도입하려면 별도 범위 변경·ADR·평가 게이트가 필요하다.
