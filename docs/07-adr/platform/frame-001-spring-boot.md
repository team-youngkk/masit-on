---
id: ADR-FRAME-001
title: Spring Boot 애플리케이션 기준
status: Accepted
decision_date: 검토 필요
owners:
  - 맛잇온 Team
related_requirements:
  1: NFR-SECURITY-001
  2: NFR-MAINTAINABILITY-003
  3: NFR-DEPLOYMENT-001
related_documents:
  1: ../../01-requirements/non-functional-requirements.md
  2: ../../02-analysis/mvp-workstreams.md
  3: ../../06-architecture/technology-policy.md
  4: lang-001-java-21-runtime.md
  5: build-001-gradle-groovy.md
  6: ../architecture/arch-001-domain-monolith.md
  7: ../security/auth-001-spring-security-jwt.md
  8: ../../03-team/roles.md
  9: ../../00-overview/scope.md
  10: ../data/data-003-spring-data-jpa.md
  11: ../quality/obs-001-logging-observability.md
  12: ../quality/test-001-automation-strategy.md
  13: ../adr-backlog.md
supersedes: []
superseded_by: null
---

# ADR-FRAME-001 Spring Boot 애플리케이션 기준

## 1. 상태

Accepted

## 2. 결정 요약

백엔드 애플리케이션 프레임워크는 Spring Boot 4.1.0을 사용하고 관리 의존성은 해당 BOM을 따른다.

## 3. 배경

맛잇온 백엔드는 웹 API([#2 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[#2 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[#2 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)), 관리자 인증·등록([#2 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)), 데이터 접근, 테스트를 네 명이 나눠 소유하지만 결국 하나의 애플리케이션으로 통합된다([#8 docs/03-team/roles.md](../../03-team/roles.md), [#2 docs/02-analysis/mvp-workstreams.md](../../02-analysis/mvp-workstreams.md)). 서비스 자체는 계정 없는 서울 지역 맛집 탐색과 관리자 전용 수동 등록만 다루는 비교적 단순한 도메인이며([#9 docs/00-overview/scope.md](../../00-overview/scope.md)), 지도·추천·AI·결제 같은 복잡한 상태나 대규모 트래픽 처리가 MVP 범위에 없다. 즉 이 프로젝트에 필요한 것은 화려한 프레임워크 기능이 아니라, 네 사람이 각자 만든 Controller·Service·Repository·인증 코드가 같은 버전 기준 위에서 마찰 없이 합쳐지는 것이다. Spring Boot의 BOM(Bill of Materials) 체계는 웹·보안·데이터·테스트 스타터의 버전 조합을 한 번에 검증된 세트로 묶어 주므로, 개별 조합의 호환성을 팀이 직접 검증할 필요를 없앤다.

## 4. 결정 문제

Java 21 백엔드의 공통 애플리케이션·의존성 기준을 무엇으로 둘 것인가. 프레임워크 자체의 선택뿐 아니라, Spring Security·Spring Data 등 관리 대상 모듈의 버전을 BOM에 맡길지 팀이 직접 선언할지도 함께 결정해야 한다.

## 5. 고려한 선택지

- **Spring Boot 4.1.0과 BOM**: 웹(Spring MVC), 보안(Spring Security 7.1.0), 데이터(Spring Data JPA), 테스트(Spring Boot Test) 스타터 버전을 BOM이 일괄 관리한다.
- **개별 Spring 모듈 버전 직접 조합**: Spring Security, Spring Data 등을 BOM 없이 각각 명시 버전으로 선언하는 방식. 이 경우 네 사람이 각자 담당 영역에서 의존성을 추가할 때마다 서로 호환되는 조합인지 매번 별도로 확인해야 하는데, 공식 호환성 근거 없이 조합 검증을 직접 하는 것은 [#3 technology-policy.md](../../06-architecture/technology-policy.md) 11장이 금지하는 "공식 호환성 근거 없는 버전 호환 단정"에 해당할 위험이 크다. 관리자 인증([#7 ADR-AUTH-001](../security/auth-001-spring-security-jwt.md))에서 쓰는 Spring Security 버전과 웹 계층 Spring MVC 버전이 어긋나면 통합 시점에야 발견되는 문제이므로, 4인이 독립적으로 개발한다는 전제(범위 경계 규칙 6번)와 맞지 않는다.
- **다른 프레임워크 또는 Snapshot 버전**: 다른 프레임워크로 바꾸는 것은 이미 확정된 Java 21·Gradle 8.14.3·PostgreSQL 17.10 등 나머지 스택 전체의 재검증을 요구하는 별도 결정이라 이 ADR의 범위를 넘어선다. Snapshot은 [#3 technology-policy.md](../../06-architecture/technology-policy.md) 3장이 명시적으로 금지하며, 정식 릴리스가 아닌 버전에 MVP 일정 전체를 거는 것은 4인 캡스톤 팀이 감당할 위험이 아니다.

## 6. 결정

Spring Boot 4.1.0과 해당 BOM을 사용한다. Spring Security 7.1.0 등 관리 대상은 직접 버전을 선언하지 않는다.

## 7. 선택 근거

BOM을 우선 사용하기로 한 것은 "이미 확정된 스펙이니까"라는 순환 논리가 아니라, 네 명이 각자 다른 Workstream에서 동시에 의존성을 추가·수정하는 상황에서 조합 호환성 검증 부담을 팀이 직접 지지 않고 Spring 팀이 검증한 조합에 위임하기 위해서다. 서비스 도메인 자체가 단순한 CRUD·조회 위주(계정 없음, 지도·AI 없음)이므로 Spring Boot의 표준 스타터 구성만으로 요구 기능을 충분히 감당할 수 있고, 프레임워크의 고급·실험적 기능을 별도로 조합해야 할 필요가 없다는 점도 표준 BOM 경로를 그대로 따르는 근거가 된다.

## 8. 트레이드오프

BOM을 그대로 따르면 BOM에 없는 최신 버전이나 BOM이 아직 포함하지 않은 신규 기능을 팀이 원하는 시점에 독립적으로 앞당겨 쓸 수 없다. 예를 들어 특정 하위 라이브러리의 최신 패치가 나와도 BOM이 갱신되기 전까지는 개별적으로 올릴 수 없다. 이 비용은 MVP 범위가 표준 기능 위주여서 최신 기능을 당장 필요로 하는 상황이 거의 없다는 점, 그리고 BOM 예외가 필요하면 10장의 강제 규칙대로 ADR과 통합 테스트로 개별 승인하는 경로가 이미 마련돼 있다는 점으로 상쇄된다. 즉 트레이드오프의 크기는 "당장 못 쓰는 기능이 생길 수 있다" 정도이며, 예외 절차가 있어 완전히 막혀 있지는 않다.

## 9. 적용 범위

모든 백엔드 애플리케이션 설정과 Spring 생태계 의존성에 적용한다. [#2 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[#2 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 네 Workstream 모두, 그리고 관리자 인증([#7 ADR-AUTH-001](../security/auth-001-spring-security-jwt.md))처럼 Spring Security를 직접 다루는 영역에도 동일하게 적용된다.

## 10. 강제 규칙

Starter와 BOM을 우선 사용하고 명시 버전이 필요한 예외는 ADR과 통합 테스트로 승인한다. BOM 관리 대상 라이브러리는 `build.gradle`에 개별 버전을 중복 선언하지 않는다.

## 11. 금지 사항

4.2 Snapshot, 임의 패치 변경, BOM 관리 의존성의 중복 버전 선언을 금지한다. 팀원이 개인 편의로 BOM 밖 버전을 로컬에만 적용하고 공유하지 않는 것도 금지한다.

## 12. 구현 및 운영 영향

공통 설정·보안·데이터·관측 기능은 Boot 4.1.0 수명주기와 호환성에 맞춘다. 관리자 인증([#7 ADR-AUTH-001](../security/auth-001-spring-security-jwt.md)), 데이터 접근([#10 ADR-DATA-003](../data/data-003-spring-data-jpa.md)), 관측([#11 ADR-OBS-001](../quality/obs-001-logging-observability.md)) 등 다른 ADR이 정의하는 세부 구현도 이 프레임워크 기준선을 전제로 설계된다.

## 13. 검증 방법

의존성 해석 결과, BOM 중복 선언과 전체 Spring Boot 통합 테스트를 검사한다. 구체적으로는 `./gradlew dependencies`로 Spring Boot BOM 버전이 정확히 `4.1.0`으로 해석되는지, Spring Security가 BOM이 지정하는 `7.1.0`과 일치하는지 확인하고, `build.gradle`에 BOM 관리 대상 라이브러리의 개별 버전이 중복 선언되어 있지 않은지 검사한다. Spring Boot Test 기반 통합 테스트([#12 ADR-TEST-001](../quality/test-001-automation-strategy.md))가 애플리케이션 컨텍스트 로딩에 성공하면 통과로 본다. 이 검증은 의존성 조합의 일관성 확인이 목적이며, p95 응답 시간 등 성능 기준([#1 RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율))은 별도 성능 테스트([#13 ADR-PERF-001](../adr-backlog.md#adr-perf-001-k6-성능-테스트-체계), 활성화 조건 미충족)에서 다룬다.

## 14. 재검토 조건

지원 종료, 치명적 결함, JDK 변경 또는 승인된 프레임워크 전환 시 재검토한다. Java 메이저 버전이 바뀌거나([#4 ADR-LANG-001](lang-001-java-21-runtime.md)), Spring Boot 4.1.0 라인의 지원이 종료되거나 MVP 운영 중 치명적 결함이 발견되면 [#3 technology-policy.md](../../06-architecture/technology-policy.md) 12장 절차에 따라 재검토한다.

## 15. 관련 문서

- [#3 기술 정책](../../06-architecture/technology-policy.md)
- [#7 관리자 인증 ADR](../security/auth-001-spring-security-jwt.md)
