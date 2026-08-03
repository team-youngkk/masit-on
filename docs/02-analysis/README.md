---
related_documents:
  - domain-boundaries.md
  - mvp-workstreams.md
  - first-expansion-workstreams.md
  - second-expansion-domain-boundaries.md
  - second-expansion-workstreams.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../03-team/roles.md
  - ../03-team/ownership.md
  - ../06-architecture/module-boundaries.md
  - ../08-planning/mvp-2day-implementation-plan.md
---

# 분석

## 1. 목적

이 디렉터리는 확정된 요구사항을 **누가 무엇을 소유하고 어떤 단위로 나눠 만들 것인지**로 변환한다. 두 가지 축을 쓴다.

- **도메인**: 같은 업무 규칙과 변경 이유를 공유하는 책임 단위 (세로로 데이터·정책을 소유)
- **Workstream**: 한 명이 사용자 가치나 관리자 업무를 처음부터 끝까지 완성하는 작업 단위

둘은 일대일 대응하지 않는다. 한 Workstream이 여러 도메인의 협업을 포함할 수 있지만, 데이터와 정책의 소유권은 언제나 도메인을 따른다.

## 2. 문서 읽기 순서

1. [도메인 경계](domain-boundaries.md): Restaurant·Creator·Video·Visit의 책임, 소유 규칙, 협업 관계와 의존 방향
2. [MVP Workstream](mvp-workstreams.md): WS-01~WS-04의 범위, 요구사항 배정, 의존 관계와 병렬 개발 전략
3. [1차 확장 Workstream](first-expansion-workstreams.md): WS-05~WS-08의 회원·개인화·지도·유튜버 상세 책임과 `OPS-VALIDATION` 공통 운영·배포 트랙
4. [2차 확장 도메인 경계](second-expansion-domain-boundaries.md): 컬렉션·인기·큐레이션·참여·알림 책임과 패키지 결정 게이트
5. [2차 확장 Workstream](second-expansion-workstreams.md): WS-09~WS-13의 범위와 담당자·리뷰어

`mvp-workstreams.md`는 `domain-boundaries.md`를 입력으로 사용한다. 순서를 바꿔 읽으면 Workstream이 왜 그렇게 잘렸는지 이해하기 어렵다.

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `domain-boundaries.md` | 이 데이터와 규칙은 어느 도메인이 소유하는가? | 팀원 배정과 작업 순서 |
| `mvp-workstreams.md` | 이 기능은 누가 어디까지 책임지고 완성하는가? | 도메인 내부의 규칙 소유권 |

## 4. Workstream 요약

| WS | 범위 | 담당 |
|---|---|---|
| [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | 맛집 목록·검색·필터 | 양성훈 |
| [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 맛집 상세·콘텐츠 조회 | 박진영 |
| [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 유튜버 기반 탐색·Visit 판정 | 이우람 |
| [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 관리자 인증·데이터 등록 | 김인안 |
| [WS-05](first-expansion-workstreams.md#4-ws-05-사용자-계정인증) | 사용자 계정·인증 | 김인안 |
| [WS-06](first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) | 개인 맛집 관리 | 박진영 |
| [WS-07](first-expansion-workstreams.md#6-ws-07-지도-탐색) | 지도 탐색 | 양성훈 |
| [WS-08](first-expansion-workstreams.md#7-ws-08-유튜버-상세) | 유튜버 상세 | 이우람 |
| [OPS-VALIDATION](first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) | 검증 참여자 제한 공개 진입 경계 — 정식 공개 시 종료 | 이우람 |
| [WS-09](second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | 개인 컬렉션 | 박진영 |
| [WS-10](second-expansion-workstreams.md#5-ws-10-인기-맛집) | 인기 맛집 | 양성훈 |
| [WS-11](second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | 관리자 큐레이션 | 김인안 |
| [WS-12](second-expansion-workstreams.md#7-ws-12-제보신고-검토) | 제보·신고 검토 | 김인안 |
| [WS-13](second-expansion-workstreams.md#8-ws-13-사용자-알림) | 사용자 알림 | 이우람 |

## 5. 사용 시 주의

- 이 단계에서는 클래스, 패키지, 테이블, API URL과 배포 단위를 정하지 않는다. 구현 구조는 [모듈 경계](../06-architecture/module-boundaries.md)와 [패키지 구조](../06-architecture/package-structure.md)에서 확정한다.
- 교차 도메인 협업이 양방향으로 보이면 양쪽에 호출을 추가하지 말고 상위 조합 단위로 끌어올린다. 구현 규칙은 [의존성 규칙](../06-architecture/dependency-rules.md) 3절을 따른다.
- 담당자 배정은 이 문서가 아니라 [roles.md](../03-team/roles.md)와 [ownership.md](../03-team/ownership.md)가 최종 기준이다. 4절 표는 참조용 요약이다.

## 6. 다음 단계

| 다음 문서 | 이 디렉터리에서 이어받는 것 |
|---|---|
| [역할](../03-team/roles.md) · [소유권](../03-team/ownership.md) | Workstream별 최종 책임자 |
| [모듈 경계](../06-architecture/module-boundaries.md) | 도메인 경계의 코드 구조 반영 |
| [MVP 구현 계획](../08-planning/mvp-2day-implementation-plan.md) | Workstream별 Task 분해와 통합 순서 |
