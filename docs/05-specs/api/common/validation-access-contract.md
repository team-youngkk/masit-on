---
status: approved
last_reviewed: 2026-08-14
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

## 4. 세션 gate 제외 경로

제외를 허용하는 **조건**은 [ADR-DEPLOY-003](../../../07-adr/platform/deploy-003-validation-cookie-session.md) 4.3절이 정한다. 이 절은 그 조건을 충족해 실제로 제외된 경로의 **목록**을 소유한다. 2026-08-13 승인 계약의 후속 결정에 따라, Nginx 제한 공개 gate의 제품 API 제외 목록을 Spring Security가 비관리자에게 `permitAll`로 공개한 계약과 동기화한다. 제한 공개 gate(`auth_request /_verification/session`)는 `/api/`와 `/` 두 prefix `location`에 걸리며, 아래 경로와 메서드만 별도 규칙으로 처리한다. **`/api/**`는 목록에 없거나 경로는 같아도 Method가 다른 요청이면 전부 gate를 통과해야 한다.** 프론트 진입점의 Method 처리는 4.1절이 별도로 정한다.

### 4.1 운영 진입점과 외부 callback

| 경로 | 매칭 | Method | 제외 이유 | 이 경로의 인증 수단 |
|---|---|---|---|---|
| `/verification/login` | exact | `GET`(Nginx 규칙상 `HEAD` 포함) | 세션 생성 화면 자체가 세션을 요구하면 로그인이 불가능하다 | 없다. 화면만 제공하며 세션은 이 화면에서 생성한다 |
| `/_next/static/` | prefix | `GET`(Nginx 규칙상 `HEAD` 포함) | 로그인 화면이 동작하는 데 필요한 정적 자산이다. Next.js가 경로를 빌드 시점에 정하므로 exact-match로 열거할 수 없다 | 없다. 비밀을 담지 않는 빌드 산출물만 제공한다 |
| `/api/verification/sessions` | exact | `POST`, `DELETE` | 이 계약 자체의 진입점이므로 gate 안에 둘 수 없다 | 자격 증명(`POST`), 검증 세션 쿠키(`DELETE`) |
| `/api/webhooks/youtube/channel-updates` | exact | `GET`, `POST` | PubSubHubbub 허브는 브라우저가 아니어서 검증 세션 쿠키를 보낼 수 없다. gate 안에 두면 구독 확인 `GET`이 `hub.challenge`를 되돌려주지 못해 구독이 성립하지 않는다 | [AI 영상 추출 API](../admin/ai-video-extraction-api.md) 4.1·4.2절이 정한다 |

`/verification/login`과 `/_next/static/`은 프론트 진입점이다. Nginx의 `limit_except GET` 의미에 따라 `GET`과 `HEAD`만 허용하며 다른 Method는 validation gate로 보내지 않고 `403`으로 거부한다. 반면 표의 `/api/**` 경로에서 허용하지 않은 Method는 validation gate를 거친다.

### 4.2 비관리자 공개 API

다음 exact 경로는 Spring Security의 비관리자 `permitAll` 공개 계약과 함께 관리한다.

| 경로 | Method | 공개 목적 |
|---|---|---|
| `/api/auth/registrations` | `POST` | 회원 등록 |
| `/api/auth/email-verifications` | `POST` | 이메일 검증 |
| `/api/auth/email-verifications/resend` | `POST` | 이메일 검증 재전송 |
| `/api/auth/password-resets/requests` | `POST` | 비밀번호 재설정 요청 |
| `/api/auth/password-resets/confirmations` | `POST` | 비밀번호 재설정 확정 |
| `/api/auth/tokens` | `POST` | 회원 로그인 |
| `/api/auth/tokens/refresh` | `POST` | 회원 Token 재발급 |
| `/api/restaurants` | `GET` | 맛집 목록 조회 |
| `/api/curations` | `GET` | 큐레이션 목록 조회 |
| `/api/creators` | `GET` | 유튜버 목록 조회 |
| `/api/restaurants/course-routes` | `POST` | 맛집 코스 추천 조회 |
| `/api/restaurants/natural-language-search` | `POST` | 자연어 맛집 검색 |

식별자가 포함된 공개 조회는 다음 anchored 정규식과 같은 의미로만 매칭한다.

| 계약 경로 | Method | Nginx 매칭 의미 |
|---|---|---|
| `/api/restaurants/{id}` | `GET` | `^/api/restaurants/[^/]+$` |
| `/api/curations/{id}` | `GET` | `^/api/curations/[^/]+$` |
| `/api/creators/{id}` | `GET` | `^/api/creators/[^/]+$` |
| `/api/creators/{id}/restaurants` | `GET` | `^/api/creators/[^/]+/restaurants$` |
| `/api/creators/{id}/videos` | `GET` | `^/api/creators/[^/]+/videos$` |

`{id}`는 비어 있지 않은 **불투명 단일 경로 세그먼트**다. UUID 형식이나 생성 규칙을 가정하지 않는다. 정규식은 시작과 끝을 고정하며, 하위 경로나 비슷한 prefix까지 제외 범위를 넓히지 않는다. `/_next/static/`을 뺀 정적 경로는 exact-match로 두고, 동적 경로는 위 anchored 정규식만 사용한다.

Spring Security에서 JWT 없이 요청할 수 있는 `/api/admin/auth/tokens`와 `/api/admin/auth/tokens/refresh`는 이 목록의 예외다. 두 관리자 경로는 Nginx 검증 session gate를 계속 요구한다. gate를 통과한 뒤에는 각각 관리자 자격 증명과 관리자 Refresh Token 계약을 적용하며, Spring JWT 자체를 요구하지 않는다. 그 밖의 정의되지 않은 `/api/**`도 gate를 유지한다.

비관리자 공개 API를 gate에서 제외하는 것은 Spring 인증·인가를 우회한다는 뜻이 아니다. Nginx는 이 경로의 `Authorization`과 `Cookie`를 보존해 Backend가 각 API 계약을 적용하게 한다. 특히 맛집 상세는 선택적 회원 Bearer 문맥을 사용할 수 있고 `/api/auth/tokens/refresh`는 회원 Refresh 쿠키를 처리해야 한다.

기존 운영 예외의 자격 증명 제거는 유지한다. `/api/verification/sessions`에서는 회원·관리자 Bearer가 검증 세션 경계로 넘어가지 않도록 `Authorization`을 비우되, `DELETE`가 검증 세션 쿠키를 Backend에서 직접 폐기할 수 있게 `Cookie`를 전달한다. 외부 시스템이 호출하는 `/api/webhooks/youtube/channel-updates`는 `Authorization`과 `Cookie`를 모두 비운다.

`/api/**` 제외 경로는 무인증 요청이 백엔드에 도달할 수 있으므로 Nginx에서 경로별 허용 Method를 제한한다. 공개 API path라도 표에 없는 Method는 제한 공개 gate를 우회할 수 없다. 본문과 호출률은 각 API의 입력·남용 방지 계약을 적용한다. `/api/webhooks/youtube/channel-updates`에서 gate 없이 허용하는 Method는 `GET`·`POST`뿐이다. `HEAD`, `PATCH`를 비롯한 다른 Method는 validation gate와 API용 JSON 오류 Adapter를 거친다. Webhook의 현재 제한은 128KB, 출처 IP별 10r/s(burst 20)이며 초과는 `429`다.

`/api/webhooks/youtube/channel-updates`의 제외는 제한 공개 범위를 넓히지 않는다. 알림 `POST`는 공유 비밀 HMAC 없이 통과하지 못하고, 구독 확인 `GET`은 서버가 구독을 시작할 때 발급한 검증 Token을 아는 호출자만 통과한다. **다만 `GET`의 방어는 검증 Token 하나에만 의존한다.** 서명 검증과 달리 페이로드 무결성 검사가 없으므로 Token 유출은 곧 구독 확인 위조를 뜻한다.

## 5. 내부 검증

Nginx `auth_request` 전용 검증 경로는 외부 API가 아니다. 인터넷에서 직접 접근할 수 없는 `internal` location을 통해 Spring Boot Adapter에 전달한다. 유효 세션은 `204`, 누락·변조·만료는 `401`, Redis 장애는 `503`으로 구분하며 응답 본문과 헤더에 세션 정보를 넣지 않는다.

인터넷 진입점의 `/internal`과 `/internal/**` 요청은 자격 증명 유무와 무관하게 항상 `404`로 응답하며 Backend로 전달하지 않는다.

## 6. 접근 실패 표현

- 화면 요청: 원래 상대 경로를 안전한 서버 측 값으로 보존하고 `/verification/login`으로 이동한다.
- API 요청: `401 VALIDATION_ACCESS_REQUIRED` 공통 JSON 오류를 반환하며 로그인 HTML로 redirect하지 않는다.
- Basic Auth challenge와 `WWW-Authenticate: Basic`은 반환하지 않는다.

## 7. 완료 조건

- 한 번 로그인한 브라우저에서 7일 동안 페이지 이동·새로고침·회원 및 관리자 Bearer 요청에 검증 로그인을 다시 요구하지 않는다.
- 회원·관리자 로그인·로그아웃은 검증 쿠키를 변경하지 않는다.
- 세션 원문·비밀번호·Cookie 헤더가 저장소·로그·오류에 남지 않는다.
- 정식 공개 시 이 API와 쿠키를 제거해도 회원·관리자 인증 계약은 바뀌지 않는다.
