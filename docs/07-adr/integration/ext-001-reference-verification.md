---
id: ADR-EXT-001
title: 관리자 외부 기준정보 확인 서비스
status: Accepted
decision_date: 2026-07-27
last_reviewed: 2026-07-27
owners:
  - 김인안
related_requirements:
  - FR-ADMIN-002
  - FR-ADMIN-003
  - FR-ADMIN-004
  - NFR-EXTERNAL-002
  - NFR-EXTERNAL-003
related_documents:
  - ../../00-overview/scope.md
  - ../../04-product/prd/admin/admin-data-management.md
  - ../../05-specs/api/admin/reference-data-api.md
  - ../architecture/arch-002-external-ports-adapters.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../security/auth-003-confirmation-token.md
  - ../quality/test-001-automation-strategy.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../03-team/roles.md
  - ../../01-requirements/non-functional-requirements.md
  - ../adr-backlog.md
  - ../adr-traceability.md
  - ../quality/obs-001-logging-observability.md
supersedes: []
superseded_by: null
---

# ADR-EXT-001 관리자 외부 기준정보 확인 서비스

## 1. 상태

Accepted

## 2. 결정 요약

관리자 맛집 확인은 Kakao Local REST API V2, 채널·영상 확인은 YouTube Data API v3를 사용하고 관리자가 결과를 확인한 뒤 저장한다.

## 3. 배경

[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 등록 흐름은 맛집·유튜버·영상·방문 관계 네 대상을 등록하며, [scope.md](../../00-overview/scope.md) 3.4절은 중복 판단 기준을 구체적으로 정의한다. 카카오에서 동일한 장소로 확인되면 중복 맛집, 동일 YouTube 채널이면 중복 유튜버, 동일 영상이면 중복 영상, 동일한 (맛집, 유튜버, 영상) 조합이면 중복 방문 관계로 판단해야 한다. 이 판단은 관리자의 기억이나 이름 비교만으로는 신뢰할 수 없고, 외부 제공자가 부여한 안정적인 식별자(카카오 place id, YouTube channel id·video id)가 있어야 한다.

이 확인은 [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)를 단독 소유하는 김인안이 구현하며([roles.md](../../03-team/roles.md) 7장), 등록 시점에만 발생한다. 일반 사용자 조회는 저장된 데이터만 사용하고 Kakao·YouTube를 실시간으로 호출하지 않는다([RV-NFR-016](../../01-requirements/non-functional-requirements.md#rv-nfr-016-외부-링크-상태-확인-정책), [NFR-EXTERNAL-003](../../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보)). 즉 외부 API 호출은 관리자 등록이라는 좁은 경계 안에 갇혀 있으며, 이 경계를 정확히 지키는 것이 공개 조회 성능과 외부 장애로부터의 격리([NFR-EXTERNAL-001](../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리))를 지키는 전제 조건이다.

[NFR-EXTERNAL-002](../../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리)는 외부 API를 도입할 경우 실패·지연·사용 제한 초과·응답 구조 변경의 영향이 연계 경계 밖으로 확산되지 않아야 한다고 요구한다. Kakao·YouTube는 팀이 통제할 수 없는 제3자 서비스이므로, 이 경계를 코드 구조로 어디에 둘지—등록 유스케이스 내부에 흩어 놓을지, 별도 어댑터로 모을지—가 이 ADR이 실질적으로 답해야 하는 질문이다.

## 4. 결정 문제

MVP 등록 과정에서 장소와 YouTube 자원의 존재·동일성을 어떤 외부 기준으로 확인할 것인가. 이 결정은 다음 제약 안에서 이루어져야 한다: [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)는 김인안 한 명이 등록 순서·중복·원자성까지 끝까지 책임지고([roles.md](../../03-team/roles.md)), 4명의 백엔드 개발자가 각자 MVP 기간 내에 독립적으로 개발·검증할 수 있어야 하며([scope.md](../../00-overview/scope.md) 6번 경계 규칙), AI 기반 자동 판정과 크롤링은 이미 MVP 제외 범위로 결정되어 있다([scope.md](../../00-overview/scope.md) 4.5절, [ADR-AI-001](../adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출), [ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) Post-MVP).

부수적으로, 확인 실패·지연을 어디에서 얼마나 감당할 것인가도 함께 결정해야 한다. 관리자 등록은 관리자 한 명이 트리거하는 저빈도 작업이므로, 이 결정 문제는 "완벽한 가용성을 보장하는 복원력 체계를 구축할 것인가" 대신 "이 정도 호출 빈도에 비례하는 최소한의 실패 처리로 충분한가"를 묻는다.

## 5. 고려한 선택지

- **관리자 자유 입력만 사용**: 외부 식별자 없이 관리자가 입력한 이름·주소·채널명만으로 중복을 판단해야 한다. 그런데 [scope.md](../../00-overview/scope.md)의 중복 판단 기준은 "카카오에서 동일한 장소" "동일한 YouTube 채널·영상"처럼 외부 제공자 식별자를 전제로 정의되어 있어, 자유 입력만으로는 이 기준 자체를 구현할 수 없다. 관리자 계정은 모두 동일한 등록 권한을 가지며 별도의 교차 검수 단계가 없으므로([scope.md](../../00-overview/scope.md) 3.4 "관리자 접근"), 등록 건수가 늘어날수록 이름만으로 동일 장소·동일 채널 여부를 관리자가 기억에 의존해 판단하는 것은 [NFR-INTEGRITY-002](../../01-requirements/non-functional-requirements.md#nfr-integrity-002-중복-및-동시-등록-방지)(중복 및 동시 등록 방지, Critical)를 만족시키기 어렵다.
- **크롤링·AI 자동 판정**: 관리자 확인 없이 자동으로 존재·동일성을 판정하는 방식이다. [scope.md](../../00-overview/scope.md) 4.5절은 "AI 또는 외부 데이터 수집을 통한 자동 등록"과 "관리자 확인 없는 자동 등록"을 MVP 제외 범위로 명시하며, [ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리)(Jsoup·n8n·Scheduler·Batch)과 [ADR-AI-001](../adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출)(Spring AI·Gemini)은 이미 Post-MVP로 분류되어 있다. 지금 이 방식을 채택하면 별도의 범위 변경 절차 없이 제외 범위를 구현 범위로 끌어오는 것이 되어 채택할 수 없다.
- **Kakao Local·YouTube 공식 API 조회 + 관리자 확인**: 관리자가 채널 링크·원본 링크를 입력하면 API로 존재·현재 표시 정보를 조회하고, 그 결과를 관리자가 확인한 뒤 저장한다([RV-NFR-008](../../01-requirements/non-functional-requirements.md#rv-nfr-008-외부-youtube-api-사용-여부), [RV-NFR-016](../../01-requirements/non-functional-requirements.md#rv-nfr-016-외부-링크-상태-확인-정책) 결정 완료). 이 방식만이 [scope.md](../../00-overview/scope.md)가 요구하는 외부 식별자 기반 동일성 판단과 "관리자 확인 후 저장"이라는 두 조건을 동시에 만족한다.

## 6. 결정

공식 API 조회 결과를 Port/Adapter로 격리하고 관리자 확인 후 내부 데이터로 저장한다. 공개 조회는 저장 데이터를 사용한다. 이 호출은 [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 화면에서 관리자가 트리거하는 동기 호출이며, 등록 건별로 발생하는 저빈도·저트래픽 호출이다. 일반 사용자를 대상으로 하는 고빈도 호출이 아니므로 메시지 큐나 별도 워커 없이 요청-응답 방식으로 처리한다.

Kakao 어댑터와 YouTube 어댑터는 동일한 Port 계약(존재 확인, 현재 표시 정보 조회, 오류 유형 반환)을 따르는 별개의 구현으로 둔다. 두 제공자의 응답 스키마와 오류 형식이 다르더라도, 등록 유스케이스 코드는 Port 인터페이스만 알고 제공자별 세부 구현을 알 필요가 없게 해 [NFR-MAINTAINABILITY-002](../../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치)(비즈니스 규칙이 요청 처리 계층에 분산되지 않아야 함)를 지킨다.

## 7. 선택 근거

- [scope.md](../../00-overview/scope.md)의 중복 판단 기준(동일 카카오 place, 동일 채널, 동일 영상, 동일 (맛집, 유튜버, 영상) 조합)은 외부 제공자 식별자를 전제로 하므로, 그 식별자를 얻을 수 있는 유일한 신뢰 가능한 경로는 공식 API 조회다. 자체 매칭 규칙을 새로 만드는 대신 제공자가 보증하는 식별자를 그대로 동일성 기준으로 사용해 별도의 판단 로직을 검증할 필요를 없앤다.
- 호출 패턴이 동기·저빈도·관리자 트리거형이라는 점이 서킷 브레이커 라이브러리나 별도 재시도 큐 같은 무거운 복원력 프레임워크를 채택하지 않는 근거다. 등록은 김인안 한 명이 수행하는 관리 작업이며 동시에 다수의 등록이 몰리는 공개 트래픽이 아니므로, 타임아웃과 관리자에게 노출되는 오류 구분만으로 실패를 감당할 수 있는 규모다.
- [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)는 김인안이 단독으로 책임지고 4명이 각자 독립적으로 개발해야 하는 제약([scope.md](../../00-overview/scope.md) 6번) 아래에서, Port/Adapter로 외부 호출을 격리하면 WireMock으로 계약을 고정해 실제 Kakao·YouTube 없이도 김인안이 혼자 개발·테스트를 완결할 수 있다. 다른 워크스트림 담당자와의 조율 없이 검증 가능한 구조라는 점이 팀 구조상 실질적인 이점이다.

## 8. 트레이드오프

관리자 등록 요청은 Kakao·YouTube 응답을 기다리는 동안 동기적으로 블로킹된다. Kakao 또는 YouTube가 느리거나 응답하지 않으면:

- [NFR-PERFORMANCE-003](../../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간)은 관리자 등록의 애플리케이션 내부 처리 시간만 p95 1초로 측정하고 외부 서비스 지연은 별도로 측정하도록 명시하므로, 외부 지연 자체는 이 성능 기준을 위반하지 않는다. 하지만 등록을 시도하는 관리자 세션은 실질적으로 그 시간만큼 대기하거나 실패를 마주한다.
- MVP 범위에는 메시지 큐나 비동기 워커가 없으므로([ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) Post-MVP, Spring Batch·Scheduler 비활성), 실패한 등록은 관리자가 나중에 수동으로 재시도하는 것이 유일한 복구 경로다.
- 이 위험은 인프라로 없애지 않고 범위를 좁혀 완화한다. 첫째, 명시적 타임아웃을 두어 응답 없는 호출이 관리자 세션을 무한정 묶어두지 않게 한다(10장 강제 규칙). 둘째, 실패를 등록 실패로 명확히 구분해 반환하고 부분 저장을 금지한다([NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)) — 즉 외부 호출 실패가 일부만 저장된 맛집·유튜버·영상을 남기지는 않는다. 저빈도·단일 관리자 작업이라는 호출 패턴과 초기 월 150,000원 예산 목표([adr-traceability.md](../adr-traceability.md))를 고려하면, 이 정도의 관리자 측 재시도 부담을 수용하고 별도 비동기 인프라를 두지 않는 것이 이 시점에서 합리적인 절충이다.

## 9. 적용 범위

- 포함: [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 맛집 등록의 카카오 장소 일치 확인, 유튜버 등록의 채널 존재·채널명 확인, 영상 등록의 존재·제목·썸네일·게시 채널 확인.
- 제외: 일반 사용자 조회 API(이 Port/Adapter를 호출하지 않으며 저장된 데이터만 사용, [RV-NFR-016](../../01-requirements/non-functional-requirements.md#rv-nfr-016-외부-링크-상태-확인-정책)), 등록 이후의 주기적 재확인(11장 금지 사항), 지도 표시·길찾기 등 Kakao·YouTube의 다른 API 기능([ADR-MAP-001](../adr-backlog.md#adr-map-001-지도-표시와-공간-검색), [ADR-ROUTE-001](../adr-backlog.md#adr-route-001-kakao-mobility와-동선-추천) Post-MVP).

## 10. 강제 규칙

- 관리자 확인을 필수로 하고, 관리자가 확인하지 않은 조회 결과는 저장하지 않는다. 관리자 화면에는 현재 표시 정보와 정규화 URL(장소명·주소, 채널명, 영상 제목·썸네일)을 노출한다. 제공자 원본 식별자(Kakao place ID, YouTube channel/video ID)는 서버의 동일성 판정과 저장소 유일 키로만 사용하며 API·화면에 노출하지 않는다.
- 제공자 원본 ID를 동일성 기준으로 사용한다. 구체적으로 동일 카카오 place id는 중복 맛집, 동일 YouTube 채널 id는 중복 유튜버, 동일 YouTube 영상 id는 중복 영상, 동일한 (맛집, 유튜버, 영상) id 조합은 중복 방문 관계로 판단한다([scope.md](../../00-overview/scope.md) 3.4).
- 제공자 원본 ID는 비밀정보는 아니지만 내부 구현 정보로 분류해 일반·관리자 응답과 업무 로그에서 제외한다.
- 상호명이 같아도 카카오 place id가 다르면 별도 지점으로 등록할 수 있다([scope.md](../../00-overview/scope.md) 명시 규칙).
- 타임아웃과 오류를 등록 실패와 구분해 관리자에게 원인(존재하지 않음, 요청 제한 초과, 응답 지연·장애)을 알 수 있는 형태로 노출한다.

## 11. 금지 사항

- 자동 주기 동기화: 등록 이후 Kakao·YouTube 정보를 주기적으로 재확인하지 않는다([RV-NFR-016](../../01-requirements/non-functional-requirements.md#rv-nfr-016-외부-링크-상태-확인-정책) 결정, 자동 주기 확인은 MVP 제외).
- 관리자 확인 없는 저장: 조회 결과를 관리자가 확인하기 전에 내부 데이터로 확정하지 않는다([NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)).
- 공개 조회의 실시간 외부 호출: 일반 사용자 요청이 Kakao·YouTube를 직접 호출하지 않는다([NFR-EXTERNAL-003](../../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보), 응답 시간·비용 보호).
- Maps·Mobility·AI 기능 확장: 지도 SDK([ADR-MAP-001](../adr-backlog.md#adr-map-001-지도-표시와-공간-검색)), 길찾기([ADR-ROUTE-001](../adr-backlog.md#adr-route-001-kakao-mobility와-동선-추천)), AI 자동 추출([ADR-AI-001](../adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출))은 이미 Post-MVP로 분류되어 있으므로 이 ADR의 범위로 끌어오지 않는다.

## 12. 구현 및 운영 영향

- API 키(Kakao REST API 키, YouTube API 키)는 소스 코드·저장소와 분리하고 Parameter Store SecureString + KMS로 보호한다([ADR-SEC-001](../security/sec-001-secrets-workload-identity.md)).
- Kakao·YouTube Adapter는 연결 timeout 2초, 전체 응답 timeout 5초를 사용하며 설정·WireMock 테스트·운영 문서에 같은 값을 적용한다.
- 등록 이후 외부 표시 메타데이터는 최신값만 유지하고 변경 이력을 별도로 저장하지 않는다. 자동 갱신은 MVP에서 수행하지 않는다.
- 제공자 오류를 존재하지 않음(404류), 요청 제한 초과, 응답 지연·서버 오류로 구분해 관리자에게 각기 다른 안내로 매핑한다.
- `READY` 중간 결과는 [ADR-AUTH-003](../security/auth-003-confirmation-token.md)에 따라 PostgreSQL 확인 Token 레코드의 10분 수명 후보 JSONB Snapshot으로만 저장한다. 핵심 Entity나 미확정 영구 자원으로 저장하지 않으며 `DUPLICATE`·`REVIEW_REQUIRED`에는 Token 레코드를 만들지 않는다.
- 실패한 호출의 재시도는 자동 재시도 루프가 아니라 관리자가 화면에서 다시 시도하는 수동 재시도로 한정한다. 자동 재시도를 넣으려면 상한 횟수·백오프 정책을 별도로 설계해야 하므로([NFR-RELIABILITY-002](../../01-requirements/non-functional-requirements.md#nfr-reliability-002-저장소-장애-및-재시도-통제)), 이 ADR 범위에서는 도입하지 않는다.
- 외부 호출 실패·성공 이벤트는 [ADR-OBS-001](../quality/obs-001-logging-observability.md)의 오류 분류 규칙에 따라 외부 서비스 오류로 별도 기록해, 저장소 오류나 인증 실패와 혼동되지 않게 한다.

## 13. 검증 방법

WireMock으로 다음 시나리오별 등록 분기를 검증한다: 정상 응답, 존재하지 않음, 요청 제한 초과, 응답 지연·타임아웃, 조회 시점 이후 표시 정보 변경(예: 채널명 변경). 이에 더해 다음을 구체적으로 검증한다.

- 중복 판정 규칙별 통합 테스트: 동일 place id 재등록 시 기존 맛집 재사용, 동일 채널 id 재등록 시 기존 유튜버 재사용, 동일 영상 id 재등록 시 기존 영상 재사용, 동일한 (맛집, 유튜버, 영상) 조합 재등록 시 신규 방문 관계 생성 거부([scope.md](../../00-overview/scope.md) 3.4, [NFR-INTEGRITY-002](../../01-requirements/non-functional-requirements.md#nfr-integrity-002-중복-및-동시-등록-방지)).
- API 키를 포함한 비밀정보가 로그·응답에 노출되지 않는지 검사([NFR-PRIVACY-002](../../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호)).
- 외부 호출 실패로 등록이 중단됐을 때 부분 저장이 0건인지 검사([NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)).
- 관리자 등록 API의 내부 처리 시간(외부 호출 대기 제외) p95 1초 기준을 검증할 때, WireMock 지연 시나리오의 대기 시간이 그 측정에서 분리되는지 확인한다([NFR-PERFORMANCE-003](../../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간) 측정 조건).

## 14. 재검토 조건

제공자 API 종료·계약 변경, 초기 월 150,000원 인프라 예산 목표 대비 API 호출 비용이 한계를 넘어설 때, 또는 범위가 자동 수집·AI 자동 판정으로 변경 승인될 때([scope.md](../../00-overview/scope.md) 4.5절 범위 변경 절차 완료, [ADR-AI-001](../adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출)·[ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) 활성화 조건 충족 시) 재검토한다.

## 15. 관련 문서

- [관리자 PRD](../../04-product/prd/admin/admin-data-management.md)
- [관리자 기준정보 API](../../05-specs/api/admin/reference-data-api.md)
- [Port/Adapter ADR](../architecture/arch-002-external-ports-adapters.md)
