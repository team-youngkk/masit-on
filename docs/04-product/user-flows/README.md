---
related_documents:
  - first-expansion-user-flows.md
  - second-expansion-user-flows.md
  - ../wireframes/first-expansion-wireframes.md
  - ../README.md
  - ../../00-overview/scope.md
  - ../../02-analysis/first-expansion-workstreams.md
---

# 사용자 흐름

## 1. 목적

이 디렉터리는 여러 화면과 기능에 걸친 사용자 행동, 시스템 상태 전이와 실패 복구를 관리한다. 개별 기능의 제품 정책은 각 PRD가 소유하며, 사용자 흐름은 화면 간 연결과 공통 상태가 서로 모순되지 않게 한다.

## 2. 문서 목록

| 문서 | 범위 | 상태 |
|---|---|---|
| [1차 확장 사용자 흐름](first-expansion-user-flows.md) | 회원가입·로그인, 찜, 최근 본 맛집, 지도, 유튜버 상세와 공통 인증·자원 상태 | 확정 |
| [2차 확장 사용자 흐름](second-expansion-user-flows.md) | 개인 컬렉션, 인기·큐레이션, 제보·신고, 알림과 공통 상태 전이 | 초안 |

## 3. 작성 원칙

- 범위와 정책은 [프로젝트 범위](../../00-overview/scope.md), 기능 요구사항과 PRD를 따른다.
- 화면 상태와 제어의 위치는 해당 확장 단계의 와이어프레임에 연결한다.
- API 경로·필드, 데이터 구조와 기술 구현은 이 디렉터리에서 확정하지 않는다.
- 정상 흐름뿐 아니라 비로그인, 인증 만료, 권한 오류, 빈 결과, 비공개·삭제 자원과 외부 의존성 장애의 다음 행동을 정의한다.
