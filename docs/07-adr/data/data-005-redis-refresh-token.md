---
id: ADR-DATA-005
title: Redis 8.8 관리자 Refresh Token 저장소
status: Accepted
decision_date: 2026-07-24
owners:
  - 김인안
  - 이우람
related_requirements:
  - FR-ADMIN-001
  - NFR-SECURITY-001
  - NFR-RELIABILITY-002
  - NFR-PRIVACY-002
related_documents:
  - ../../05-specs/api/admin/authentication-api.md
  - ../../05-specs/data/entity-definitions.md
  - ../security/auth-001-spring-security-jwt.md
  - ../../06-architecture/technology-policy.md
  - ../quality/obs-001-logging-observability.md
supersedes: []
superseded_by: null
---

# ADR-DATA-005 Redis 8.8 관리자 Refresh Token 저장소

## 1. 상태

Accepted

## 2. 결정 요약

관리자 Refresh Token의 발급·회전·폐기 상태는 Redis Open Source 8.8에 저장한다.

## 3. 배경

JWT Access Token 재발급을 통제하고 새 로그인·로그아웃·Token 재사용 시 Refresh 권한을 빠르게 폐기해야 한다.

## 4. 결정 문제

관리자 Refresh Token 상태를 어떤 저장소와 운영 배치로 관리할 것인가.

## 5. 고려한 선택지

- 개발 Docker / 운영 사설 서브넷 Redis 8.8
- PostgreSQL 저장
- 저장 없는 장기 JWT Refresh Token

## 6. 결정

개발은 Docker Redis 8.8, 운영은 앱 인스턴스에 함께 올린 Docker Redis 8.8을 사용하며 `127.0.0.1:6379`에만 바인딩해 인스턴스 밖에서 연결할 수 없게 한다 (2026-07-30 김인안·이우람 합의로 배치 표현 개정. 이전 표현은 "사설 서브넷 전용 Redis 8.8 인스턴스"였다). Refresh Token은 `auth:refresh:{adminId}` 키에 SHA-256 Token 해시, Token 계열 ID, 발급·만료 시각을 JSON으로 저장한다. 계정당 활성 Refresh Token 하나만 유지하며 TTL은 14일이다. 재발급마다 원자적으로 회전하고 재사용을 탐지해 Token 계열을 즉시 폐기한다.

로그인 실패는 원문 login ID 대신 SHA-256 해시를 사용한 `auth:login-failure:{loginIdHash}` 카운터에 저장한다. 첫 실패부터 15분 TTL을 부여하고 원자 증가 결과가 5 이상이면 남은 TTL 동안 로그인을 차단하며 성공 시 삭제한다. 만료된 인증 상태는 Redis TTL로 정리하고 별도 주기 삭제 작업을 두지 않는다.

## 7. 선택 근거

고정 기술 스펙과 관리자 JWT 결정에 따라 TTL 기반 Token 수명과 빠른 폐기·대조를 제공한다.

## 8. 트레이드오프

인증 DB 부하를 분리하지만 Redis 장애가 토큰 재발급에 영향을 주고 별도 복구·관측이 필요하다.

## 9. 적용 범위

현재는 관리자 Refresh Token에만 적용한다. 캐시, 일반 사용자 Token과 분산 락은 별도 Backlog 결정이다.

## 10. 강제 규칙

8.8 계열, 사설 네트워크, `auth:refresh`·`auth:login-failure` 네임스페이스, JSON 직렬화, 정확한 TTL, 회전 원자성과 재사용 탐지를 적용한다.

## 11. 금지 사항

퍼블릭 IP, `latest`, Token 원문 로그, 무기한 TTL, 캐시·락 키와 책임 혼합을 금지한다.

## 12. 구현 및 운영 영향

환경별 연결, 직렬화, TTL·eviction 보호, AOF/RDB, 장애 시 재로그인 정책과 메트릭이 필요하다. Redis 장애로 Refresh Token 조회가 불가능하면 재발급을 차단하는 fail-closed로 처리하여 관리자 Access Token 만료 후 재로그인을 요구한다 (2026-07-24 결정).

## 13. 검증 방법

Testcontainers Redis 8.8로 동시 로그인, 회전, 재사용, 만료, 로그아웃과 Redis 장애 시나리오를 검증한다.

## 14. 재검토 조건

Redis 운영 비용·가용성 문제가 발생하거나 인증 제공자 전환·일반 사용자 인증이 승인될 때 재검토한다.

## 15. 관련 문서

- [관리자 인증 ADR](../security/auth-001-spring-security-jwt.md)
- [관리자 인증 API](../../05-specs/api/admin/authentication-api.md)
- [Redis 정책](../../06-architecture/technology-policy.md#7-redis-연결-및-역할-분리-정책)
