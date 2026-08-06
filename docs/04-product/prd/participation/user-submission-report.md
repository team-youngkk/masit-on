---
id: PRD-PARTICIPATION-001
title: 사용자 제보와 신고
status: approved
workstream: WS-12
owner: 김인안
reviewers:
  - 이우람
related_requirements:
  - FR-SUBMISSION-001
  - FR-SUBMISSION-002
  - FR-SUBMISSION-003
  - FR-REPORT-001
  - FR-REPORT-002
  - FR-REPORT-003
related_business_rules:
  - BR-SUBMISSION-001
  - BR-SUBMISSION-002
  - BR-SUBMISSION-003
  - BR-SUBMISSION-004
  - BR-REPORT-001
  - BR-REPORT-002
  - BR-REPORT-003
  - BR-REPORT-004
related_nfr:
  - NFR-SECURITY-006
  - NFR-INTEGRITY-005
  - NFR-OBSERVABILITY-004
  - NFR-PRIVACY-005
  - NFR-TEST-005
related_documents:
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/second-expansion-workstreams.md
  - ../../user-flows/second-expansion-user-flows.md
  - ../../wireframes/second-expansion-wireframes.md
  - ../notification/user-notification.md
  - ../../../05-specs/api/participation/submission-report-api.md
  - ../../../05-specs/data/second-expansion-data-contract.md
  - ../../../07-adr/integration/notify-002-in-app-notification-reliability.md
  - ../../../07-adr/data/data-012-second-expansion-retention-cleanup.md
---

# 사용자 제보와 신고 PRD

## 1. 문서 정보

- 상태: 초안. WS-12 김인안이 소유하고 이우람이 기본 리뷰한다.
- 목적: 회원이 신규 데이터 후보와 기존 정보 문제를 구분해 전달하고 처리 결과를 추적하게 한다.
- 선행 조건: 회원·관리자 인증, 관리자 데이터 처리 흐름과 공개 상태가 구현되어야 한다.

## 2. 용어와 문제

- 제보: 신규 맛집·유튜버·영상·방문 관계의 등록 후보를 제안한다.
- 신고: 기존 정보의 오류·폐업·잘못된 영상·부적절한 콘텐츠를 알린다.

두 요청은 입력 대상과 관리자 조치가 다르므로 별도 모델과 API로 관리하되, 공통 상태와 일일 제한은 일관되게 적용한다.

## 3. 목표와 성공 기준

- 로그인 회원이 설명과 선택적 근거 URL로 제보 또는 신고를 등록하고 자신의 요청을 조회한다.
- 관리자가 요청을 검토하고 사유와 함께 허용된 상태로 전이한다.
- 승인과 실제 데이터 등록·수정·숨김을 구분해 잘못된 자동 반영을 막는다.
- 성공 지표 후보: 유효 접수율, 처리 리드타임, 잘못된 자동 공개 0건, 제한 우회 0건.

## 4. 범위

### 포함

- 회원 전용 제보·신고 등록과 본인 목록·상세 조회
- 설명과 선택적 증거 URL, 서버 측 입력 검증
- 공통 상태 `RECEIVED → IN_REVIEW → ACCEPTED → COMPLETED` 또는 `IN_REVIEW → REJECTED`
- 제보·신고 합산 회원당 하루 5건(Asia/Seoul)
- 동일 회원·대상·유형의 열린 요청 중복 차단
- 관리자 검토, 상태 변경 사유와 감사 이력

### 제외

- 파일 첨부, 익명 요청, 요청 담당자 배정
- 신고 접수만으로 자동 숨김 또는 자동 데이터 수정
- 외부 티켓·메시징 시스템 연동

## 5. 사용자·관리자 흐름

1. 회원이 제보 또는 신고를 선택하고 대상·설명·근거 URL을 입력한다.
2. 시스템이 인증, 입력, 중복과 일일 제한을 확인해 `RECEIVED`로 접수한다.
3. 회원은 자신의 목록과 상세에서 현재 상태와 사유를 확인한다.
4. 관리자는 `IN_REVIEW`로 전환하고 내용을 검토한다.
5. 반려하면 사유와 함께 `REJECTED`, 승인하면 `ACCEPTED`로 전환한다.
6. 실제 데이터 등록·정정·필요 시 수동 숨김을 완료한 뒤 `COMPLETED`로 전환한다.

## 6. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-PARTICIPATION-001 | 회원은 신규 데이터 후보를 제보하고 자신의 제보를 조회한다. | FR-SUBMISSION-001~002 |
| PR-PARTICIPATION-002 | 회원은 기존 데이터 문제를 신고하고 자신의 신고를 조회한다. | FR-REPORT-001~002 |
| PR-PARTICIPATION-003 | 관리자는 제보와 신고를 구분해 조회·검토하고 유효한 상태만 적용한다. | FR-SUBMISSION-003, FR-REPORT-003 |
| PR-PARTICIPATION-004 | 중복과 일일 5건 제한을 제보·신고 합산으로 적용한다. | BR-SUBMISSION-002, BR-REPORT-002 |
| PR-PARTICIPATION-005 | 승인만으로 원본 데이터를 만들거나 바꾸거나 숨기지 않는다. | BR-SUBMISSION-003, BR-REPORT-003 |

## 7. 정책과 예외

- `RECEIVED`에서는 `IN_REVIEW`만, `IN_REVIEW`에서는 `ACCEPTED` 또는 `REJECTED`만, `ACCEPTED`에서는 `COMPLETED`만 허용한다.
- 반려와 처리 완료에는 회원에게 표시할 사유 또는 결과 요약을 기록한다.
- 신고는 자동 숨김하지 않는다. 긴급 사안은 관리자가 기존 공개 상태 관리 기능으로 수동 숨김한다.
- 종료 상태는 1년 보존한 뒤 회원 식별자를 제거한다. 탈퇴 시에는 개인정보 정책에 따라 식별 연결을 제거한다.
- 악성 HTML·스크립트, 비정상 URL, 과도한 길이를 서버에서 거부하거나 안전하게 처리한다.

## 8. 화면과 상태

- 회원 요청 목록: 제보/신고 탭, 상태 필터, 빈 상태, 보존 안내
- 등록 폼: 대상 유형, 대상 식별 또는 후보 정보, 설명, 근거 URL, 검증·중복·일일 제한 오류
- 요청 상세: 타임라인, 상태, 관리자 사유, 관련 데이터 이동
- 관리자 큐: 유형·상태 필터, 상세 검토, 상태 변경 확인, 실제 조치 확인

## 9. 개인정보·운영·비용 영향

- 자유 입력에 개인정보가 포함될 수 있으므로 최소 수집 안내, 접근 통제, 로그 마스킹과 삭제 절차가 필요하다.
- 관리자는 접수 분류, 사실 확인, 데이터 등록·정정과 상태 갱신을 수행한다. 자동 담당자 배정은 없다.
- 외부 연동은 없고 관리자 처리량, 감사 로그와 남용 방어 구현 비용이 발생한다.

## 10. 완료 조건

- [ ] FR-SUBMISSION-001~003, FR-REPORT-001~003과 관련 BR 테스트가 통과한다.
- [ ] 권한, 상태 전이, 중복과 Asia/Seoul 일일 합산 제한이 동시 요청에서도 일관된다.
- [ ] 승인과 실제 데이터 조치가 분리되고 신고 자동 숨김이 발생하지 않는다.
- [ ] 감사 이력, 입력 보안, 1년 보존·식별 제거와 탈퇴 처리가 검증된다.
- [ ] 각 상태 전이가 알림 PRD의 원자적 생성 계약과 연결된다.
- [ ] WS-12 김인안 구현과 이우람 기본 리뷰가 완료된다.
- [ ] 일정과 API·데이터 계약이 승인된다.

## 11. 승인 필요 사항

- 관리자 처리 SLA

대상 유형별 필수 입력과 중복 판정 기준은 [사용자 제보·신고 API](../../../05-specs/api/participation/submission-report-api.md#2-회원-접수)에서 확정한다.
1년 경과 식별 제거의 실행 주기와 운영 책임은 [2차 확장 데이터 계약](../../../05-specs/data/second-expansion-data-contract.md#91-보존-작업-운영-계약)에서 확정한다.
