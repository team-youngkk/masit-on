---
related_documents:
  - ../README.md
  - identifier-contract.md
  - response-contract.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../07-adr/security/auth-003-confirmation-token.md
  - ../../../07-adr/quality/obs-001-logging-observability.md
---

# 오류 계약

## 1. 오류 본문

```json
{
  "code": "RESTAURANT_NOT_FOUND",
  "message": "요청한 맛집을 찾을 수 없습니다.",
  "errors": [],
  "resource": null,
  "traceId": "01K123ABC456DEF789GHJKMNPQ"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `code` | string | 예 | 안정적인 서비스 오류 코드 |
| `message` | string | 예 | 사용자 또는 클라이언트가 이해할 수 있는 일반화된 메시지 |
| `errors` | array | 예 | 상세 검증 오류. 없으면 `[]` |
| `errors[].field` | string | 예 | 잘못된 요청 필드의 JSON 경로 또는 쿼리 이름 |
| `errors[].reason` | string | 예 | 안전한 검증 실패 설명 |
| `resource` | object 또는 null | 아니요 | 관리자 중복 등록 오류에서 재사용할 기존 자원의 최소 식별·표시 정보. 그 밖의 오류에서는 생략 |
| `traceId` | string | 예 | 서버가 요청마다 생성한 불투명 추적 문자열 |

발생 시각과 요청 경로는 응답에 포함하지 않는다. 모든 오류 응답은 `traceId`를 포함하고 같은 값을 운영 로그에 기록한다. 클라이언트는 값의 구조를 해석하지 않고 지원·장애 문의 시 그대로 전달한다.

## 2. 공통 매핑

| 범주 | HTTP | 코드 | 사용 조건 |
|---|---:|---|---|
| 잘못된 요청 형식 | 400 | `INVALID_REQUEST` | JSON·쿼리 구조를 해석할 수 없음 |
| 필수값 누락 | 400 | `MISSING_REQUIRED_FIELD` | 필수 입력 누락 |
| 형식·범위·허용값 오류 | 400 | `INVALID_FIELD_VALUE` | URL, 열거값, 페이지 등 검증 실패 |
| 식별자 형식 오류 | 400 | `INVALID_IDENTIFIER` | 확정된 식별자 형식 불일치 |
| 인증 실패 | 401 | `AUTHENTICATION_REQUIRED` | 관리자 인증 없음 또는 무효 |
| 권한 부족 | 403 | `FORBIDDEN` | 인증됐으나 관리자 권한 없음 |
| 자원 없음 | 404 | `*_NOT_FOUND` | 단일 조회 또는 관리자 참조 대상 없음 |
| 중복 데이터 | 409 | `DUPLICATE_*` | 동일 맛집·유튜버·영상·방문 관계가 이미 존재 |
| 동일성 판단 보류 | 409 | `IDENTITY_VERIFICATION_REQUIRED` | 신규·기존 여부를 결정할 수 없음 |
| 확인 토큰 만료 | 409 | `VERIFICATION_EXPIRED` | 검증 미리보기 확인 토큰이 만료됨 |
| 확인 Token 오류 | 400 | `INVALID_CONFIRMATION_TOKEN` | 존재하지 않는 Token·관리자 식별자 불일치·다른 자원 생성 API 사용 |
| 비즈니스 규칙 위반 | 422 | 기능별 코드 | 채널 불일치, 방문 근거 부족 등 입력 구조는 맞으나 등록 불가 |
| 비공개 참조 | 422 | `REFERENCE_NOT_PUBLIC` | 공개 상태가 아닌 대상으로 관계 생성 시도 |
| 외부 서비스 오류 | 502 | `EXTERNAL_SERVICE_ERROR` | 관리자 등록 중 필수 외부 확인 실패 |
| 서버 내부 오류 | 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 내부 실패 |

`*_NOT_FOUND`와 `DUPLICATE_*`의 실제 코드는 기능 문서에서 한 가지 HTTP 상태와 연결한다. 내부 예외명, 스택 트레이스, SQL, 파일 경로, 토큰과 외부 키는 응답에 노출하지 않는다.

확인 Token으로 최초 생성에 성공하면 `201`, 같은 관리자·같은 Token의 생성 완료 재시도는 오류가 아니라 기존 자원의 `200`이다. 미리보기 뒤 동시 중복으로 완료된 Token은 최초와 재시도 모두 동일한 `409 DUPLICATE_*`와 기존 자원 정보를 반환한다.

## 3. 상세 검증 오류 예시

```json
{
  "code": "INVALID_FIELD_VALUE",
  "message": "요청 값을 확인해 주세요.",
  "errors": [
    {
      "field": "category",
      "reason": "지원하는 대표 음식 카테고리가 아닙니다."
    }
  ],
  "resource": null,
  "traceId": "01K123ABC456DEF789GHJKMNPQ"
}
```
