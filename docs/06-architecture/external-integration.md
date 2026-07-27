---
related_documents:
  - architecture-overview.md
  - package-structure.md
  - application-flow.md
  - transaction-boundaries.md
  - ../05-specs/api/admin/reference-data-api.md
  - ../07-adr/architecture/arch-002-external-ports-adapters.md
  - ../07-adr/integration/ext-001-reference-verification.md
  - ../07-adr/security/auth-003-confirmation-token.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
  - ../07-adr/quality/test-001-automation-strategy.md
---

# 외부 연동

## 1. 적용 범위

MVP 외부 호출은 관리자 등록 미리보기의 다음 확인으로 제한한다.

- Kakao Local REST API V2: 장소 존재·동일성, 표시 정보
- YouTube Data API v3: 채널 존재·외부 채널 ID·표시 정보
- YouTube Data API v3: 영상 존재·외부 영상 ID·표시 정보·게시 채널

공개 목록·상세는 저장된 내부 데이터만 사용하고 Kakao·YouTube를 실시간 호출하지 않는다.

## 2. Port/Adapter 배치

Port는 이를 필요로 하는 도메인의 `application.port.out`에 둔다.

```text
restaurant.application.port.out.PlaceVerificationPort
    ← restaurant.infrastructure.external.kakao.KakaoPlaceVerificationAdapter

creator.application.port.out.CreatorVerificationPort
    ← creator.infrastructure.external.youtube.YoutubeCreatorVerificationAdapter

video.application.port.out.VideoVerificationPort
    ← video.infrastructure.external.youtube.YoutubeVideoVerificationAdapter
```

공통 HTTP 전송·인증 헤더·JSON 역직렬화가 실제로 중복되면 기술적인 `YoutubeApiClient`를 제한적으로 공유할 수 있다. 이 Client는 업무 판정이나 내부 결과 조합을 소유하지 않고, Creator·Video Adapter가 각 Port 결과로 변환한다.

## 3. Port 예시

```java
public interface PlaceVerificationPort {
    PlaceVerificationResult verify(PlaceVerificationQuery query);
}

public sealed interface PlaceVerificationResult {
    record Verified(
            ExternalPlaceId externalPlaceId,
            String name,
            String roadAddress,
            String phoneNumber,
            URI placeUrl
    ) implements PlaceVerificationResult {}

    record NotFound() implements PlaceVerificationResult {}
    record ReviewRequired(String safeReasonCode)
            implements PlaceVerificationResult {}
}
```

Port와 내부 결과에는 `KakaoResponse`, YouTube SDK Resource 같은 제공자 타입을 사용하지 않는다. 제공자 이름은 Adapter와 설정에서만 드러내고 Port는 “장소 확인”, “채널 확인”, “영상 확인”이라는 내부 용어를 사용한다.

## 4. Adapter 책임

| 책임 | Adapter | Application |
|---|---:|---:|
| 엔드포인트·인증 헤더 | 예 | 아니요 |
| 제공자 요청·응답 DTO | 예 | 아니요 |
| HTTP timeout 적용 | 예 | 아니요 |
| 404·429·5xx·역직렬화 실패 분류 | 예 | 아니요 |
| 제공자 DTO → 내부 확인 결과 변환 | 예 | 아니요 |
| 기존 내부 자원 중복 조회 | 아니요 | 예 |
| `READY/DUPLICATE/REVIEW_REQUIRED` 결정 | 아니요 | 예 |
| 관리자 확인 Token 발급 | 아니요 | 예 |
| Domain 생성·저장 | 아니요 | 예 |

## 5. 외부 DTO 변환 경계

```text
Provider JSON
  → Provider DTO (Infrastructure private)
  → Adapter validation
  → Internal Verification Result
  → Application candidate
  → 관리자 확인
  → Domain command
```

- 제공자 DTO는 Adapter 패키지 밖으로 반환하지 않는다.
- 누락·예상하지 못한 필드는 “불완전 응답”으로 분류하고 내부 모델을 억지로 채우지 않는다.
- 외부 표시 이름이나 URL을 내부 유일성으로 사용하지 않고 외부 place/channel/video ID를 사용한다.
- 외부 원본 영상·이미지는 저장하지 않고 URL만 저장한다.

## 6. 실패 모델

Application이 받는 안정된 실패 유형은 최소 다음 의미를 구분한다.

| 내부 실패 | 제공자 상황 | API 매핑 |
|---|---|---|
| `ExternalResourceNotFound` | 존재하지 않음·비공개로 확인 불가 | 입력 오류 또는 검토 필요 판정 |
| `ExternalRateLimited` | 429·할당량 초과 | `502 EXTERNAL_SERVICE_ERROR` |
| `ExternalTimeout` | 연결·응답 시간 초과 | `502 EXTERNAL_SERVICE_ERROR` |
| `ExternalServiceUnavailable` | 제공자 5xx·네트워크 장애 | `502 EXTERNAL_SERVICE_ERROR` |
| `ExternalContractViolation` | 필수 필드 누락·응답 구조 불일치 | `502 EXTERNAL_SERVICE_ERROR` |

오류 객체는 제공자 원문 본문·API Key·내부 URL을 포함하지 않는다. 운영 로그에는 제공자, 호출 종류, 안전한 오류 분류, latency, traceId를 기록한다.

## 7. timeout, retry와 rate limit

### timeout

- 연결과 전체 응답에 유한한 상한을 둔다.
- 구체 수치는 현재 NFR과 ADR에서 확정되지 않았다.
- **확인 필요:** Kakao·YouTube별 연결·응답 timeout 수치를 구현 전에 확정하고 설정·WireMock 테스트·운영 문서에 같은 값을 사용한다.
- timeout은 Adapter 설정이며 Domain에 전파하지 않는다.

### retry

- MVP는 자동 재시도를 하지 않는다.
- 실패한 미리보기는 핵심 데이터를 저장하지 않고 관리자가 수동 재시도한다.
- 쓰기 요청을 Adapter가 임의 재시도하지 않는다.
- 자동 재시도를 도입하려면 최대 횟수, backoff, 429 처리, 전체 시간 예산과 멱등성을 추가 ADR로 결정한다.

### rate limit

- 제공자 429·할당량 오류를 명시적으로 분류한다.
- 무한 대기하거나 일반 서버 오류로 숨기지 않는다.
- 실제 호출량과 한도가 확인되기 전 클라이언트 측 분산 rate limiter를 선제 도입하지 않는다.

## 8. 트랜잭션과 장애 격리

- 외부 호출은 DB 트랜잭션 밖에서 수행한다.
- 외부 확인 실패 시 Restaurant·Creator·Video·Visit를 생성하지 않는다.
- 공개 조회 경로에는 외부 Adapter Bean 호출이 없어야 한다.
- 이미 저장된 링크의 일시 도달 실패는 상세 전체 실패로 바꾸지 않는다.
- 외부 서비스 장애와 DB·인증 오류를 다른 관측 분류로 기록한다.

## 9. 테스트 대역

### Application 단위 테스트

각 Port를 구현한 in-memory Fake를 주입한다.

```java
final class FakeVideoVerificationAdapter implements VideoVerificationPort {
    // 테스트가 지정한 내부 결과만 반환
}
```

Fake는 제공자 JSON을 흉내 내지 않고 Application이 소비하는 내부 계약을 검증한다.

### Adapter 계약 테스트

WireMock으로 실제 HTTP 경계를 검증한다.

- 정상 응답
- 존재하지 않음
- 429·할당량 초과
- 연결·응답 지연과 timeout
- 5xx
- 필수 필드 누락·타입 변경
- 채널명 등 표시 정보 변경

실제 운영 API와 운영 Key를 자동화 테스트에 사용하지 않는다.

## 10. 설정과 비밀정보

- API Key는 코드, Git, 기본 설정과 로그에 두지 않는다.
- 운영은 Parameter Store SecureString과 KMS, EC2 IAM Role을 사용한다.
- 개발·테스트에는 명시적 가짜 값과 WireMock 엔드포인트를 사용한다.
- 설정에는 provider, base URL, timeout 같은 기술값만 두고 비즈니스 판정을 넣지 않는다.
- API Key나 Authorization 헤더를 HTTP 로깅에서 마스킹한다.

## 11. 관측성

외부 호출마다 다음을 기록한다.

- `traceId`
- 제공자와 호출 유형
- 성공/실패 분류
- 응답 시간
- rate limit 또는 timeout 여부

다음은 기록하지 않는다.

- API Key·전체 Authorization 헤더
- 관리자 Access/Refresh Token
- 제공자 원문 응답 전체
- 민감한 요청·응답 본문

## 12. 추가 ADR 필요

- 자동 재시도·Circuit Breaker·비동기 Queue 도입
- 주기적 외부 상태 동기화
- 제공자 다중화와 자동 fallback
- 공개 조회에서 실시간 외부 호출
- AI·크롤링 기반 자동 추출
- 공통 YouTube Client가 독립 모듈·서비스로 커지는 경우
