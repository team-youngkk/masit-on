---
id: ADR-AUTH-001
title: 관리자 Spring Security JWT 인증·인가
status: Accepted
decision_date: 2026-07-24
owners:
  - 김인안
related_requirements:
  - FR-ADMIN-001
  - NFR-SECURITY-001
  - NFR-SECURITY-003
  - NFR-PRIVACY-002
related_documents:
  - ../../00-overview/scope.md
  - ../../05-specs/api/admin/authentication-api.md
  - ../../01-requirements/non-functional-requirements.md
supersedes: []
superseded_by: null
---

# ADR-AUTH-001 관리자 Spring Security JWT 인증·인가

## 1. 상태

Accepted

## 2. 결정 요약

관리자 인증·인가는 Spring Security 7.1.0과 JWT Access Token을 사용한다. Refresh Token은 Redis 8.8에 저장하고 보안 쿠키로 전달·회전한다.

## 3. 배경

사전 발급된 관리자만 WS-04 등록 API에 접근하도록 통제해야 하며 일반 사용자 공개 조회는 무인증이어야 한다.

## 4. 결정 문제

관리자 신원과 `ADMIN` 권한을 API 요청마다 어떻게 검증하고 재인증·로그아웃을 통제할 것인가.

## 5. 고려한 선택지

- Spring Security JWT Access Token + Redis Refresh Token
- 서버 관리 세션과 CSRF Token
- 장기 API Key 또는 브라우저 저장 Token

## 6. 결정

Spring Security Filter Chain이 Bearer JWT의 서명·issuer·audience·만료와 `ADMIN` 권한을 검증한다. Refresh Token은 Redis에 저장하고 `HttpOnly`, `Secure`, `SameSite=Strict` 쿠키로만 전달하며 재발급 때 회전한다.

Access Token 만료는 30분, Refresh Token TTL은 14일로 하며 재발급마다 회전하고 재사용을 탐지해 즉시 폐기한다. Redis 장애로 Refresh Token 조회가 불가능한 경우 재발급을 차단하는 fail-closed로 처리하여 Access Token 만료 후 관리자 재로그인을 요구한다.

## 7. 선택 근거

사용자가 JWT와 Spring Security로 인증·인가 방식을 확정했으며, Access Token은 상태 없는 API 검증을 제공하고 Redis는 재발급·폐기를 통제한다.

## 8. 트레이드오프

요청별 세션 조회를 줄이는 대신 발급된 Access Token은 자체 만료 전 즉시 폐기가 어렵고 키·만료·회전 운영이 필요하다.

## 9. 적용 범위

관리자 로그인·재발급·로그아웃과 모든 `/admin` 등록 API에 적용한다. 일반 사용자 공개 API에는 적용하지 않는다.

## 10. 강제 규칙

Access Token은 메모리에만 유지해 Bearer 헤더로 보내고 JWT claim은 최소화한다. Refresh Token은 JavaScript에 노출하지 않고 회전·재사용 탐지를 수행한다.

## 11. 금지 사항

Access·Refresh Token의 localStorage·sessionStorage 저장, 장기 Access Key, 비밀·비밀번호 claim, 일반 사용자 인증 선반영을 금지한다.

## 12. 구현 및 운영 영향

JWT 서명 키 보호·교체, Redis 가용성, Token 회전·폐기·로그 마스킹과 프론트엔드 메모리 상태 처리가 필요하다. Access Token 30분·Refresh Token 14일 TTL과 Redis 장애 시 fail-closed(강제 재로그인) 정책을 구현에 반영한다 (2026-07-24 결정).

## 13. 검증 방법

정상·만료·변조·잘못된 issuer/audience JWT, 권한 없음, Refresh 회전·재사용·로그아웃과 비밀정보 로그 검사를 자동화한다.

## 14. 재검토 조건

일반 사용자 로그인이 범위에 추가되거나 즉시 Access Token 폐기·외부 IdP·다중 권한 요구가 생길 때 재검토한다.

## 15. 관련 문서

- [관리자 인증 API](../../05-specs/api/admin/authentication-api.md)
- [Redis Token 저장 ADR](../data/data-005-redis-refresh-token.md)
- [기술 정책](../../06-architecture/technology-policy.md)
