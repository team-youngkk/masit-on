---
id: ADR-BUILD-001
title: Gradle과 Groovy DSL 빌드 체계
status: Accepted
decision_date: 검토 필요
owners:
  - 맛잇온 Team
related_requirements:
  1: NFR-DEPLOYMENT-001
  2: NFR-TEST-003
related_documents:
  1: ../../01-requirements/non-functional-requirements.md
  2: ../../06-architecture/technology-policy.md
  3: lang-001-java-21-runtime.md
  4: frame-001-spring-boot.md
  5: ci-001-github-actions-quality-gate.md
  6: ../../02-analysis/mvp-workstreams.md
  7: ../../03-team/roles.md
  8: ../architecture/arch-001-domain-monolith.md
supersedes: []
superseded_by: null
---

# ADR-BUILD-001 Gradle과 Groovy DSL 빌드 체계

## 1. 상태

Accepted

## 2. 결정 요약

백엔드 빌드는 Gradle 8.14.3 Wrapper와 Groovy DSL을 사용한다.

## 3. 배경

4명(이우람 [#6 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), 양성훈 [#6 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), 박진영 [#6 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), 김인안 [#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))이 각자 독립된 Workstream을 소유하며 같은 저장소의 같은 `build.gradle`을 함께 수정한다([#7 docs/03-team/roles.md](../../03-team/roles.md)). 이 문서는 개인별 기술 역량과 선호도가 확인되지 않았다고 명시하므로, 빌드 스크립트 문법 자체가 팀원 전원에게 낯설지 않아야 한다. Groovy DSL은 오랫동안 Gradle 공식 예제와 대다수 스타터 문서에서 기본으로 쓰여 온 문법이라 익히는 데 필요한 사전 지식이 적고, Kotlin DSL보다 컴파일 단계 자체가 없어 스크립트 수정 후 반영 확인 절차가 단순하다. 이는 "팀이 이미 Groovy를 잘 안다"는 주장이 아니라, 역량이 불확실한 4인이 같은 파일을 자주 함께 건드려야 하는 상황에서 어떤 DSL이 진입장벽이 낮은지에 대한 판단이다.

## 4. 결정 문제

Java 21·Spring Boot 4.1.0 프로젝트의 빌드 도구와 DSL을 무엇으로 고정할 것인가. 빌드 도구(Gradle vs Maven), 스크립트 언어(Groovy DSL vs Kotlin DSL), 버전 갱신 방식(Wrapper 고정 vs 자동 최신화)을 함께 결정해야 한다.

## 5. 고려한 선택지

- **Gradle 8.14.3 + Groovy DSL**: Wrapper로 버전을 고정하고 모든 환경이 저장소에 커밋된 동일 스크립트를 사용한다.
- **Gradle Kotlin DSL**: 타입 안전성과 IDE 자동완성이 강점이지만, 스크립트가 Kotlin 컴파일러를 거치므로 빌드 스크립트 수정 시 첫 빌드가 느려지고, Groovy DSL 대비 학습 자료·예제가 상대적으로 적다. 4명이 각자 다른 Workstream 작업 중 공용 `build.gradle`에 의존성을 추가해야 하는 빈도가 낮지 않은데, 역량이 확인되지 않은 상태에서 DSL 문법 자체의 학습 비용을 추가로 지우는 것은 MVP 일정(범위 경계 규칙 6번, "4명의 백엔드 개발자가 MVP 기간 내 서로 독립적으로 개발 가능한 크기와 의존성을 가지는가") 관점에서 불리하다.
- **Maven 또는 Gradle 자동 최신화**: Maven은 Spring Boot 생태계에서도 널리 쓰이지만, 프로젝트가 이미 Gradle Wrapper 기반으로 시작된 뒤 도구 자체를 바꾸는 것은 별도 마이그레이션 비용이며 이 ADR이 다루는 결정 범위를 벗어난다. Gradle 버전 자동 최신화(Wrapper 버전을 고정하지 않고 실행 시점 최신판을 받는 방식)는 [#2 technology-policy.md](../../06-architecture/technology-policy.md) 3장이 이미 전면 금지하는 범위 버전·자동 최신화 정책과 정면으로 충돌하므로 고려하지 않는다.

## 6. 결정

Gradle 8.14.3 Wrapper와 Groovy DSL을 사용한다. Wrapper 스크립트(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)는 저장소에 커밋하여 팀원과 CI가 별도 설치 없이 동일 버전을 실행하도록 한다.

## 7. 선택 근거

Wrapper는 "Gradle을 설치해 두었는지, 어떤 버전인지"를 팀원 개개인의 관리 몫으로 남기지 않고 저장소가 강제하므로, 역량 차가 확인되지 않은 4인 팀에서 빌드 도구 버전 불일치로 인한 실패를 원천적으로 줄인다. Groovy DSL은 앞서 3장에서 설명한 진입장벽 이유 외에도, Spring Boot 4.1.0 BOM([#4 ADR-FRAME-001](frame-001-spring-boot.md))을 그대로 참조하는 표준적인 `plugins { }` / `dependencies { }` 블록 구성이 Groovy 기준으로 작성된 예제가 많아, 의존성 추가·BOM 정렬 작업을 각자 독립적으로 진행해야 하는 4개 Workstream 모두가 동일한 패턴을 참고하기 쉽다.

## 8. 트레이드오프

Groovy DSL은 동적 타입이라 오탈자나 잘못된 설정 키를 컴파일 시점이 아닌 빌드 실행 시점에야 발견하는 경우가 있고, IDE 자동완성도 Kotlin DSL만큼 정밀하지 않다. 4명이 같은 파일을 병행 수정하는 구조에서 이는 실제로 겪을 수 있는 비용이지만, 빌드 스크립트 변경 자체가 매 스프린트마다 크게 늘어나는 부분은 아니고(의존성 추가·플러그인 선언 등 저빈도 변경), 오류가 나더라도 빌드 실패로 즉시 드러나 리뷰 단계에서 걸러진다. Wrapper 버전 고정의 트레이드오프는 Gradle 9 계열의 신규 기능(예: 향상된 설정 캐시, 새 태스크 API)을 곧바로 쓸 수 없다는 것인데, MVP 범위에서 이런 고급 기능이 당장 필요하지 않으므로 지금 시점에는 감수할 만한 손실이다.

## 9. 적용 범위

모든 백엔드 모듈, 플러그인 선언, 로컬 빌드와 CI에 적용한다. 현재 단일 모듈 구조([#8 ADR-ARCH-001](../architecture/arch-001-domain-monolith.md))이므로 적용 대상은 루트 `build.gradle` 한 곳과 그 안의 의존성·플러그인 선언 전체다.

## 10. 강제 규칙

Wrapper 파일을 저장소에서 관리하고 의존성은 정확한 버전 또는 승인된 BOM으로 해석한다. Spring 생태계 의존성은 Spring Boot 4.1.0 BOM([#4 ADR-FRAME-001](frame-001-spring-boot.md))을 우선 사용하고, BOM 밖 라이브러리만 개별 버전을 명시한다.

## 11. 금지 사항

시스템 Gradle 의존, Kotlin DSL 임의 전환, 동적 버전과 자동 Wrapper 업그레이드를 금지한다. 개인 편의를 이유로 일부 팀원만 로컬에서 Kotlin DSL 스크립트를 병행 작성하거나, 시스템에 설치된 Gradle로 Wrapper를 우회해 실행하는 것도 금지한다.

## 12. 구현 및 운영 영향

CI는 Wrapper로 빌드하며 캐시 키에 Wrapper·잠금 파일 변경을 반영해야 한다. GitHub Actions 워크플로는 `./gradlew` 실행 경로만 사용하고, 의존성 캐시 키에 `gradle/wrapper/gradle-wrapper.properties`와 빌드 스크립트 해시를 포함해 버전이 바뀌면 캐시가 무효화되도록 구성한다.

## 13. 검증 방법

깨끗한 환경에서 Wrapper 버전과 Groovy 스크립트를 확인하고 빌드·테스트 산출물을 재현한다. `./gradlew --version` 출력의 Gradle 버전이 정확히 `8.14.3`인지 확인하고, 저장소에 Kotlin DSL 파일(`*.gradle.kts`)이 존재하지 않는지 검사한다. 클린 체크아웃 후 `./gradlew clean build`가 성공하고, CI와 로컬에서 같은 커밋 기준으로 동일한 테스트 결과를 재현하면 통과로 본다. Wrapper 버전이 다르거나 Kotlin DSL 파일이 섞여 있으면 실패로 처리한다.

## 14. 재검토 조건

Spring Boot 요구사항 변경, 빌드 결함 또는 팀이 DSL 전환 비용을 승인할 때 재검토한다. 예를 들어 Gradle 8.14.3에서 재현 불가능한 빌드 결함이 발견되거나, Spring Boot 후속 버전이 특정 Gradle 최소 버전을 요구하게 되면 [#2 technology-policy.md](../../06-architecture/technology-policy.md) 12장 절차에 따라 재검토한다.

## 15. 관련 문서

- [#2 기술 정책](../../06-architecture/technology-policy.md)
- [#1 NFR](../../01-requirements/non-functional-requirements.md)
