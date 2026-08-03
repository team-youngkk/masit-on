---
id: API-COMMON-E2-001
title: 2차 확장 공통 API 계약
status: draft
related_documents:
  - ../README.md
  - authentication-contract.md
  - response-contract.md
  - error-contract.md
  - pagination-contract.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/second-expansion-domain-boundaries.md
---

# 2차 확장 공통 API 계약

## 1. 적용 범위

개인 컬렉션, 인기 맛집, 관리자 큐레이션, 사용자 제보·신고와 사용자 알림 API에 공통 적용한다. 기능별 문서가 이 문서와 충돌하면 범위·요구사항을 먼저 고친 뒤 두 계약을 함께 갱신한다.

## 2. 인증·소유권 경계

| 경로 | 접근 주체 | 실패 계약 |
|---|---|---|
| `/api/me/**` | 유효한 회원 Access Token의 현재 회원 | 미인증 `401 AUTHENTICATION_REQUIRED` |
| `/api/admin/**` | 유효한 관리자 Access Token과 `ADMIN` 권한 | 미인증 `401`, 권한 부족 `403 FORBIDDEN` |
| 그 밖의 이 문서군 공개 `GET` | 비로그인 포함 모든 사용자 | 공개 상태가 아니면 기능별 `404` 또는 목록 제외 |

- `/api/me/**` 요청·응답에는 `memberId`를 받거나 노출하지 않고 인증 Principal로 소유자를 결정한다.
- 다른 회원이 소유한 컬렉션·제보·신고·알림은 존재하지 않는 자원과 같은 기능별 `404 *_NOT_FOUND`를 반환한다.
- 관리자 응답의 제보·신고에는 처리에 필요한 요청자 식별값을 제공할 수 있지만 공개 API와 다른 회원 API에는 제공하지 않는다.
- 개인 응답은 `Cache-Control: private, no-store`, 공개 조회는 별도 캐시 정책이 생기기 전까지 `Cache-Control: no-store`를 사용한다.

## 3. 페이지네이션과 정렬

- 페이지 목록은 [페이지네이션 계약](pagination-contract.md)의 1부터 시작하는 `page`, `size`와 응답 `page` 객체를 사용한다.
- 허용 `size`는 `10`, `20`, `50`, 기본값은 `20`이다. 기능 문서에 정렬이 고정돼 있으면 임의 `sort` 쿼리를 받지 않는다.
- 최대 20개인 컬렉션 목록, 인기 맛집과 공개 큐레이션은 페이지 없이 `{ "items": [] }`를 반환한다.
- 같은 정렬값의 보조 정렬은 기능별 식별자 오름차순으로 고정해 페이지 이동 중 순서를 안정화한다.

## 4. 멱등성

### 4.1 생성 요청

다음 `POST`는 `Idempotency-Key` 헤더를 필수로 받는다.

- `POST /api/me/collections`
- `POST /api/admin/curations`
- `POST /api/me/submissions`
- `POST /api/me/reports`

키는 호출 주체와 API 경로 범위에서 24시간 보존하는 8~128자의 불투명 문자열이다. 키는 자원 생성 성공과 원자적으로 확정하며 같은 키·같은 본문 재요청은 최초 `201` 본문을 반환하고 새 자원을 만들지 않는다. 생성이 확정되지 않은 `4xx`·`5xx`는 키 결과로 보존하지 않는다. 성공한 키를 다른 본문에 쓰면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다. 헤더가 없거나 형식이 맞지 않으면 `400 INVALID_IDEMPOTENCY_KEY`다.

`expiresAt <= 현재 시각`인 기록은 cleanup 실행 여부와 무관하게 조회 시 만료로 판정하며 최초 응답을 재생하지 않는다. 서버는 해당 키 범위를 직렬화한 트랜잭션에서 만료 기록을 제거·교체하고 새 요청으로 처리한다. 동시 재사용의 고유 키 충돌은 승자 기록을 다시 읽어 같은 본문이면 새 성공 응답을 재생하고 다른 본문이면 `409 IDEMPOTENCY_KEY_REUSED`로 처리한다.

### 4.2 상태 설정 요청

- `PUT`은 목표 상태를 설정하는 계약이며 같은 요청 반복 시 동일 결과를 반환한다.
- 컬렉션 맛집 제거와 컬렉션 삭제의 `DELETE`는 대상 관계가 이미 없거나 현재 회원 소유가 아니어도 `204 No Content`로 종료해 존재 여부를 노출하지 않는다.
- 관리자 상태 전이는 현재 상태가 이미 요청 상태면 `200`, 허용되지 않는 다른 전이면 `409 INVALID_STATUS_TRANSITION`이다.

## 5. 상태·공개 상태 표현

- 공개·활성 Restaurant만 새 컬렉션 관계와 큐레이션 구성에 추가할 수 있다. 비공개·삭제·존재하지 않는 Restaurant은 일반 회원에게 모두 `404 RESTAURANT_NOT_FOUND`로 표현한다.
- 컬렉션에 포함된 Restaurant이 이후 비공개가 되면 관계는 보존하되 회원 조회의 `items`에서 숨긴다. 삭제 관계는 Restaurant 삭제 정책에 따라 정리한다.
- 공개 큐레이션은 비공개·삭제 Restaurant을 `items`에서 숨긴다. 관리자 큐레이션 응답은 관계를 유지하고 각 항목의 `availability`를 `PUBLIC`, `PRIVATE`, `DELETED`로 제공한다.
- 제보·신고 상태는 `RECEIVED`, `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `COMPLETED`만 사용한다. 허용 전이는 `RECEIVED → IN_REVIEW → ACCEPTED → COMPLETED`와 `IN_REVIEW → REJECTED`다.

## 6. 공통 오류

모든 오류는 [오류 계약](error-contract.md)의 `code`, `message`, `errors`, `resource`, `traceId`를 따른다.

| HTTP | 코드 | 조건 |
|---:|---|---|
| 400 | `INVALID_IDEMPOTENCY_KEY` | 생성 API의 키 누락·형식 오류 |
| 401 | `AUTHENTICATION_REQUIRED` | 회원 또는 관리자 인증 실패 |
| 403 | `FORBIDDEN` | 관리자 권한 부족 |
| 404 | `*_NOT_FOUND` | 자원 없음, 비공개 또는 본인 소유 아님 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키를 다른 본문에 재사용 |
| 409 | `INVALID_STATUS_TRANSITION` | 현재 상태에서 목표 상태로 전이 불가 |
| 409 | 기능별 상한·중복 코드 | 현재 자원 상태와 충돌 |
| 429 | `DAILY_REQUEST_LIMIT_EXCEEDED` | 제보·신고 합산 일일 5건 초과 |

관리자 상태 변경이 `409 INVALID_STATUS_TRANSITION`이면 서버 상태를 추측해 덮어쓰지 않고 상세를 다시 조회한다. `ACCEPTED` 전환 성공은 원본 데이터 생성·정정 성공을 뜻하지 않으며, 실제 조치가 끝난 뒤 `COMPLETED`를 별도 요청한다.

## 7. 알림 읽음 계약

읽음은 `false → true` 단방향이다. 개별·전체 읽음은 반복해도 기존 `readAt`을 바꾸지 않는다. 서버는 정확한 `unreadCount`를 반환하며 `99+` 축약은 화면 책임이다. 별도 알림 설정·동의·해지 API는 제공하지 않는다.
