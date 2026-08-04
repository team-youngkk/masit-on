---
id: PRD-NOTIFICATION-001
title: 사용자 알림
status: draft
workstream: WS-13
owner: 이우람
reviewers:
  - 김인안
related_requirements:
  - FR-NOTIFICATION-001
  - FR-NOTIFICATION-002
  - FR-NOTIFICATION-003
  - FR-NOTIFICATION-004
related_business_rules:
  - BR-NOTIFICATION-001
  - BR-NOTIFICATION-002
  - BR-NOTIFICATION-003
  - BR-NOTIFICATION-004
related_nfr:
  - NFR-INTEGRITY-005
  - NFR-RELIABILITY-004
  - NFR-PRIVACY-005
  - NFR-TEST-005
related_documents:
  - ../../../00-overview/scope.md
  - ../../../05-specs/api/notification/notification-api.md
  - ../../../05-specs/data/second-expansion-data-contract.md
  - ../../../07-adr/integration/notify-002-in-app-notification-reliability.md
  - ../../../07-adr/data/data-012-second-expansion-retention-cleanup.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/second-expansion-workstreams.md
  - ../../user-flows/second-expansion-user-flows.md
  - ../../wireframes/second-expansion-wireframes.md
  - ../participation/user-submission-report.md
---

# 사용자 알림 PRD

## 1. 문서 정보

- 상태: 초안. WS-13 이우람이 소유하고 김인안이 기본 리뷰한다.
- 목적: 회원이 제보·신고 처리 상태 변화를 서비스 안에서 놓치지 않고 확인하게 한다.
- 선행 조건: 회원 식별과 제보·신고 상태 전이 계약이 구현되어야 한다.

## 2. 사용자와 문제

회원은 요청 상세를 반복 방문하지 않고도 검토 시작과 처리 결과를 알아야 한다. 초기 범위는 외부 전달 채널 없이 서비스 내 알림으로 제한해 전달 의미와 운영 복잡도를 명확히 한다.

## 3. 목표와 성공 기준

- `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `COMPLETED` 전이마다 대상 회원에게 알림이 정확히 한 건 생성된다.
- 회원은 최근 알림과 정확한 미읽음 수를 보고 개별 또는 전체 읽음 처리한다.
- 알림 생성 실패가 상태 전이만 성공한 불일치로 남지 않는다.
- 성공 지표 후보: 상태-알림 불일치 0건, 중복 생성 0건, 목록 조회 성공률.

## 4. 범위

### 포함

- 서비스 내 처리 결과 알림 생성
- 최신순 목록, 정확한 미읽음 수와 UI `99+` 표시
- 개별 읽음과 전체 읽음
- 최근 90일 또는 최신 200개 중 사용자에게 더 넓은 범위 제공
- 요청 식별자와 상태 조합의 중복 생성 방지

### 제외

- 이메일, 웹 푸시, FCM, SMS
- SSE·WebSocket 실시간 전송
- 마케팅·추천·운영 CloudWatch 알림
- 처리 결과 알림의 사용자 동의·해지 설정
- 메시지 브로커, outbox와 독립 재시도 파이프라인

## 5. 핵심 사용자 흐름

1. 관리자가 제보·신고 상태를 전이한다.
2. 시스템이 같은 DB 트랜잭션에서 해당 회원의 알림을 한 건 생성한다.
3. 회원이 헤더의 미읽음 수를 확인하고 알림 목록을 연다.
4. 알림을 선택하면 읽음 처리 후 관련 요청 상세로 이동한다.
5. 회원은 필요하면 전체 읽음을 실행한다.

## 6. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-NOTIFICATION-001 | 네 처리 상태 전이와 알림 생성을 같은 트랜잭션으로 완료한다. | FR-NOTIFICATION-001, BR-NOTIFICATION-001 |
| PR-NOTIFICATION-002 | 회원은 자신의 최신 알림과 정확한 미읽음 수를 조회한다. | FR-NOTIFICATION-002, BR-NOTIFICATION-002 |
| PR-NOTIFICATION-003 | 회원은 알림 하나 또는 현재 미읽음 전체를 읽음 처리한다. | FR-NOTIFICATION-003~004 |
| PR-NOTIFICATION-004 | 보존 범위와 탈퇴 정책을 일관되게 적용한다. | BR-NOTIFICATION-003 |
| PR-NOTIFICATION-005 | 처리 결과 알림은 필수 서비스 알림이며 별도 수신 동의 UI를 제공하지 않는다. | BR-NOTIFICATION-004 |

## 7. 정책과 예외

- 같은 요청과 같은 상태의 알림은 한 건만 존재한다.
- 알림 생성에 실패하면 해당 상태 전이도 실패하며 사용자는 재시도 가능한 결과를 받는다.
- 정확한 미읽음 수를 저장·조회하되 헤더 표시는 100 이상에서 `99+`로 표현한다.
- 사용자는 다른 회원의 알림 존재 여부나 내용을 조회·변경할 수 없다.
- 90일 이내 알림과 최신 200개 가운데 더 많은 항목이 남도록 보존한다. 회원 탈퇴 시 회원 연결과 알림을 개인정보 정책에 따라 제거한다.

## 8. 화면과 상태

- 헤더 알림 진입점: 0, 1~99, 99+, 인증 만료
- 알림 목록: 로딩, 읽음/미읽음 혼합, 빈 상태, 더 이상 없음, 오류
- 개별 선택: 낙관적 표시 여부는 UI 계약에서 정하되 실패 시 원래 읽음 상태를 보존한다.
- 전체 읽음: 확인, 처리 중, 성공, 일부 성공 없이 전체 실패
- 삭제·보존 만료된 관련 요청은 알림 본문을 표시하고 이동 불가 상태를 안내한다.

## 9. 개인정보·운영·비용 영향

- 알림은 회원 식별자와 요청 처리 정보를 포함하므로 본인 접근만 허용하고 민감한 제보·신고 원문을 본문에 복제하지 않는다.
- 외부 채널 비용은 없고 같은 저장소 트랜잭션, 보존 정리와 정합성 모니터링 비용이 발생한다.
- CloudWatch→Slack 운영 알림은 별도 운영 계약이며 이 PRD에 포함하지 않는다.

## 10. 완료 조건

- [ ] FR-NOTIFICATION-001~004와 BR-NOTIFICATION-001~004 테스트가 통과한다.
- [ ] 상태 전이와 알림의 원자성, 고유성, 회원별 접근 통제가 동시 요청에서 검증된다.
- [ ] 정확한 미읽음 수, `99+`, 개별·전체 읽음과 보존 경계가 검수된다.
- [ ] 외부 채널·수신 설정·실시간 연결이 구현 범위에 섞이지 않는다.
- [ ] WS-13 이우람 구현과 김인안 기본 리뷰가 완료된다.
- [ ] 일정과 API·데이터 계약이 승인된다.

## 11. 승인 필요 사항

- 추가 미결정 항목 없음. 목록 페이지 크기는 API 계약, 보존 작업은 데이터 계약을 따른다.
