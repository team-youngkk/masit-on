---
status: Ready
plan_date: 2026-07-29
owners:
  - 이우람
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../03-team/ownership.md
  - ../06-architecture/technology-policy.md
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../07-adr/platform/runtime-001-docker.md
  - ../07-adr/quality/obs-001-logging-observability.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
  - mvp-2day-implementation-plan.md
  - mvp-local-verification.md
---

# 맛잇온 M2 초기 운영 배포 계획

## 1. 목표 요약

- 해결 문제: MVP 구현이 로컬 Docker 통합까지 완료됐으나 실제 공개 환경에서 검증한 적이 없다.
- 목표: [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md)에 따라 1차 확장 착수 전에 최초 운영 환경을 배포하고 검증 참여자에게 제한 공개한다.
- 최종 결과: 도메인·HTTPS로 접근 가능한 단일 EC2에서 공개 탐색·상세와 관리자 인증·등록 흐름이 동작하고, 수동 복구 절차가 리허설로 검증된다.
- 후속 유지: 검증을 통과한 같은 환경을 재구축 없이 계속 운영하며 1~3차 확장의 인프라 변경을 여기에 반영한다.
- 담당: 이우람 단독. AWS 자원과 운영 비밀정보를 나눠 작업하기 어렵고 [ownership.md](../03-team/ownership.md)의 인프라·배포 최종 책임자가 이우람이다. 배포 후 기능 검증(M2-11)만 4인이 분담한다.

## 2. 요구사항 분석

### 적용되는 비기능 요구사항

| 요구사항 | M2에서 만족해야 하는 것 |
|---|---|
| [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분) | `/internal/**`을 인터넷 Nginx 경로에서 차단하고 EC2 내부에서만 호출 |
| [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구) | 단일 인스턴스 장애 후 수동 복구와 핵심 조회 확인 절차 |
| [NFR-DEPLOYMENT-002](../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증) | 배포 전후 자동 테스트·빌드·상태 확인을 운영 환경에서 재수행 |
| [NFR-DEPLOYMENT-003](../01-requirements/non-functional-requirements.md#nfr-deployment-003-버전-추적과-복구-절차) | 이전 정상 버전 식별과 복구 리허설, 스키마 변경과 버전의 대응 관계 |
| [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-초기-운영-배포-복잡도-제한) | 수동 승인 배포 허용, 무중단 배포는 요구하지 않음 |
| [NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | 운영 비밀정보를 소스와 분리 |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)~003 | CloudWatch 수집, 로그 14일 보관, 민감정보 미노출 |

### 적용되는 ADR

- [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md) — 배포 순서와 활성화 범위
- [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md) — Nginx 경로 경계와 인증 matcher
- [ADR-SEC-001](../07-adr/security/sec-001-secrets-workload-identity.md) — Parameter Store SecureString, KMS, EC2 IAM Role, GitHub Actions OIDC
- [ADR-OBS-001](../07-adr/quality/obs-001-logging-observability.md) — CloudWatch 수집, 로그 14일, 스냅샷, Slack 알림
- [ADR-CI-001](../07-adr/platform/ci-001-github-actions-quality-gate.md) — 품질 게이트 유지, ECR push와 EC2 배포 활성화
- [ADR-RUNTIME-001](../07-adr/platform/runtime-001-docker.md) — ECR digest, CloudWatch Agent, 배포 후 Smoke Test

### 범위 제외

- ALB·ASG·Blue-Green 무중단 배포 — 3차 확장 이후 배포 고도화 단계에서 검토하며 비용·일정 영향 검토가 선행 조건이다(ADR-DEPLOY-002 3.1절).
- 1~3차 확장 기능 — M2는 현재 MVP 범위를 그대로 배포한다.
- 다중 리전·읽기 복제본·자동 복구.
- 일반 사용자 대상 정식 공개. M2는 검증 참여자 제한 공개까지다.

## 3. 확정 사항

| 항목 | 값 | 근거 |
|---|---|---|
| AWS 리전 | `ap-northeast-2` (서울) | 국내 사용자 대상, 지연·요금 |
| 토폴로지 | 단일 EC2 (Nginx + Next.js + Spring Boot) | 기술 정책 13절 |
| 데이터베이스 | RDS PostgreSQL 17.10 | ADR-DATA-001, 기술 정책 |
| 이미지 저장소 | ECR | ADR-DEPLOY-002 |
| CI 경로 | GitHub Actions → ECR → EC2, 수동 승인 | RV-NFR-012 |
| 백업 | 일 1회 자동 스냅샷, 7일 보관, RPO 최대 24시간 | RV-NFR-010 |
| 로그 | CloudWatch, 14일 보관 | RV-NFR-009 |
| 알림 | CloudWatch 알람 → Slack Webhook, 담당자 1명 | RV-NFR-013 |
| 월 인프라 예산 목표 | 150,000원 | ADR 추적표 |
| 목표 완료일 | 2026-07-31 | [M2 마일스톤](https://github.com/team-youngkk/masit-on/milestone/2) |

목표 완료일까지 여유가 크지 않다. 도메인 구입과 DNS 전파, RDS 생성처럼 대기 시간이 고정된 Task가 있으므로 M2-01을 먼저 끝내고 M2-02·M2-03을 같은 날 착수한다.

## 4. 확인 필요 항목

계획 수립 시점에 결정되지 않았고 **임의로 확정하지 않는다.** 각 항목은 표시된 Task를 시작하기 전에 결정해야 한다.

| 항목 | 필요 시점 | 비고 |
|---|---|---|
| 도메인명 | M2-02 | 미확보. 구입 후 확정 |
| HTTPS 인증서 발급 방식 | M2-07 | ACM 인증서는 ALB·CloudFront·API Gateway에만 연결되고 EC2에 직접 설치할 수 없다. ALB를 배포 고도화로 미뤘으므로 단일 EC2에서는 Let's Encrypt(certbot)가 사실상 유일한 선택이다. **새 외부 서비스 도입이므로 별도 ADR이 필요하다.** |
| 검증 참여자 제한 공개 방식 | M2-10 | ADR-DEPLOY-002는 "검증 참여자에게만 제한 공개"만 규정하고 방식을 정하지 않았다. Nginx Basic Auth, 보안 그룹 IP allowlist 중 선택이 필요하다 |
| EC2 인스턴스 타입과 RDS 인스턴스 클래스 | M2-03, M2-04 | 예산 목표 150,000원 대비 산정이 필요하다. 단일 인스턴스에 Nginx·Next.js·Spring Boot가 함께 올라가므로 메모리 여유를 확인해야 한다 |

## 5. Task 분해

### M2-01 AWS 기반 준비

- 작업: 리전 `ap-northeast-2` 고정, 작업용 IAM 사용자·역할 생성, 루트 계정 MFA 활성화, 월 예산 알림(150,000원) 설정
- 선행: 없음
- 완료 조건: 장기 AWS 액세스 키를 저장소·로컬 설정에 두지 않고, 예산 초과 알림이 실제로 발송된다
- 근거: ADR-SEC-001

### M2-02 도메인 확보와 DNS 설정

- 작업: 도메인 구입, DNS 호스팅 영역 생성, EC2 Elastic IP를 가리키는 A 레코드 등록, 전파 확인
- 선행: M2-01, M2-03(Elastic IP 확보 후 A 레코드 등록)
- 완료 조건: 도메인이 Elastic IP로 해석된다
- 주의: **DNS 전파가 끝나야 M2-07의 인증서 발급이 가능하다.** 전파 대기가 최대 수 시간 걸릴 수 있으므로 M2-03 직후 착수한다

### M2-03 네트워크와 EC2 프로비저닝

- 작업: 보안 그룹 생성(인터넷 인바운드는 80·443만, 22는 작업자 IP로 제한), EC2 인스턴스 생성, Elastic IP 할당, EC2 IAM Role 부여
- 선행: M2-01
- 완료 조건: 22 포트가 전체 공개되지 않고, EC2가 IAM Role로 Parameter Store·ECR·CloudWatch에 접근한다
- 근거: ADR-SEC-001

### M2-04 RDS PostgreSQL 프로비저닝

- 작업: 서브넷 그룹과 RDS 보안 그룹 생성(EC2 보안 그룹에서만 5432 허용), PostgreSQL 17.10 인스턴스 생성, 자동 스냅샷 일 1회·7일 보관 설정
- 선행: M2-03
- 완료 조건: RDS가 인터넷에서 직접 접근되지 않고 EC2에서만 연결되며, 스냅샷 일정이 활성화된다
- 근거: RV-NFR-010

### M2-05 ECR 리포지토리와 이미지 push 자동화

- 작업: 백엔드·프론트엔드 ECR 리포지토리 생성, GitHub Actions OIDC용 IAM Role 생성, 워크플로에 이미지 빌드·push 단계 추가
- 선행: M2-01
- 완료 조건: GitHub Actions가 장기 키 없이 OIDC로 ECR에 push하고, 이미지가 digest로 식별된다
- 근거: ADR-CI-001, ADR-SEC-001, ADR-RUNTIME-001

### M2-06 운영 비밀정보 구성

- 작업: JWT RS256 키쌍, RDS 자격 증명, Kakao·YouTube 운영 API 키를 Parameter Store SecureString과 KMS로 저장
- 선행: M2-01, M2-04
- 완료 조건: 비밀값이 소스·이미지·환경 파일에 남지 않고 EC2 IAM Role로만 조회된다
- 근거: ADR-SEC-001, NFR-SECURITY-003

### M2-07 Nginx 리버스 프록시와 HTTPS

- 작업: Nginx 설치·설정(`/api/**` → Spring Boot, 나머지 외부 경로 → Next.js, `/internal/**` 차단), 인증서 발급과 자동 갱신 구성, HTTP → HTTPS 리다이렉트
- 선행: M2-02(DNS 전파 완료), M2-03, 인증서 발급 방식 ADR
- 완료 조건: 인터넷에서 `/internal/health/live`가 차단되고, HTTPS로 프론트엔드와 `/api/**`가 모두 응답하며, 인증서 자동 갱신이 동작한다
- 근거: ADR-WEB-003, NFR-AVAILABILITY-001

### M2-08 애플리케이션 배포

- 작업: EC2에서 ECR 이미지 pull·실행, Parameter Store에서 설정 주입, 초기 스키마 Flyway 마이그레이션을 빈 RDS에 적용, 관리자 계정 생성
- 주의: 초기 스키마를 단일 baseline으로 통합하는 작업이 별도로 진행 중이다. 이 Task는 착수 시점에 저장소에 있는 마이그레이션 파일을 적용하며, 적용된 버전을 이미지 digest와 함께 기록한다
- 선행: M2-04, M2-05, M2-06, M2-07
- 완료 조건: `/internal/health/ready`와 `/internal/health/dependencies`가 EC2 내부에서 정상이고, 적용된 마이그레이션 버전과 이미지 digest가 기록된다
- 근거: NFR-DEPLOYMENT-002, NFR-DEPLOYMENT-003

### M2-09 CloudWatch 로그·지표·알람

- 작업: CloudWatch Agent 설치, 로그 그룹 14일 보관 설정, 알람 4종 구성(5분 구간 서버 오류율 5% 이상, 5분 구간 p95 2초 초과, 상태 확인 연속 3회 실패, 저장소 연결 실패 연속 3회), Slack Webhook 연결
- 선행: M2-08
- 완료 조건: 시험 알람이 Slack에 실제로 도달하고, 로그에 비밀번호·JWT·API 키 원문이 없다
- 근거: ADR-OBS-001, RV-NFR-009, RV-NFR-013, NFR-OBSERVABILITY-003

### M2-10 검증 참여자 제한 공개 설정

- 작업: 결정된 방식으로 접근을 제한하고 검증 참여자에게 접근 정보를 전달
- 선행: M2-07, 제한 공개 방식 결정
- 완료 조건: 검증 참여자만 접근하고 그 외 접근이 차단된다

### M2-11 배포 후 기능 검증

- 작업: 공개 맛집 목록·검색·필터, 맛집 상세와 관련 영상, 관리자 로그인과 4종 등록 흐름을 운영 환경에서 확인. `/internal/**` 외부 차단 재확인
- 선행: M2-08, M2-10
- 담당: WS 담당자별 분담 — 양성훈(목록·검색·필터), 박진영(상세·콘텐츠), 이우람(유튜버 탐색), 김인안(관리자 인증·등록)
- 완료 조건: 각 WS 담당자가 자신의 인수 흐름 통과를 확인하고 결과를 기록한다
- 근거: NFR-DEPLOYMENT-002

### M2-12 복구 리허설

- 작업: EC2 인스턴스 재기동·교체 후 핵심 조회 확인, 직전 이미지로 롤백, RDS 스냅샷 복구 시험
- 선행: M2-11
- 완료 조건: 문서화된 절차만으로 복구가 성공하고, 복구 소요 시간과 RPO 24시간 충족 여부가 기록된다
- 근거: NFR-AVAILABILITY-002, NFR-DEPLOYMENT-003, RV-NFR-010

## 6. 선행 관계

```
M2-01 AWS 기반
  ├── M2-03 네트워크·EC2 ── M2-04 RDS ─┐
  │        └── M2-02 도메인·DNS ──┐     │
  ├── M2-05 ECR·OIDC ─────────────│─────│─┐
  └── M2-06 비밀정보 (M2-04 필요) ─│─────┘ │
                                  │        │
                       M2-07 Nginx·HTTPS ──┤
                                  │        │
                                  ├── M2-08 애플리케이션 배포
                                  │        ├── M2-09 CloudWatch
                       M2-10 제한 공개 ────┤
                                           └── M2-11 기능 검증 ── M2-12 복구 리허설
```

순서를 어기면 막히는 의존은 다음 둘이다.

- **M2-02 → M2-07**: DNS 전파가 끝나지 않으면 도메인 검증 방식의 인증서 발급이 실패한다.
- **M2-03 → M2-02**: Elastic IP가 있어야 A 레코드를 등록할 수 있다.

## 7. 병렬 가능 범위

이우람 단독 작업이므로 실제 병렬 실행은 없다. 다만 대기 시간이 있는 Task는 겹쳐서 진행할 수 있다.

- M2-02의 DNS 전파 대기 중에 M2-04·M2-05·M2-06을 진행한다.
- M2-04의 RDS 생성 대기 중에 M2-05를 진행한다.

## 8. 검증 계획

| 대상 | 방법 |
|---|---|
| 라우팅 경계 | 인터넷에서 `/internal/health/live` 접근 차단 확인, `/api/**`와 화면 경로 응답 확인 |
| 인증 경계 | 무인증 공개 GET 3종 성공, 무인증 `/api/admin/**` 거부, JWT 발급 후 등록 성공 |
| 데이터 | Flyway 적용 버전과 이미지 digest 대조 |
| 관측성 | 시험 알람 Slack 도달, 로그 표본의 민감정보 검사 |
| 복구 | 인스턴스 교체·이미지 롤백·스냅샷 복구 리허설 |
| 품질 게이트 | 배포 후보 커밋의 GitHub Actions 빌드·테스트 통과 |

## 9. 위험 요소

| 위험 | 가능성 | 영향 | 대응 |
|---|---:|---:|---|
| RDS 요금으로 월 예산 목표 150,000원 초과 | 높음 | 높음 | M2-01에서 예산 알림을 먼저 설정하고 M2-04 전에 인스턴스 클래스별 요금을 산정 |
| 단일 EC2 메모리 부족으로 컨테이너 종료 | 중간 | 높음 | M2-03 전에 인스턴스 타입 산정, M2-08에서 메모리 사용량 확인 |
| DNS 전파 지연으로 인증서 발급 대기 | 중간 | 중간 | M2-03 직후 M2-02를 착수하고 대기 중 다른 Task 진행 |
| 이우람 단독 작업이라 병렬화 불가 | 높음 | 중간 | 대기 시간에 다른 Task를 겹쳐 진행하고, 기능 검증만 4인 분담 |
| 운영 외부 API 키 발급·쿼터 제한 | 중간 | 중간 | M2-06에서 Kakao·YouTube 운영 키 쿼터를 미리 확인 |
| 로컬과 운영의 설정 차이로 기동 실패 | 중간 | 중간 | 설정 계층 규칙을 따르고 운영 프로파일 값을 Parameter Store로만 주입 |

## 10. 완료 정의

- 도메인과 HTTPS로 공개 탐색·상세, 관리자 인증·등록 흐름이 동작한다.
- `/internal/**`이 인터넷에서 차단되고 EC2 내부에서만 응답한다.
- 운영 비밀정보가 소스·이미지·환경 파일에 없고 Parameter Store와 IAM Role로만 조회된다.
- CloudWatch 로그 14일 보관과 알람 4종이 활성화되고 시험 알람이 Slack에 도달한다.
- RDS 자동 스냅샷이 일 1회 생성되고 7일 보관된다.
- 복구 리허설이 문서화된 절차만으로 성공하고 결과가 기록된다.
- 4개 WS 담당자가 각자 인수 흐름 통과를 확인한다.
- 실제 월 인프라 비용 실측치가 기록되고 예산 목표와 대조된다.

## 11. 가장 먼저 착수할 Task

**M2-01 AWS 기반 준비.** 다른 모든 Task가 IAM Role과 리전에 의존하고, 예산 알림을 먼저 걸어두지 않으면 RDS·EC2 생성 이후 비용 초과를 늦게 발견한다.

착수 전에 4절 확인 필요 항목 중 EC2 인스턴스 타입과 RDS 인스턴스 클래스 산정을 M2-01에 포함해 진행한다.
