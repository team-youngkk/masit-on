---
related_documents:
  - ../README.md
  - ../../06-architecture/technology-policy.md
  - lang-001-java-21-runtime.md
  - build-001-gradle-groovy.md
  - frame-001-spring-boot.md
  - web-001-frontend-platform.md
  - web-002-data-state.md
  - web-003-routing-boundary.md
  - web-005-application-port-binding.md
  - runtime-001-docker.md
  - ci-001-github-actions-quality-gate.md
  - deploy-003-validation-cookie-session.md
  - deploy-004-public-api-validation-gate-boundary.md
---

# 플랫폼 ADR

언어·빌드·프레임워크·프론트엔드·실행 환경·CI 결정을 관리한다.

| ADR | 제목 |
|---|---|
| [ADR-LANG-001](lang-001-java-21-runtime.md) | Java 21 런타임 기준 |
| [ADR-BUILD-001](build-001-gradle-groovy.md) | Gradle과 Groovy DSL 빌드 체계 |
| [ADR-FRAME-001](frame-001-spring-boot.md) | Spring Boot 애플리케이션 기준 |
| [ADR-WEB-001](web-001-frontend-platform.md) | 프론트엔드 런타임과 프레임워크 기준 |
| [ADR-WEB-002](web-002-data-state.md) | 프론트엔드 데이터와 상태 책임 분리 |
| [ADR-WEB-003](web-003-routing-boundary.md) | 웹 화면·API·운영 경로 경계 |
| [ADR-WEB-005](web-005-application-port-binding.md) | 운영 애플리케이션 포트 loopback 바인딩 |
| [ADR-RUNTIME-001](runtime-001-docker.md) | Docker 기반 실행 환경 |
| [ADR-CI-001](ci-001-github-actions-quality-gate.md) | GitHub Actions 빌드·테스트 품질 게이트 |
| [ADR-DEPLOY-003](deploy-003-validation-cookie-session.md) | 검증 참여자 제한 공개 쿠키 세션(Superseded) |
| [ADR-DEPLOY-004](deploy-004-public-api-validation-gate-boundary.md) | 비관리자 공개 API 검증 세션 gate 경계 |
| [ADR-DEPLOY-005](deploy-005-asg-blue-green-rollout.md) | ASG 기반 Blue-Green 운영 배포 (Accepted) |
