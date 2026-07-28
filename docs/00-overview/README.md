---
related_documents:
  - service-overview.md
  - scope.md
  - glossary.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../02-analysis/domain-boundaries.md
  - ../04-product/README.md
---

# 서비스 개요와 범위

## 1. 목적

이 디렉터리는 맛잇온이 **무엇을 하는 서비스이고 MVP에서 어디까지 만드는지**를 확정한다. 이후의 요구사항, PRD, API 계약, 데이터 모델과 ADR은 모두 여기서 정한 범위와 용어를 전제로 작성된다.

저장소에서 가장 상위의 기준 문서이므로, 다른 문서와 충돌하면 이 디렉터리가 우선한다. 범위를 넓히거나 좁히려면 [범위 변경 절차](scope.md#7-범위-변경-절차)를 먼저 거친다.

## 2. 문서 읽기 순서

1. [서비스 개요](service-overview.md): 서비스 정의, 해결하려는 문제, 핵심 사용자와 사용자 흐름
2. [범위](scope.md): MVP 포함·제외 범위, 확장 단계, 범위 경계 규칙과 변경 절차
3. [용어집](glossary.md): 요구사항부터 구현까지 공통으로 쓰는 용어의 기준 의미

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `service-overview.md` | 이 서비스는 누구의 어떤 문제를 어떻게 푸는가? | 기능별 상세 동작과 예외 조건 |
| `scope.md` | MVP에 무엇이 들어가고 무엇이 빠지는가? | 기능의 구체적 결과와 판단 기준 |
| `glossary.md` | 이 용어를 우리는 어떤 의미로 쓰는가? | 테이블명·클래스명·API 경로 |

## 4. 사용 시 주의

- 용어집의 영문명은 **코드와 API 설계를 위한 참고 표현**이다. 테이블명, 클래스명, API 경로를 확정하지 않는다. 물리 이름은 [데이터 명세](../05-specs/data/README.md)와 [API 계약](../05-specs/api/README.md)에서 정한다.
- 와이어프레임에는 지도·테마·보관함 같은 확장 기능이 그려져 있다. [제외 범위](scope.md#4-mvp-제외-범위)에 있으면 화면과 Route를 만들지 않는다.
- MVP는 로컬 Docker 통합 검증까지이며 AWS 운영 배포를 포함하지 않는다.

## 5. 다음 단계

| 다음 문서 | 이 디렉터리에서 이어받는 것 |
|---|---|
| [기능 요구사항](../01-requirements/functional-requirements.md) | 범위 안 기능의 정상 결과와 예외 조건 |
| [비즈니스 규칙](../01-requirements/business-rules.md) | 등록·관계·공개·중복 판단 기준 |
| [도메인 경계](../02-analysis/domain-boundaries.md) | 업무 책임 단위 분리 |
| [PRD](../04-product/README.md) | 화면 단위 제품 정의 |
