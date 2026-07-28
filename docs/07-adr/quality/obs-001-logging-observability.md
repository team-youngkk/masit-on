---
id: ADR-OBS-001
title: 애플리케이션 로그와 운영 관측 기준
status: Accepted
decision_date: 2026-07-27
owners:
  - 이우람
related_requirements:
  - NFR-AVAILABILITY-001
  - NFR-OBSERVABILITY-001
  - NFR-OBSERVABILITY-002
  - NFR-OBSERVABILITY-003
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../05-specs/api/common/error-contract.md
  - ../../06-architecture/technology-policy.md
  - ../security/auth-001-spring-security-jwt.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../../02-analysis/mvp-workstreams.md
  - ../integration/ext-001-reference-verification.md
  - ../../03-team/roles.md
  - ../adr-traceability.md
  - ../platform/frame-001-spring-boot.md
  - ../platform/web-003-routing-boundary.md
  - ../platform/deploy-002-validation-deployment-before-expansion.md
  - ../adr-backlog.md
supersedes: []
superseded_by: null
---

# ADR-OBS-001 애플리케이션 로그와 운영 관측 기준

## 1. 상태

Accepted

## 2. 결정 요약

전 단계에서 SLF4J·Logback과 Spring Boot Actuator로 로그·상태를 검증한다. Amazon CloudWatch, 로그 보관 14일, DB 스냅샷과 운영 알림은 M2 초기 운영 배포부터 적용한다.

## 3. 배경

[ADR-DEPLOY-002](../platform/deploy-002-validation-deployment-before-expansion.md)에 따라 MVP 구현은 로컬에서 검증하고 M2 초기 운영 배포부터 운영 환경을 함께 검증한다. 모든 단계에서 관리자 등록의 Kakao·YouTube 외부 호출, 인증 실패, 저장소 오류를 구분할 수 있어야 하므로 구조화 로그와 헬스체크는 구현한다. AWS 수집·알림은 초기 운영 배포부터 활성화한다.

이우람은 [WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)(유튜버 기반 탐색)의 최종 책임자이면서 동시에 인프라·배포 조율 책임을 겸한다([roles.md](../../03-team/roles.md) 4장 "아키텍처·배포 책임이 기능 개발 일정을 방해할 수 있다"는 주요 리스크로 이미 명시). 따라서 이 ADR이 정하는 관측 기준은 이우람이 매 배포마다 직접 붙어서 해석해야 하는 복잡한 도구가 아니라, 4명 모두가 자신의 워크스트림 오류를 스스로 진단할 수 있을 만큼 단순해야 한다.

## 4. 결정 문제

애플리케이션과 AWS 운영 환경의 로그·상태·지표 기준을 어떤 도구로 통일할 것인가. 제약 조건은 초기 월 인프라 예산 목표 150,000원([adr-traceability.md](../adr-traceability.md)), 이미 확정된 단일 EC2 배포 토폴로지([technology-policy.md](../../06-architecture/technology-policy.md) 13절), 그리고 4명의 백엔드 개발자가 이해·운영할 수 없는 불필요한 분산 구성요소를 추가하지 않아야 한다는 [NFR-MAINTAINABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도)이다.

## 5. 고려한 선택지

- **SLF4J·Logback + Actuator + CloudWatch**: Spring Boot 4.1.0에 이미 포함된 로깅·상태 확인 도구와, 이미 결정된 단일 EC2 AWS 배포에 네이티브한 수집·조회 도구를 그대로 사용한다.
- **파일 로그만 사용**: 로그를 인스턴스 로컬 파일로만 남긴다. 단일 EC2 인스턴스가 장애로 교체·재기동되는 수동 복구 절차([NFR-AVAILABILITY-002](../../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구))에서는 인스턴스가 사라지면 로컬 파일도 함께 유실될 위험이 있고, 14일 보관·검색이라는 운영 정책을 충족하기 어렵다.
- **별도 관측 플랫폼 선제 도입**: Datadog·ELK 같은 전용 관측 플랫폼을 새로 도입한다. 이는 초기 월 150,000원 예산 목표를 넘어서는 구독형 비용을 추가로 발생시킬 가능성이 높고, 이미 결정된 단일 EC2/RDS 배포 토폴로지([technology-policy.md](../../06-architecture/technology-policy.md) 13절)에 없는 새로운 운영 구성요소를 더하는 것이므로 [NFR-MAINTAINABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도)(4명이 운영할 수 없는 불필요한 분산 구성요소 금지)에 반한다.

## 6. 결정

단계별 로컬 검증에는 SLF4J·Logback과 Actuator를 사용한다. 초기 운영 배포부터 CloudWatch 수집, 로그 14일 보관, DB 일 1회 스냅샷·7일 보관과 Slack 알림을 활성화한다.

이 결정은 다음 세 층위로 나뉜다. 첫째, 애플리케이션 로그(SLF4J·Logback)는 요청 상관관계와 오류 분류를 남긴다. 둘째, 상태·지표(Actuator)는 [ADR-WEB-003](../platform/web-003-routing-boundary.md)의 `/internal/health/live`, `/internal/health/ready`, `/internal/health/dependencies`에서 프로세스와 PostgreSQL·Redis 연결 가능 여부를 구분한다([NFR-AVAILABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)). 셋째, 운영 수집·조회(CloudWatch)는 위 두 층위의 산출물을 모아 14일간 검색 가능하게 보관하고, 정의된 지표가 임계값을 넘으면 알람을 발생시킨다. 이 세 층위 중 하나만 갖추는 것으로는 단일 EC2 수동 복구 절차에서 필요한 진단이 완결되지 않는다.

## 7. 선택 근거

- SLF4J·Actuator는 이미 채택된 Spring Boot 4.1.0 스택의 일부이므로([ADR-FRAME-001](../platform/frame-001-spring-boot.md)) 별도 의존성을 추가하지 않는다.
- CloudWatch는 이미 결정된 단일 EC2 배포 토폴로지([technology-policy.md](../../06-architecture/technology-policy.md) 13절)에 네이티브하게 연동되므로, 별도 에이전트·플랫폼 통합 작업 없이 로그·지표를 수집할 수 있다.
- CloudWatch의 사용량 기반 과금 구조는 정상 부하 50명·20 RPS와 초기 기준 데이터 규모에서 고정 구독료를 요구하는 전용 플랫폼보다 초기 월 150,000원 예산 목표에 맞추기 쉽다.
- 알림 채널을 Slack 하나로 정한 것은 팀의 상시 소통 채널이 Slack뿐이고 운영 이메일 수신·당직 체계가 없기 때문이다. 담당자 1명 통지에 수신 체계가 없는 채널을 추가하면 도달을 검증할 수 없으므로 Slack Webhook만 활성화한다. 이메일 통지는 수신 체계를 갖춘 뒤 재검토한다([RV-NFR-013](../../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준), 2026-07-28 결정).

## 8. 트레이드오프

- CloudWatch 종속: 로그·지표 조회 방식이 CloudWatch 쿼리 문법과 대시보드에 묶이므로 다른 관측 플랫폼으로의 이식성은 낮아진다. 이는 AWS 기반 배포를 이미 확정한 상태에서 수용 가능한 결합으로 본다.
- 비용 상한: CloudWatch는 로그량과 지표 수에 따라 과금되므로, 로그량이 예상보다 늘어나면 150,000원 예산 목표를 초과할 위험이 있다. 이는 로그 보관 14일([RV-NFR-009](../../01-requirements/non-functional-requirements.md#rv-nfr-009-로그-보관-기간))로 누적량을 제한하고, 불필요한 요청·응답 본문 로깅을 금지(11장)하는 방식으로 완화한다.
- 단일 수신자 구조: [RV-NFR-013](../../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준)에 따라 운영 알림은 담당자 1명에게만 전달된다. 이 담당자가 부재중이면 장애 인지가 지연될 위험이 있다. 4인 팀 규모에서 온콜 로테이션을 구성하는 것은 이 ADR의 범위 밖이며, 현재는 이 위험을 완화 장치 없이 수용된 리스크로 남겨두고 향후 팀 결정이 필요한 항목으로 취급한다.

## 9. 적용 범위

구조화 로그와 Actuator는 모든 단계의 API·인증·PostgreSQL·Redis·외부 연동에 적용한다. RDS·EC2·CloudWatch 운영 구성은 초기 운영 배포부터 적용한다.

## 10. 강제 규칙

- 모든 오류 응답과 대응 로그에 서버가 생성한 요청 상관관계 식별자를 포함한다([NFR-OBSERVABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)).
- 관리자 등록 실패, 인증 실패, 권한 실패, 데이터 저장소 오류, 외부 서비스 오류(Kakao·YouTube)를 서로 다른 오류 분류로 기록한다.
- 비밀번호, 관리자 JWT Access·Refresh Token, 외부 서비스 API 키(Kakao·YouTube), 개인정보를 로그에서 마스킹한다([NFR-PRIVACY-002](../../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호)).

## 11. 금지 사항

- 민감정보 원문 기록: 비밀번호·토큰·API 키 원문을 남기면 [NFR-SECURITY-003](../../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호)·[NFR-PRIVACY-002](../../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호)를 직접 위반한다.
- 불필요한 요청·응답 본문 로깅: 로그량이 늘어나면 14일 보관 기준에서도 CloudWatch 수집·저장 비용이 커져 150,000원 예산 목표를 압박한다.
- 운영 실측 없는 임계값 완화: 초기 알람 기준은 확정값으로 적용하고 운영 실측 없이 임의로 완화하지 않는다.

## 12. 구현 및 운영 영향

- 구조화 로그에 요청 상관관계 식별자와 오류 분류를 포함한다.
- Actuator Health Group을 `/internal/health/live`는 프로세스만, `/internal/health/ready`는 PostgreSQL, `/internal/health/dependencies`는 PostgreSQL·Redis를 개별 구분하도록 구성한다. 세 경로는 인터넷 Nginx에서 차단한다([NFR-AVAILABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)).
- 초기 운영 배포에서 CloudWatch 접근 권한, 로그 보관, RDS 스냅샷과 운영 알림을 구성한다.
- 로그 레벨은 개발·시험·운영 환경별로 조정 가능하게 구성하고, 정상적인 빈 조회 결과(예: 검색 결과 없음)는 오류로 기록하지 않는다([NFR-OBSERVABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단)).
- 정상 부하 50명·20 RPS, 최대 부하 200명·80 RPS와 초기 기준 데이터 규모를 사용해 로그·지표 수집량을 산정하고 운영 초기 실측치로 비용을 재확인한다.

## 13. 검증 방법

- 상관관계 식별자가 모든 오류 응답과 대응 로그에 실제로 포함되는지 표본 검사한다.
- 애플리케이션 프로세스 이상과 DB·Redis 연결 단절을 헬스체크 결과로 구분할 수 있는지 테스트한다([NFR-AVAILABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)).
- 비밀번호·JWT·API 키 원문이 표본 로그에 노출되지 않는지 자동·수동 검사한다([NFR-OBSERVABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단)).
- 로그가 14일 경과 후 실제로 폐기되는지, PostgreSQL 스냅샷이 일 1회 생성되고 7일 보관되는지 설정을 확인하고, RPO 24시간 이내로 복구할 수 있는지 복구 리허설로 검증한다([RV-NFR-010](../../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위)).
- CloudWatch 알람이 오류율·응답 지연·헬스체크 실패·저장소 장애 지표에 실제로 연결되어 Slack으로 통지되는지 시험 알람을 발생시켜 확인한다([RV-NFR-013](../../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준)).
- 배포 후 맛집 목록·상세 등 핵심 API가 정상 동작하는지 확인한다([NFR-DEPLOYMENT-002](../../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증)).

## 14. 재검토 조건

실측 로그·지표 비용이 150,000원 예산 목표를 넘어설 것으로 판단될 때, SLO 요구가 CloudWatch 단일 대시보드로 충분히 표현되지 않을 때, 또는 초기 알림 임계값이 반복적으로 오탐·미탐을 만들 때 재검토한다.

## 15. 관련 문서

- [NFR](../../01-requirements/non-functional-requirements.md)
- [ADR Backlog](../adr-backlog.md#5-범위-충돌-검토)
- [배포 토폴로지 정책](../../06-architecture/technology-policy.md)
