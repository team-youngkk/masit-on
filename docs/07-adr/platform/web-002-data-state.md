---
id: ADR-WEB-002
title: 프론트엔드 데이터와 상태 책임 분리
status: Accepted
decision_date: 2026-07-27
owners:
  - 양성훈
  - 김인안
related_requirements:
  - FR-AUTH-004
  - NFR-MAINTAINABILITY-001
  - NFR-COMPATIBILITY-003
related_documents:
  - ../../04-product/prd/discovery/restaurant-discovery.md
  - ../../05-specs/api/discovery/restaurant-discovery-api.md
  - ../../05-specs/api/README.md
  - web-001-frontend-platform.md
  - web-006-unified-login-rbac-route.md
  - web-006-unified-login-rbac-route.md
  - ../security/auth-007-unified-account-rbac-session.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../00-overview/scope.md
  - ../../01-requirements/non-functional-requirements.md
  - ../adr-traceability.md
  - ../../03-team/roles.md
supersedes: []
superseded_by: null
---

# ADR-WEB-002 프론트엔드 데이터와 상태 책임 분리

## 1. 상태

Accepted

## 2. 결정 요약

초기 서버 데이터는 Server Components `fetch`, 상호작용 이후 서버 상태와 현재 계정 세션은 TanStack Query, 검색 조건은 URL Query Parameter, 화면 지역 상태는 React `useState`로 관리한다.

## 3. 배경

탐색 URL 재현성과 초기 렌더링 성능을 확보하면서 불필요한 전역 상태를 피해야 한다.

[WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색은 이름 검색과 지역·유튜버·카테고리 필터를 AND 조합으로 지원하며([scope.md](../../00-overview/scope.md) 3.1), 이 조건들이 새로고침·뒤로가기·링크 공유 후에도 같은 결과를 재현해야 한다([scope.md](../../00-overview/scope.md) MVP 완료 기준: "사용자가 이름 검색과 각 필터를 함께 적용할 수 있고... 모든 조건을 만족하는 결과만 조회할 수 있다"). 이미 결정된 성능 기준([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율))은 일반 조회 p95 500ms, 검색·필터 조회 p95 800ms, 오류율 1% 미만이며, 일반 목록의 기본 페이지 크기는 20(10/20/50 선택 가능), WS-01 맛집 탐색의 `GET /api/restaurants`와 자연어 검색의 `POST /api/restaurants/natural-language-search`는 21(10/20/21/50 선택 가능, 기존 `size=20` 호출 호환)이다([RV-NFR-003](../../01-requirements/non-functional-requirements.md#rv-nfr-003-페이지-크기)). 이 ADR은 이 기준을 만족하는 데이터 패칭 구조를 정하는 문제이며, [ADR-WEB-001](web-001-frontend-platform.md)이 고정한 Next.js App Router 위에서 서버 데이터·검색 조건·화면 상태의 책임을 구체적으로 나눈다.

## 4. 결정 문제

성격이 다른 서버 데이터, 검색 조건과 화면 지역 상태의 책임을 어디에 둘 것인가.

## 5. 고려한 선택지

- 상태 성격별 도구 분리: Server Components `fetch`(초기 서버 데이터) + TanStack Query(상호작용 이후 재조회·캐시) + URL Query Parameter(검색 조건) + `useState`(화면 지역 상태)
- 모든 상태를 하나의 전역 저장소(예: Redux, Zustand)에 통합
- 모든 조회를 클라이언트 요청으로만 처리(Server Components `fetch` 없이 초기 로드도 클라이언트에서 수행)

각 대안이 이 프로젝트에 맞지 않는 이유는 다음과 같다.

- 전역 저장소 통합: 검색 조건까지 저장소 상태로 관리하면 URL과 저장소 두 곳의 값을 항상 동기화해야 하고, 이 동기화 로직 자체가 새로고침·공유 URL 재현이라는 원래 목적을 저해한다. 별도 라이브러리·러닝커브를 추가하는 것도 4명이 MVP 기간 내 독립적으로 개발해야 하는 제약([scope.md](../../00-overview/scope.md) 규칙 6)에 부담이 된다.
- 클라이언트 전용 조회: 초기 로드까지 클라이언트에서 요청하면 첫 화면에 빈 상태를 먼저 보여준 뒤 데이터를 채우게 되어, 이미 결정된 p95 500~800ms 목표([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율))를 첫 조회부터 불리하게 만든다. 또한 이는 [ADR-WEB-001](web-001-frontend-platform.md)에서 서버 렌더링 프레임워크를 고정한 이유 자체를 무력화한다.

## 6. 결정

상태 성격별 책임을 분리하고 검색·필터는 공유 가능한 URL로 표현한다.

## 7. 선택 근거

검색 조건을 URL Query Parameter로 표현하는 이유는 [scope.md](../../00-overview/scope.md)가 요구하는 "이름 검색과 각 필터를 함께 적용"한 결과를 새로고침·공유 링크·뒤로가기에서도 그대로 재현해야 하기 때문이다(MVP 완료 기준). 검색·필터 상태를 화면 지역 상태(`useState`)나 전역 저장소에만 두면, 사용자가 URL을 복사해 다른 사람에게 보내거나 브라우저 뒤로가기를 눌렀을 때 같은 목록을 재현할 수 없다. 이는 별도 서버 세션이나 저장소 없이 무료로 얻을 수 있는 재현성이므로, 15만 원 예산([adr-traceability.md](../adr-traceability.md)) 안에서 부가 인프라 없이 요구사항을 만족하는 방법이기도 하다.

초기 서버 데이터에 Server Components `fetch`를 쓰는 이유는 목록·검색·필터·상세가 첫 응답에 데이터를 포함해야 p95 500~800ms 목표([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율))를 맞추기 유리하기 때문이다. 상호작용 이후(페이지 이동, 필터 변경, 관리자 등록 폼 제출 후 재조회 등) 발생하는 재조회·캐시·로딩 상태 관리는 TanStack Query로 위임해, 각 화면이 캐시 무효화·재시도 로직을 매번 직접 구현하지 않도록 한다. 화면 지역 상태(`useState`)는 검색 조건이나 서버 데이터가 아닌 것 — 예: 필터 드롭다운이 열려 있는지, 관리자 등록 폼의 입력값 임시 상태 — 에만 쓴다. 이런 상태는 URL이나 서버 캐시에 실릴 필요가 없고, 화면을 벗어나면 사라져도 되는 값이기 때문이다.

## 8. 트레이드오프

상태 성격별로 책임을 나누면 각 상태가 있어야 할 자리는 명확해지지만, 그 대가로 "이 값이 URL에도 있고 TanStack Query 캐시 키에도 들어가야 하는가"를 매 기능마다 팀이 판단해야 한다. 상태 종류가 하나(예: 전역 저장소)였다면 이런 경계 판단 자체가 필요 없었을 것이다. 개인별 기술 숙련도가 확인되지 않은 상황([roles.md](../../03-team/roles.md))에서 이 경계를 잘못 나누면(예: URL 검색 조건을 지역 state로 복제) 눈에 잘 띄지 않는 버그(새로고침 시 필터가 사라짐 등)로 이어질 수 있다. 이 리스크는 11장의 금지 사항(URL 검색 상태의 지역 state 복제 금지)과 10장의 강제 규칙(캐시 키에 모든 검색 조건 포함)으로 통제하고, 새로고침·공유 URL·뒤로가기 시나리오를 통합 테스트에서 검증해 조기에 드러낸다.

TanStack Query의 정확한 버전은 `@tanstack/react-query` `5.101.4`로 확정한다. 이 버전은 프로젝트의 React `19.2.0`을 peer dependency(`^18 || ^19`)로 지원한다. 재현 가능한 설치를 위해 `package.json`에는 범위 연산자 없이 정확한 버전으로 고정한다.

## 9. 적용 범위

공개 탐색([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색))·상세([WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회))와 관리자 등록([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)) 웹 화면의 데이터·상태 관리에 적용한다. 목록·검색·필터 결과와 상세 정보는 서버 데이터 경로를, 관리자 등록 폼의 입력 진행 상태는 화면 지역 상태 경로를 따른다.

## 10. 강제 규칙

검색 조건은 `05-specs`의 API 계약과 동일한 URL 의미(파라미터 이름·값)를 유지한다. 화면 URL `/restaurants`의 Query Parameter를 백엔드 `/api/restaurants` 호출에 전달하고, TanStack Query 캐시 키에는 페이지 번호·페이지 크기(맛집 탐색은 10/20/21/50, 그 밖의 일반 목록은 10/20/50)·검색어·지역·유튜버·카테고리 조건을 모두 포함해 조건이 다르면 다른 캐시로 취급한다.

현재 계정의 비밀이 아닌 식별 정보와 역할은 정확히 `['auth', 'session']` Query Key에 저장한다. 로그인·재발급 성공 응답의 `role`을 즉시 반영한 뒤 `GET /api/me`를 다시 조회해 `id`, `email`, `role`을 완성한다. Access Token 원문은 메모리 전용 인증 모듈에만 두고 TanStack Query 캐시·Local Storage·Session Storage에 저장하지 않는다. 로그아웃 처리, 재발급까지 실패한 확정 `401`, 서버가 알린 역할 변경 때는 Access Token과 세션 Query를 포함한 인증 범위 Query 캐시를 함께 제거한다. `403`은 재발급이나 재로그인 반복을 시작하지 않는다. 상세 흐름과 경로 권한은 [ADR-WEB-006](web-006-unified-login-rbac-route.md)을 따른다.

## 11. 금지 사항

URL 검색 상태를 화면 지역 state로 중복 저장하는 것, 서버 데이터를 근거 없이 전역 저장소에 복제하는 것, 역할 문자열이나 경로 접두사만으로 권한을 추정하는 것, Access Token을 Query 캐시나 영구 브라우저 저장소에 넣는 것, 확정된 `@tanstack/react-query` `5.101.4` 이외의 버전을 임의로 설치하는 것을 금지한다.

## 12. 구현 및 운영 영향

Server Components와 Client Components 사이의 Hydration 경계, 캐시 무효화 시점(예: 관리자 등록 직후 목록 재조회), 브라우저 뒤로가기 시나리오를 테스트해야 한다. 정상 부하 50명·20 RPS와 초기 기준 데이터 규모를 사용해 서버 부하를 검증하되, 캐시는 실제 반복 조회율과 병목이 확인되기 전 선제 도입하지 않는다.

## 13. 검증 방법

새로고침·공유 URL·브라우저 뒤로가기 후 동일한 목록·필터 결과가 재현되는지 확인한다. 초기 렌더링(Server Components `fetch`)이 이미 결정된 p95 목표 — 일반 조회 500ms, 검색·필터 조회 800ms, 오류율 1% 미만([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율)) — 을 만족하는지, 일반 목록은 페이지 크기 10/20/50, 맛집 탐색은 10/20/21/50 선택이 정상 동작하는지 확인한다. 상호작용 재조회(필터 변경, 관리자 등록 후 반영)가 TanStack Query 캐시 무효화를 거쳐 최신 데이터를 반영하는지 통합 테스트로 검증한다. 로그인·재발급·로그아웃·역할 변경에서 `['auth', 'session']`, 메모리 Token과 인증 범위 캐시가 함께 전이하는지, 공개 조회 캐시는 불필요하게 제거되지 않는지 검증한다.

## 14. 재검토 조건

오프라인 지원, 복잡한 교차 화면 상태, 또는 동시 사용자·초기 데이터 규모([RV-NFR-001](../../01-requirements/non-functional-requirements.md#rv-nfr-001-목표-동시-사용자-수)·[RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모))가 확정되어 현재 분리로는 캐시·성능 요구를 충족하기 어렵다고 판단될 때 재검토한다.

## 15. 관련 문서

- [맛집 탐색 PRD](../../04-product/prd/discovery/restaurant-discovery.md)
- [맛집 탐색 API](../../05-specs/api/discovery/restaurant-discovery-api.md)
