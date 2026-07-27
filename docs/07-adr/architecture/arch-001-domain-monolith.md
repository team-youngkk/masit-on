---
id: ADR-ARCH-001
title: 단일 모듈 도메인 중심 모놀리스
status: Accepted
decision_date: 검토 필요
owners:
  - 이우람
related_requirements:
  - NFR-MAINTAINABILITY-001
  - NFR-MAINTAINABILITY-002
  - NFR-MAINTAINABILITY-003
related_documents:
  - ../../02-analysis/domain-boundaries.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../03-team/ownership.md
  - ../../05-specs/data/data-model.md
  - ../../06-architecture/technology-policy.md
  - ../../03-team/roles.md
  - ../../00-overview/scope.md
  - ../adr-traceability.md
  - ../security/auth-001-spring-security-jwt.md
  - ../../01-requirements/non-functional-requirements.md
supersedes: []
superseded_by: null
---

# ADR-ARCH-001 단일 모듈 도메인 중심 모놀리스

## 1. 상태

Accepted

## 2. 결정 요약

1차 MVP는 하나의 배포 가능한 백엔드 모듈 안에서 도메인 중심 패키지와 명시적 계층을 유지하는 모놀리스로 구현한다.

## 3. 배경

네 명의 백엔드 개발자(양성훈-[WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색, 박진영-[WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세, 이우람-[WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색, 김인안-[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 등록)가 하나의 MVP 기간 안에서 각자 담당 Workstream을 요구사항부터 API 계약, 데이터 모델, 구현, 테스트까지 끝까지 책임진다([docs/03-team/roles.md](../../03-team/roles.md)). [roles.md](../../03-team/roles.md)는 역할을 Controller·Service·Repository와 같은 기술 계층이 아니라 기능·도메인 소유권 기준으로 나눈다고 명시하며, 개인별 기술 역량과 선호도는 아직 확인되지 않았다고 밝히고 있어 특정 인프라·서비스 경계를 개인의 역량에 맞춰 설계할 근거가 없다.

[scope.md](../../00-overview/scope.md) 6번 범위 경계 규칙은 새 기능이 "4명의 백엔드 개발자가 MVP 기간 내 서로 독립적으로 개발 가능한 크기와 의존성을 가지는가"를 요구하며, 이는 코드 수준의 소유권 경계(누가 어떤 패키지를 바꾸는가)가 배포 수준의 서비스 경계보다 먼저 해결해야 할 문제임을 보여준다. 동시에 초기 월 인프라 예산은 15만 원 수준을 목표로 하고([docs/07-adr/adr-traceability.md](../adr-traceability.md)), 배포 토폴로지는 단일 EC2 인스턴스(Nginx 리버스 프록시 + Next.js 프론트엔드 + Spring Boot 백엔드)에 수동 복구를 전제로 하며 ALB·ASG·Blue-Green은 Post-MVP로 보류한다([docs/06-architecture/technology-policy.md](../../06-architecture/technology-policy.md) 13절). 이 예산·배포 제약 안에서 Restaurant·Creator·Video·Visit 각 영역의 정책 소유권과 변경 경계를 어떻게 보존할지가 이 결정의 배경이다.

## 4. 결정 문제

단일 EC2·15만 원 예산 제약 안에서 MVP의 배포 단위와 내부 코드 경계를 어떻게 구성해야 4명이 서로의 작업을 막지 않고 각자 담당 Workstream을 독립적으로 완결할 수 있는가.

## 5. 고려한 선택지

- 단일 모듈 도메인 중심 계층형 모놀리스: 하나의 배포 산출물 안에서 최상위 패키지를 Workstream이 소유하는 도메인(예: restaurant, creator, video, visit) 단위로 나누고, 각 도메인 내부에서만 web·application·domain·infrastructure 계층을 둔다.
- 기술 계층 중심 단일 모듈: 전체 애플리케이션을 Controller·Service·Repository 등 기술 계층별 패키지로 나누고 그 안에 모든 도메인 코드를 함께 둔다. [roles.md](../../03-team/roles.md)가 명시적으로 금지하는 계층별 역할 분담과 구조가 그대로 일치하므로, 도메인 하나를 변경할 때마다 4개 계층 패키지를 넘나들며 다른 담당자의 변경과 충돌할 여지가 커 4인 독립 개발이라는 [scope.md](../../00-overview/scope.md) 6번 조건과 맞지 않는다.
- 초기 멀티모듈 또는 마이크로서비스: Workstream 경계를 그대로 서비스 경계로 승격하는 방식이다. 서비스마다 별도 배포 파이프라인·헬스체크·모니터링·네트워크 구성이 필요해 15만 원 목표 예산과 단일 EC2 배포 결정을 벗어난다. 또한 [roles.md](../../03-team/roles.md)가 밝히듯 팀원 개인별 기술 역량과 선호도가 아직 확인되지 않은 상태에서 서비스 경계별 오너십(배포 권한, 장애 대응, 계약 버전 관리)까지 함께 지우는 것은 검증되지 않은 역량 위에 운영 부담을 얹는 결과가 되어 MVP 기간 내 독립 개발이라는 목표를 오히려 해친다.

## 6. 결정

단일 Gradle 빌드·단일 Spring Boot 실행 아티팩트를 하나의 EC2 인스턴스에 배포한다. 최상위 패키지는 Workstream이 소유한 도메인 책임(예: restaurant, creator, video, visit)을 드러내고, 각 도메인 패키지 내부에서 계층을 명시적으로 유지하며 의존 방향은 도메인이 프레임워크·인프라 세부사항에 의존하지 않는 방향으로 고정한다.

## 7. 선택 근거

단일 배포 아티팩트는 2026-07-24 결정된 단일 EC2 배포 토폴로지와 정확히 대응한다. 서비스를 여러 개로 쪼개면 인스턴스·리버스 프록시·모니터링을 서비스 수만큼 늘려야 하지만, 지금은 하나의 Jar를 하나의 인스턴스에서 실행하는 구조로 충분하다. 도메인 중심 패키지는 [roles.md](../../03-team/roles.md)가 정의한 "Workstream 담당자가 요구사항부터 통합까지 끝까지 책임진다"는 소유권 모델을 코드 구조로 그대로 옮긴 것이다. 각 담당자는 자신의 패키지 안에서 클래스 구조를 스스로 결정할 수 있고([roles.md](../../03-team/roles.md) 4~7절의 "결정 권한"), 이는 서비스 간 배포·계약 관리 비용 없이도 [scope.md](../../00-overview/scope.md) 6번이 요구하는 독립 개발을 코드 수준에서 달성한다. 즉 이 선택은 팀 규모·예산·배포 결정이라는 이미 확정된 제약을 그대로 만족시키는 가장 낮은 비용의 구조이며, 단순히 "모놀리스가 무난해서"가 아니라 대안들이 구체적으로 이 제약을 위반하기 때문에 남는 선택이다.

## 8. 트레이드오프

4명이 하나의 저장소·하나의 실행 프로세스를 공유하므로 공통 응답 형식, 예외 처리, 설정 파일과 같은 공유 파일 변경이 겹치면 병합 충돌이나 의도치 않은 계약 변경이 발생할 수 있다. 또한 한 도메인의 무거운 조회나 장애가 같은 JVM·DB 커넥션 풀을 쓰는 다른 도메인의 응답에 영향을 줄 수 있고, 단일 EC2 배포이므로 배포 실패나 장애가 전체 서비스에 영향을 미친다(수동 복구 전제, [technology-policy.md](../../06-architecture/technology-policy.md) 13절). 이 위험은 패키지 의존 방향을 코드 리뷰와 아키텍처 테스트로 강제하고, [roles.md](../../03-team/roles.md) 10절의 "공유 파일은 변경 범위를 최소화하고 한 변경에서 구현 책임자 한 명이 병합을 주도한다" 규칙과 "다른 Workstream 담당자 한 명 이상이 리뷰한다" 규칙으로 완화한다. 즉 운영·배포 단순성을 얻는 대신 결합 위험을 조직적 규율(코드 리뷰, 계약 변경 절차)로 관리하는 트레이드오프를 받아들인다.

## 9. 적용 범위

전체 백엔드 코드, 패키지 구조, [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) Workstream 통합과 단일 EC2 배포 단위에 적용한다. 프론트엔드(Next.js)와 관리자 인증([ADR-AUTH-001](../security/auth-001-spring-security-jwt.md))처럼 별도 ADR로 이미 결정된 영역은 이 ADR의 세부 구조 결정 범위에서 제외한다.

## 10. 강제 규칙

도메인 규칙을 웹·데이터 접근 계층에 분산하지 않고 각 도메인 패키지 안에 모은다. 다른 도메인의 내부 엔티티·리포지토리를 직접 참조하지 않고, [roles.md](../../03-team/roles.md) 10절이 정의한 대로 소유 담당자가 노출한 식별자·상태·판정 결과 계약만 사용한다(예: [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)은 [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 제공하는 유튜버 조건 판정 결과만 사용하고 Visit 내부 규칙을 재구현하지 않는다).

## 11. 금지 사항

Workstream별 독립 서비스 선제 분리, 기술 계층만으로 모든 도메인을 혼합하는 구조, 도메인 간 순환 의존, 팀 합의 없는 패키지 경계 변경([roles.md](../../03-team/roles.md) 9절 "공동 결정 사항"의 패키지 구조 변경 항목)을 금지한다.

## 12. 구현 및 운영 영향

단일 산출물(Jar)로 빌드해 단일 EC2 인스턴스에 배포하며, Nginx가 리버스 프록시 역할을 한다([technology-policy.md](../../06-architecture/technology-policy.md) 13절). 패키지 의존성 검사(ArchUnit 등)를 CI에 포함하고, 도메인별 테스트 경계를 유지해 다른 담당자의 도메인 테스트를 깨지 않고 자신의 도메인을 리팩터링할 수 있게 한다. 공유 설정·공통 응답 클래스는 변경 시 영향받는 Workstream 담당자의 리뷰를 거친다.

## 13. 검증 방법

아키텍처 테스트(예: ArchUnit)로 도메인 패키지 간 금지된 의존 방향이나 순환 의존이 생기면 빌드를 실패시킨다. PR 리뷰 체크리스트에 "다른 도메인 내부 클래스 직접 import 여부"를 포함해 코드 리뷰 단계에서 걸러낸다. Workstream 간 통합 테스트로 [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)·[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 서로 노출한 계약(식별자·판정 결과)만으로 조합이 성립하는지 확인한다. 단일 프로세스 구조이므로 도메인 간 호출에 네트워크 지연이 없다는 전제가 깨지지 않는지, 즉 일반 조회 p95 500ms·검색·필터 p95 800ms([RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율), 결정됨) 목표에 도메인 간 결합으로 인한 불필요한 지연이 섞이지 않는지 통합 테스트로 함께 확인한다.

## 14. 재검토 조건

독립 배포·확장·보안 경계가 측정 가능한 병목이 되거나(예: 특정 도메인만 트래픽이 급증해 단일 인스턴스로 감당이 안 되는 경우), 팀 규모가 커지거나, 초기 월 15만 원 예산 목표 자체가 상향 조정되어 다중 인스턴스·서비스 분리 비용을 감당할 수 있게 되거나, [roles.md](../../03-team/roles.md)의 [RV-ROLE-006](../../03-team/roles.md#rv-role-006-개인별-역량선호-반영)(개인별 역량·선호 확인)에 따라 Workstream 재배정이 이루어져 소유권 경계가 바뀔 때 재검토한다.

## 15. 관련 문서

- [도메인 경계](../../02-analysis/domain-boundaries.md)
- [MVP Workstream](../../02-analysis/mvp-workstreams.md)
- [팀 역할](../../03-team/roles.md)
- [기술 정책](../../06-architecture/technology-policy.md)
