---
id: ADR-LANG-001
title: Java 21 런타임 기준
status: Accepted
decision_date: 2026-07-27
owners:
  - 맛잇온 Team
related_requirements:
  - NFR-DEPLOYMENT-001
  - NFR-MAINTAINABILITY-003
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../06-architecture/technology-policy.md
  - build-001-gradle-groovy.md
  - frame-001-spring-boot.md
  - runtime-001-docker.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../03-team/roles.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-LANG-001 Java 21 런타임 기준

## 1. 상태

Accepted

## 2. 결정 요약

백엔드의 컴파일·테스트·CI·배포 런타임을 JDK 21.0.12 LTS로 고정한다.

## 3. 배경

맛잇온 백엔드는 이우람([WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)), 양성훈([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)), 박진영([WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)), 김인안([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)) 네 명이 각자 하나의 Workstream을 처음부터 끝까지 독립적으로 소유·개발한다([docs/03-team/roles.md](../../03-team/roles.md)). 같은 문서는 "개인별 기술 역량과 선호도는 확인되지 않았다"고 명시한다. 즉 JVM 마이너 차이가 만드는 미묘한 동작 차이(가비지 컬렉터 기본값, deprecation 경고, 직렬화·리플렉션 접근 제어 변화 등)를 팀원 각자가 스스로 진단할 수 있는 역량을 전제로 설계를 세울 수 없다. 네 Workstream이 병렬로 만든 코드는 결국 하나의 배포 산출물로 통합되므로, 로컬 개발·CI·컨테이너 실행 이미지([ADR-RUNTIME-001](runtime-001-docker.md))가 모두 같은 JVM 패치 버전을 쓰지 않으면 통합 시점에 원인 파악이 오래 걸리는 "내 환경에서는 되는데" 유형의 실패가 발생하기 쉽다. MVP 일정이 고정된 팀 프로젝트에서는 이런 진단에 쓸 여유 시간이 크지 않다.

## 4. 결정 문제

백엔드가 공통으로 사용할 Java 릴리스와 패치 버전을 무엇으로 고정할 것인가. 메이저 버전뿐 아니라 패치 버전까지 명시적으로 고정할지, 아니면 메이저만 고정하고 패치 갱신은 각 환경(로컬, CI, 컨테이너)이 개별적으로 따라가도록 둘지를 함께 결정해야 한다.

## 5. 고려한 선택지

- **JDK 21.0.12 LTS 고정**: 메이저와 패치를 모두 명시하고 모든 환경이 동일 값을 사용한다.
- **Java 21 범위 또는 최신 패치 자동 추종**: 메이저만 21로 고정하고 패치는 각 개발자 로컬, CI 러너, 컨테이너 베이스 이미지가 그 시점의 최신값을 각자 받는다. 이 방식은 팀원 4명이 서로 다른 시점에 로컬 JDK를 설치·갱신할 경우 네 환경의 패치 버전이 쉽게 어긋난다. 개인별 JVM 트러블슈팅 역량이 확인되지 않은 상태에서 "왜 내 컴퓨터에서만 테스트가 깨지는지" 원인을 패치 버전 차이까지 추적하는 것은 추가 비용이며, [technology-policy.md](../../06-architecture/technology-policy.md) 3장이 이미 `latest`·범위 버전 전반을 금지하고 있어 Java에서만 예외를 두는 것도 정책 일관성을 깬다.
- **다른 메이저 JDK(예: 17 LTS로 유지하거나 21보다 최신인 비-LTS로 전환)**: Spring Boot 4.1.0과 Spring Security 7.1.0 BOM([ADR-FRAME-001](frame-001-spring-boot.md))이 이미 확정 기준으로 고정되어 있어, Java 메이저를 바꾸면 이미 합의한 프레임워크 기준선과 별개로 재검증이 필요한 축이 하나 더 생긴다. 공식 호환성 근거 없이 특정 메이저가 더 낫다고 단정할 수 없으므로(기술 정책 11장), 별도 승인 없이 메이저를 바꾸는 옵션은 채택하지 않는다.

## 6. 결정

JDK 21.0.12 LTS를 사용하고 자동 추종과 다른 메이저 전환을 허용하지 않는다. 패치 버전까지 포함한 정확한 문자열(`21.0.12`)을 개발·CI·이미지 전 구간에서 동일하게 요구한다.

## 7. 선택 근거

네 명이 서로 다른 Workstream을 병행 개발하면서도 개인별 기술 역량 차는 확인되지 않았다는 전제([roles.md](../../03-team/roles.md)) 아래에서는, 팀원 각자가 버전 관리 판단을 내리게 하기보다 하나의 정확한 값을 문서로 못박아 두는 편이 실패 원인을 줄인다. 패치까지 고정하면 "내 JDK는 21이니 맞다"는 식의 착각된 일치를 방지하고, 로컬·CI·컨테이너 세 표면이 항상 동일한 값을 갖는지 기계적으로 대조할 수 있다. LTS 트랙을 선택한 것은 MVP 기간 중 예정에 없던 필수 업그레이드 창을 강제로 만들지 않기 위해서다. 4인 캡스톤 규모 팀은 별도 보안·인프라 전담 인력이 없고 예산도 초기 월 15만 원 수준([adr-traceability.md](../adr-traceability.md))으로 크지 않아, 비-LTS 트랙처럼 짧은 주기로 강제 업그레이드가 발생하면 그 대응에 쓸 여력을 확보하기 어렵다.

## 8. 트레이드오프

패치 버전을 고정하면 보안 패치나 JVM 버그 수정이 나와도 팀이 명시적으로 승인하기 전까지는 자동으로 반영되지 않는다. 이 비용은 팀에 전담 보안 담당이 없는 4인 구조에서는 특히 커서, 아무도 담당하지 않으면 패치가 방치될 위험이 있다. 이를 줄이기 위해 인프라 기술 의사결정을 이우람이 담당하도록 역할을 정해 두었고([roles.md](../../03-team/roles.md) 4장), 본 ADR 14장의 재검토 조건(지원 종료·중대한 보안 문제 등)이 발생하면 패치 버전을 올리는 절차를 [technology-policy.md](../../06-architecture/technology-policy.md) 12장의 기술 변경 절차로 명시해 두었다. LTS 트랙은 일반적으로 비-LTS보다 지원 기간이 길기 때문에, 이번 결정으로 발생하는 실제 위험은 "패치 반영이 늦어질 수 있다"는 정도이며 "지원이 끊겨 방치된다"는 수준까지는 아니다.

## 9. 적용 범위

백엔드 소스, Gradle toolchain, 테스트, CI와 런타임 이미지에 적용한다. 로컬 개발 환경, GitHub Actions CI 러너, Docker 런타임 이미지([ADR-RUNTIME-001](runtime-001-docker.md))의 베이스 이미지 태그 세 곳이 모두 이 범위에 포함된다.

## 10. 강제 규칙

모든 환경에서 21.0.12를 명시하고 toolchain과 이미지 기준을 함께 검증한다. Gradle `build.gradle`의 toolchain 블록에 정확한 버전을 선언하고, Dockerfile 베이스 이미지 태그도 21.0.12에 대응하는 명시 태그를 사용한다.

## 11. 금지 사항

`latest`, 21 범위 태그, 다른 패치·메이저, RC·EA JDK를 임의 사용하지 않는다. 팀원 개인 판단으로 로컬 JDK만 최신화하고 CI·이미지는 그대로 두는 부분 갱신도 금지한다.

## 12. 구현 및 운영 영향

개발 JDK, Gradle toolchain, CI와 컨테이너 베이스 이미지가 같은 패치 기준을 가져야 한다. 새 팀원이 합류하거나 개발 환경을 재설정할 때 문서화된 정확한 버전 문자열을 그대로 설치하는 것이 유일한 절차이며, 임의로 "21 계열 아무거나"를 설치하지 않는다.

## 13. 검증 방법

로컬·CI·이미지의 `java -version`과 Gradle toolchain 결과를 대조하고 깨끗한 환경에서 빌드한다. 구체적으로는 각 팀원 로컬, GitHub Actions 러너, Dockerfile 베이스 이미지에서 `java -version` 출력이 정확히 `21.0.12`인지, `./gradlew -q javaToolchains` 결과가 같은 값을 가리키는지 확인한다. 넷 중 하나라도 다른 패치·메이저·범위 태그를 보이면 실패로 간주한다. 추가로 클린 체크아웃 상태에서 `./gradlew clean build`가 시스템 기본 JDK로 폴백하지 않고 선언된 toolchain으로만 성공하는지 확인한다. 이 검증은 재현성([NFR-DEPLOYMENT-001](../../01-requirements/non-functional-requirements.md#nfr-deployment-001-재현-가능한-빌드와-환경-분리)) 확인이 목적이며, p95 응답 시간 등 성능 기준([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율))과는 직접 관련이 없다.

## 14. 재검토 조건

지원 종료, 중대한 보안 문제, Spring Boot 기준 변경 또는 사용자 승인된 런타임 전환 시 재검토한다. 재검토가 필요해지면 [technology-policy.md](../../06-architecture/technology-policy.md) 12장의 기술 변경 절차에 따라 새 패치·메이저 버전과 근거를 명시한 뒤 팀 합의를 거쳐 이 ADR을 갱신하거나 대체한다.

## 15. 관련 문서

- [기술 정책](../../06-architecture/technology-policy.md)
- [NFR](../../01-requirements/non-functional-requirements.md)
