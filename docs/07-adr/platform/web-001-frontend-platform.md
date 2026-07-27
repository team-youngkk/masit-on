---
id: ADR-WEB-001
title: 프론트엔드 런타임과 프레임워크 기준
status: Accepted
decision_date: 2026-07-27
owners:
  - 양성훈
  - 김인안
related_requirements:
  - NFR-COMPATIBILITY-001
  - NFR-DEPLOYMENT-001
related_documents:
  - ../../00-overview/scope.md
  - ../../03-team/ownership.md
  - ../../04-product/prd/00-product-overview.md
  - ../../05-specs/api/README.md
  - web-003-routing-boundary.md
  - ../../06-architecture/technology-policy.md
  - web-002-data-state.md
  - ../security/auth-001-spring-security-jwt.md
  - ci-001-github-actions-quality-gate.md
  - ../../03-team/roles.md
  - ../../02-analysis/mvp-workstreams.md
  - ../adr-traceability.md
  - lang-001-java-21-runtime.md
  - ../../01-requirements/non-functional-requirements.md
supersedes: []
superseded_by: null
---

# ADR-WEB-001 프론트엔드 런타임과 프레임워크 기준

## 1. 상태

Accepted

## 2. 결정 요약

웹 프론트엔드는 Node.js 24.18.0 LTS, Next.js 16.2.11과 TypeScript 7.0.2를 사용한다.

## 3. 배경

MVP는 웹·모바일 브라우저의 탐색과 관리자 등록 화면을 재현 가능한 빌드로 제공해야 한다.

공개 화면(목록·검색·필터·상세)은 로그인 없이 서울 지역 맛집을 탐색하는 흐름이며([scope.md](../../00-overview/scope.md)), URL로 검색 조건을 공유할 수 있어야 한다([ADR-WEB-002](web-002-data-state.md)). 관리자 화면(맛집·유튜버·영상·방문 관계 등록)은 JWT 인증 뒤에 있는 폼 중심 상호작용이다([ADR-AUTH-001](../security/auth-001-spring-security-jwt.md)). 하나의 프론트엔드 코드베이스가 이 두 성격의 화면을 모두 감당해야 하므로, 런타임·프레임워크·언어를 먼저 고정해야 [ADR-WEB-002](web-002-data-state.md)와 [ADR-CI-001](ci-001-github-actions-quality-gate.md)이 그 위에서 결정을 내릴 수 있다.

프론트엔드 기술 의사결정은 [roles.md](../../03-team/roles.md) 6장에 따라 양성훈·김인안이 공동 담당하지만, 두 사람 모두 각자 High 복잡도 백엔드 Workstream([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))의 최종 책임자이기도 하다. [roles.md](../../03-team/roles.md)는 "개인별 기술 역량과 선호도는 확인되지 않았다"고 명시하므로, 이 문서의 근거는 특정 팀원이 특정 기술에 이미 익숙하다는 가정에 의존하지 않는다. 대신 [scope.md](../../00-overview/scope.md) 6장의 범위 경계 규칙 6번 — "4명의 백엔드 개발자가 MVP 기간 내 서로 독립적으로 개발 가능한 크기와 의존성을 가지는가" — 을 프론트엔드 스택 선택의 실질적 제약으로 사용한다. 초기 월 인프라 예산은 15만 원([adr-traceability.md](../adr-traceability.md))으로, 별도 유료 플랫폼·서비스를 도입할 여지는 크지 않다.

## 4. 결정 문제

프론트엔드의 실행 환경, 애플리케이션 프레임워크와 언어 버전을 무엇으로 고정할 것인가.

## 5. 고려한 선택지

- 확정된 Node.js 24.18.0 · Next.js 16.2.11 · TypeScript 7.0.2 조합을 정확한 버전으로 고정
- 범위 버전(semver range) 또는 패키지 매니저·CI의 최신 버전 자동 추종
- Next.js 외 다른 프레임워크(예: Vite 기반 React SPA, 별도 메타프레임워크) 채택

각 대안이 이 프로젝트에 맞지 않는 이유는 다음과 같다.

- 범위 버전·자동 추종: 4명이 각자 로컬 환경에서 독립적으로 개발한다는 제약([scope.md](../../00-overview/scope.md) 규칙 6)에서, 팀원마다 다른 시점에 다른 패치·마이너 버전을 받으면 "내 로컬에서는 되는데 다른 사람 환경이나 CI에서는 안 된다"는 문제를 개인이 각자 진단해야 한다. 개인별 기술 숙련도가 확인되지 않은 상태([roles.md](../../03-team/roles.md))에서 이런 진단을 팀 전체의 자율에 맡기는 것은 통합 리스크를 키운다. 또한 기술 정책 3장의 고정 버전 정책과도 정면으로 충돌한다.
- 다른 프레임워크: 별도 라우터·빌드 도구·서버 진입점을 조합해야 하는 스택은 그 자체로 팀이 추가로 설계·합의해야 할 결정을 늘린다. 양성훈·김인안은 이미 각자 High 복잡도 Workstream([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))을 최종 책임지고 있어([roles.md](../../03-team/roles.md) 5·7장), 프레임워크 자체를 새로 조립하는 데 드는 시간은 MVP 일정 안에서 감당하기 어렵다. Next.js처럼 라우팅·렌더링 관례가 이미 정해진 프레임워크를 쓰면 이 설계 결정 자체가 줄어든다.

## 6. 결정

Node.js 24.18.0, Next.js 16.2.11, TypeScript 7.0.2를 정확히 고정한다.

## 7. 선택 근거

공개 탐색 화면은 초기 응답에 서버 렌더링이 필요하고([ADR-WEB-002](web-002-data-state.md)의 Server Components `fetch` 결정과 직결), 관리자 등록 화면은 클라이언트 상호작용 중심이다. Next.js는 이 두 렌더링 방식을 하나의 라우팅 체계 안에서 기본 제공하므로, 별도의 라우터·빌드 도구·서버 진입점을 조합하는 설계를 팀이 새로 결정할 필요가 없다. 이는 [scope.md](../../00-overview/scope.md) 범위 경계 규칙 6번(4명이 MVP 기간 내 독립적으로 개발 가능한 크기와 의존성)을 만족하는 데 직접 기여한다 — 프레임워크가 라우팅·렌더링 방식을 이미 정해 주므로, 양성훈([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색))과 김인안([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))이 각자의 화면을 독립적으로 만들면서도 통합 시점에 구조가 어긋날 위험이 줄어든다.

버전을 범위가 아닌 정확한 값으로 고정한 이유는, 4명이 각자 로컬에서 독립적으로 개발한 뒤 CI와 배포 빌드에서 같은 결과가 나와야 하기 때문이다. 팀원별 기술 숙련도가 확인되지 않은 상태([roles.md](../../03-team/roles.md))에서는, 환경 차이로 생기는 문제를 각자가 스스로 진단할 수 있다고 가정하기 어렵다. 정확한 버전 고정은 이런 환경 차이를 원천적으로 줄여, 확인되지 않은 역량 편차가 그대로 통합 리스크로 번지는 것을 막는다. TypeScript를 사용하는 이유도 같은 맥락이다 — `05-specs`의 API 계약을 어떤 담당자가 구현하든 타입 불일치를 컴파일 시점에 드러내어, 팀 전체의 확인되지 않은 숙련도를 보완하는 안전장치로 삼는다.

## 8. 트레이드오프

정확한 버전 고정은 재현성을 얻는 대신, 보안 패치나 버그 수정이 나와도 팀이 명시적으로 버전을 올리기 전에는 적용되지 않는다는 비용을 받아들인다. Next.js 16·TypeScript 7은 상대적으로 최근에 나온 메이저 버전이므로, 팀이 실제로 부딪혀 본 적 없는 경계 사례(예: App Router의 특정 캐싱·렌더링 동작)를 만났을 때 내부적으로 해결 경험이 쌓여 있지 않을 수 있다 — [roles.md](../../03-team/roles.md)가 명시하듯 개인별 역량이 확인되지 않았기 때문에 이 리스크의 크기를 미리 가늠하기 어렵다. 이를 줄이기 위해 프론트엔드가 다루는 화면 범위 자체를 [scope.md](../../00-overview/scope.md)가 정한 좁은 범위(목록·검색·필터·상세·관리자 등록 폼)로 제한하고, 막히는 문제는 양성훈·김인안 공동 담당자를 거쳐 팀 전체에 공유한다([roles.md](../../03-team/roles.md) 10장). 버전을 올리는 결정도 매번 팀 합의를 거쳐야 하므로(기술 정책 12장 기술 변경 절차), 그 검토·회귀 테스트에 드는 시간이 이미 빠듯한 MVP 일정에 추가 부담으로 작용할 수 있다.

## 9. 적용 범위

[WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)(맛집 탐색)·[WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)(맛집 상세)의 공개 사용자 화면과 [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)(관리자 데이터 등록)의 관리자 화면, 그리고 이들의 빌드와 CI 파이프라인에 적용한다. 백엔드 Spring Boot 애플리케이션의 런타임(JDK, [ADR-LANG-001](lang-001-java-21-runtime.md))에는 적용하지 않는다.

## 10. 강제 규칙

런타임·패키지 버전과 `package-lock.json` 등 잠금 파일을 저장소에 고정하고, Server Components와 Client Components(`"use client"`) 경계를 파일 단위로 명시한다. 관리자 화면은 공개 화면과 같은 코드베이스를 쓰되, [ADR-WEB-003](web-003-routing-boundary.md)의 `/admin/login`, 기능별 `/admin/**` 경로와 인증 복구 흐름으로 구분한다.

## 11. 금지 사항

범위 버전, 다른 패치·Preview, Node `latest`와 잠금 파일 없는 설치를 금지한다. 이는 기술 정책 3장의 고정 버전 정책과 동일하다.

## 12. 구현 및 운영 영향

개발 환경, CI([ADR-CI-001](ci-001-github-actions-quality-gate.md))와 컨테이너 빌드가 동일한 Node 24.18.0 기준을 사용해야 하며, 이는 GitHub Actions에서 검증된다. PC Chrome·Edge, Android Chrome, iPhone Safari의 테스트 시점 최신 및 직전 안정 버전을 지원하고 360px, 390px, 768px, 1280px, 1440px 화면 폭을 검증한다.

## 13. 검증 방법

CI에서 `node -v`, `npm ls next typescript` 결과가 각각 정확히 24.18.0, 16.2.11, 7.0.2인지 확인하고 불일치 시 빌드를 실패시킨다. `package-lock.json`이 커밋되어 있고 CI가 `npm ci`(또는 동등한 고정 설치)를 사용하는지, 의존성 선언에 범위 버전 문자열이 없는지 검사한다. 컨테이너 이미지 태그가 고정되어 있는지 확인한다. 이 검증은 [ADR-CI-001](ci-001-github-actions-quality-gate.md)의 품질 게이트 실행 결과로 판단하며, 별도의 성능 측정(p95 응답 시간 등)은 [ADR-WEB-002](web-002-data-state.md)의 데이터 패칭 패턴에서 검증한다.

## 14. 재검토 조건

런타임 지원 종료, 승인된 브라우저 범위 변경 또는 사용자 승인된 프레임워크 전환 시 재검토한다.

## 15. 관련 문서

- [MVP 범위](../../00-overview/scope.md)
- [기술 정책](../../06-architecture/technology-policy.md)
