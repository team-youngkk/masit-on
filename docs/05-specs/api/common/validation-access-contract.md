---
status: approved
last_reviewed: 2026-08-03
owner: 이우람
related_documents:
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../06-architecture/security-boundary.md
  - ../../../07-adr/platform/deploy-003-validation-cookie-session.md
  - ../../../08-planning/expansion-1-task-breakdown.md
---

# 검증 참여자 제한 공개 API 계약

## 1. 범위

이 계약은 정식 공개 전 `masiton.click` 접근을 검증 참여자로 제한하는 임시 운영 경계다. 일반 회원·관리자 로그인이나 서비스 권한을 제공하지 않는다. [OPS-VALIDATION 공통 운영·배포 트랙](../../../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)이 이 계약을 소유하며, 현재 Basic Auth 구현은 [E1-T13](../../../08-planning/expansion-1-task-breakdown.md#e1-t13-검증-참여자-제한-공개-쿠키-세션-전환)에서 이 계약으로 교체한다.

## 2. 세션 생성

### API-VALIDATION-001 검증 참여자 로그인

- Method: `POST`
- Path: `/api/verification/sessions`
- 인증: 없음, HTTPS 동일 Origin 필수

```json
{
  "loginId": "participant",
  "password": "secret"
}
```

성공은 `204 No Content`이며 `__Host-masiton-verification` 쿠키를 발급한다. 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, 7일 고정 만료이고 `Domain`을 지정하지 않는다. 응답 본문에 세션 ID를 넣지 않는다.

잘못된 자격 증명은 `401 INVALID_VALIDATION_CREDENTIALS`, 출처 또는 ID별 15분 5회 초과는 `429 RATE_LIMIT_EXCEEDED`, Redis 장애는 `503 VALIDATION_SESSION_UNAVAILABLE`이다. 오류는 검증 참여자 등록 여부를 구분하지 않는다.

## 3. 세션 종료

### API-VALIDATION-002 검증 참여자 세션 종료

- Method: `DELETE`
- Path: `/api/verification/sessions`
- 인증: 검증 세션 쿠키 선택, HTTPS 동일 Origin 필수. 이 경로는 Nginx 세션 gate에서 제외하고 Backend가 쿠키를 직접 처리한다.

서버 세션을 폐기하고 같은 속성으로 쿠키를 만료한 뒤 `204 No Content`를 반환한다. 이미 없거나 만료된 세션도 외부에는 같은 결과를 제공한다.

## 4. 내부 검증

Nginx `auth_request` 전용 검증 경로는 외부 API가 아니다. 인터넷에서 직접 접근할 수 없는 `internal` location을 통해 Spring Boot Adapter에 전달한다. 유효 세션은 `204`, 누락·변조·만료는 `401`, Redis 장애는 `503`으로 구분하며 응답 본문과 헤더에 세션 정보를 넣지 않는다.

## 5. 접근 실패 표현

- 화면 요청: 원래 상대 경로를 안전한 서버 측 값으로 보존하고 `/verification/login`으로 이동한다.
- API 요청: `401 VALIDATION_ACCESS_REQUIRED` 공통 JSON 오류를 반환하며 로그인 HTML로 redirect하지 않는다.
- Basic Auth challenge와 `WWW-Authenticate: Basic`은 반환하지 않는다.

## 6. 완료 조건

- 한 번 로그인한 브라우저에서 7일 동안 페이지 이동·새로고침·회원 및 관리자 Bearer 요청에 검증 로그인을 다시 요구하지 않는다.
- 회원·관리자 로그인·로그아웃은 검증 쿠키를 변경하지 않는다.
- 세션 원문·비밀번호·Cookie 헤더가 저장소·로그·오류에 남지 않는다.
- 정식 공개 시 이 API와 쿠키를 제거해도 회원·관리자 인증 계약은 바뀌지 않는다.
