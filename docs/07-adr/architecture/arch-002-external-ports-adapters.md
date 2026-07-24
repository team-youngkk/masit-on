---
id: ADR-ARCH-002
title: 외부 연동 Port/Adapter 경계
status: Accepted
decision_date: 검토 필요
owners:
  - 이우람
related_requirements:
  1: NFR-EXTERNAL-001
  2: NFR-EXTERNAL-002
  3: NFR-EXTERNAL-003
  4: NFR-INTEGRITY-004
related_documents:
  1: ../../01-requirements/non-functional-requirements.md
  2: ../../05-specs/api/admin/reference-data-api.md
  3: ../integration/ext-001-reference-verification.md
  4: ../security/sec-001-secrets-workload-identity.md
  5: ../quality/test-001-automation-strategy.md
  6: ../../02-analysis/mvp-workstreams.md
  7: ../../00-overview/scope.md
  8: ../../03-team/roles.md
supersedes: []
superseded_by: null
---

# ADR-ARCH-002 외부 연동 Port/Adapter 경계

## 1. 상태

Accepted

## 2. 결정 요약

Kakao·YouTube 등 외부 서비스 호출은 애플리케이션이 소유한 Port와 제공자별 Adapter로 격리한다.

## 3. 배경

외부 API 호출은 [#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 등록(김인안)에서만 발생한다. 맛집 등록 시 Kakao Local REST API로 장소를 확인하고, 유튜버·영상 등록 시 YouTube Data API v3로 채널·영상을 확인한다([#7 scope.md](../../00-overview/scope.md) 3.4절, [#3 ADR-EXT-001](../integration/ext-001-reference-verification.md)). 이 호출은 관리자가 등록 화면에서 직접 트리거하는 동기 호출이며, 일반 사용자가 사용하는 [#6 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)(양성훈)·[#6 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)(박진영)·[#6 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)(이우람)의 조회 경로는 저장된 내부 데이터만 사용하고 실시간 외부 호출을 필수 경로로 두지 않는다([#7 scope.md](../../00-overview/scope.md) 4.6절, [#3 ADR-EXT-001](../integration/ext-001-reference-verification.md) 11절 금지 사항).

즉 외부 API를 직접 호출하는 담당자(김인안)와 그 결과로 저장된 데이터를 읽는 담당자(양성훈·박진영·이우람)가 서로 다르며, [#8 roles.md](../../03-team/roles.md)도 "등록 결과가 세 조회 Workstream에 반영되는지 담당자들과 인수 테스트한다"는 책임을 [#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)에 명시한다. 외부 제공자(Kakao·YouTube)의 응답 형식·오류·요율 제한 변화가 등록 도메인 규칙과 조회 도메인 규칙에 그대로 새어 들어가면, 소유권이 다른 Workstream들이 서로의 코드 변경 없이도 영향을 받게 된다.

## 4. 결정 문제

변동성이 큰 외부 제공자(Kakao·YouTube)를 [#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 코드가 어떻게 호출하고, 그 결과가 다른 Workstream이 읽는 내부 데이터와 어떻게 분리되어야 하는가.

## 5. 고려한 선택지

- 내부 Port와 외부 Adapter: 유스케이스는 내부 Port 인터페이스에만 의존하고, Kakao·YouTube 각각의 Adapter가 SDK·HTTP 호출과 응답 변환을 전담한다.
- 서비스 계층에서 SDK·HTTP Client 직접 호출: 등록 유스케이스 코드가 Kakao Local·YouTube Data API 클라이언트를 직접 호출한다. 등록은 김인안([#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))이 소유하지만 저장되는 결과(장소 일치 여부, 채널명, 영상 메타데이터)는 양성훈·박진영·이우람의 조회 도메인이 그대로 사용하므로, 직접 호출로 제공자 응답 필드·인증 방식·오류 코드가 등록 유스케이스 코드에 흩어지면 제공자 쪽 변경이나 장애가 등록 로직 전반을 건드리게 되고 조회 담당자들이 그 결과 계약을 신뢰하기 어려워진다.
- 제공자 응답 모델을 내부 모델로 사용: Kakao·YouTube의 DTO를 그대로 저장·전달한다. 제공자가 필드명이나 구조를 바꾸면 그 여파가 등록 도메인뿐 아니라 이를 소비하는 조회 Workstream까지 즉시 전파되며, [#3 ADR-EXT-001](../integration/ext-001-reference-verification.md) 11절이 금지하는 "관리자 확인 없는 자동 등록"과도 결합하기 쉬워 관리자 검증이라는 MVP 원칙([#7 scope.md](../../00-overview/scope.md) 3.4절 "중복 등록 방지")을 지키기 어렵다.

무거운 회복탄력성 프레임워크(예: Resilience4j Circuit Breaker, 별도 재시도 큐)의 전면 도입은 이번 결정에서 함께 채택하지 않는다. Kakao·YouTube 호출은 관리자가 등록 화면에서 트리거하는 저빈도·동기 호출이며 대량 트래픽을 상시 처리하는 통합이 아니므로, Adapter 수준의 타임아웃·오류 변환만으로 시작하고 실제 실패율·호출량이 확인된 뒤([#1 RV-NFR-001](../../01-requirements/non-functional-requirements.md#rv-nfr-001-목표-동시-사용자-수)·[#1 RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모), 동시 사용자·데이터 규모 미확정 참조) 회로 차단기 도입 여부를 재검토한다.

## 6. 결정

유스케이스([#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 흐름)는 내부 Port(예: 장소 확인, 채널 확인, 영상 확인 성격의 인터페이스)에만 의존하고, Kakao Adapter·YouTube Adapter가 제공자 인증·호출·응답을 내부 확인 결과 모델로 변환한다. 조회 Workstream([#6 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[#6 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[#6 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색))은 Adapter나 제공자 응답이 아니라 등록 시 저장된 내부 확인 결과 데이터만 사용한다.

## 7. 선택 근거

Port/Adapter 경계는 "누가 무엇을 호출하는가"와 "누가 그 결과를 읽는가"가 다른 이 프로젝트의 실제 소유권 구조(김인안 대 양성훈·박진영·이우람)를 코드로 반영한다. 등록 담당자는 Adapter만 교체·수정하면 되고 조회 담당자는 Port가 정의한 내부 확인 결과 계약만 알면 되므로, [#8 roles.md](../../03-team/roles.md) 10절의 "다른 Workstream의 내부 구현에 직접 의존하지 않고 합의된 계약만 사용한다"는 협업 규칙을 그대로 만족한다. 동시에 호출 자체가 관리자 트리거 방식의 저빈도 동기 호출이라는 실제 특성([#7 scope.md](../../00-overview/scope.md) 3.4절, [#3 ADR-EXT-001](../integration/ext-001-reference-verification.md)) 때문에, Port/Adapter라는 가벼운 구조적 격리로도 장애 전파를 막기에 충분하며 회로 차단기 같은 무거운 회복탄력성 계층까지 지금 도입할 근거는 부족하다.

## 8. 트레이드오프

Port 인터페이스와 Adapter 구현, 제공자 DTO↔내부 모델 변환 코드가 추가로 필요해 등록 기능만 놓고 보면 직접 호출보다 코드량이 늘어난다. 이 비용은 제공자 계약 변화나 API 장애가 등록 유스케이스·조회 도메인 코드까지 번지는 것을 막아 얻는 안정성으로 상쇄되며, 특히 조회 경로([#6 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[#6 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[#6 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색))가 외부 API 장애와 완전히 분리된다는 이점이 등록 기능 하나의 추가 코드 비용보다 크다고 판단한다. 이 추가 비용은 WireMock 기반 계약 테스트([#3 ADR-EXT-001](../integration/ext-001-reference-verification.md) 13절)로 검증 비용 자체를 관리한다.

## 9. 적용 범위

Kakao Local, YouTube Data API와 향후 승인된 지도·AI·크롤링 연동에 적용한다. [#6 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 화면에서 트리거되는 확인 호출에 한정하며, 일반 사용자 조회 API 경로에는 실시간 외부 호출을 두지 않는다([#3 ADR-EXT-001](../integration/ext-001-reference-verification.md) 11절).

## 10. 강제 규칙

Port는 Kakao·YouTube라는 이름이나 필드 구조가 아니라 "장소 확인 결과", "채널 확인 결과"와 같은 내부 용어를 사용한다. Adapter는 인증(API 키), 요율 제한, 타임아웃, 오류를 내부 실패 유형으로 변환하는 책임과 응답 검증(존재 여부, 관리자가 확인할 표시 정보 추출)을 담당한다.

## 11. 금지 사항

Controller·도메인에서 제공자 SDK·HTTP Client 직접 호출, 제공자 DTO를 그대로 저장·응답에 노출, 공개 조회 API의 필수 경로에 실시간 외부 호출을 두는 것을 금지한다([#3 ADR-EXT-001](../integration/ext-001-reference-verification.md) 11절과 동일 기준).

## 12. 구현 및 운영 영향

Kakao·YouTube 제공자별 설정(API 키, 엔드포인트, 타임아웃 값)과 호출 실패 시 메트릭·로그, WireMock 기반 계약 대체 테스트가 필요하다. API 키는 [#4 ADR-SEC-001](../security/sec-001-secrets-workload-identity.md)이 정한 Parameter Store SecureString으로 보호한다.

## 13. 검증 방법

Adapter 계약 테스트(WireMock으로 정상·없음·요율 제한·지연·응답 변경 시나리오 주입), 타임아웃·오류·부분 실패 주입 테스트, 비밀정보(API 키) 노출 검사를 수행한다. 특히 등록 중 Kakao·YouTube 호출이 타임아웃되거나 실패해도 이미 저장된 데이터를 읽는 [#6 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[#6 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[#6 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 조회 API 응답 시간과 오류율에는 영향이 없어야 하며, 이는 일반 조회 p95 500ms·검색·필터 p95 800ms·오류율 1% 미만([#1 RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율), 결정됨) 기준으로 통합 테스트에서 확인한다. 등록 자체의 실패는 관리자 등록 p95 1초 기준([#1 RV-NFR-004](../../01-requirements/non-functional-requirements.md#rv-nfr-004-목표-응답-시간과-허용-오류율)) 안에서 명확한 오류로 반환되는지 별도로 검증한다.

## 14. 재검토 조건

내부 Port가 제공자 특화 기능(예: Kakao 카테고리 세분류, YouTube 채널 통계)을 가려 실제 요구사항을 만족시키지 못하거나, 연동 호출량·실패율이 실측되어([#1 RV-NFR-001](../../01-requirements/non-functional-requirements.md#rv-nfr-001-목표-동시-사용자-수)·[#1 RV-NFR-002](../../01-requirements/non-functional-requirements.md#rv-nfr-002-초기-데이터-규모) 확정 이후) 회로 차단기 같은 별도 회복탄력성 계층이 필요해지거나, 연동 책임 자체가 별도 배포 경계를 요구할 때 재검토한다.

## 15. 관련 문서

- [#1 NFR](../../01-requirements/non-functional-requirements.md)
- [#2 관리자 기준정보 API](../../05-specs/api/admin/reference-data-api.md)
- [#3 외부 기준정보 확인 서비스 ADR](../integration/ext-001-reference-verification.md)
- [#4 비밀정보 ADR](../security/sec-001-secrets-workload-identity.md)
