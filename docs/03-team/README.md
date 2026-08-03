---
related_documents:
  - roles.md
  - ownership.md
  - ../02-analysis/mvp-workstreams.md
  - ../02-analysis/first-expansion-workstreams.md
  - ../02-analysis/second-expansion-workstreams.md
  - ../06-architecture/implementation-conventions.md
  - ../08-planning/mvp-2day-implementation-plan.md
---

# 팀 역할과 소유권

## 1. 목적

이 디렉터리는 MVP와 확장 Workstream 및 공통 운영 트랙을 **누가 최종 책임지는지** 확정한다. 소유자는 담당 항목의 요구사항 구체화, 계약, 구현, 테스트, 문서화와 통합 완료를 책임진다.

원칙은 하나다. **각 Workstream과 기능 요구사항의 최종 책임자는 항상 한 명이다.** 공동 작업에서도 최종 병합 책임자는 한 명으로 유지한다.

MVP 배정은 2026-07-27, 1·2차 확장과 `OPS-VALIDATION` 배정은 2026-08-03에 승인됐다. 일정 차단이나 지속적인 부담 불균형이 확인될 때만 [소유권 변경 절차](ownership.md#11-소유권-변경-절차)로 조정한다.

## 2. 문서 읽기 순서

1. [역할](roles.md): 팀원별 주요 책임, 결정 권한, 산출물과 협업 관계
2. [소유권](ownership.md): Workstream·기능 요구사항·비즈니스 규칙·비기능 요구사항·공통 작업·문서의 항목별 최종 책임자

"나는 무엇을 만드는가"는 `roles.md`, "이 항목은 누구에게 물어보는가"는 `ownership.md`를 본다.

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `roles.md` | 이 사람은 무엇을 책임지고 무엇을 결정할 수 있는가? | 개별 요구사항 ID의 담당자 |
| `ownership.md` | 이 요구사항·규칙·문서의 소유자는 누구인가? | 사람별 업무 흐름과 산출물 |

## 4. 배정 요약

| 담당 | 제품 Workstream | 공통·운영 책임 |
|---|---|---|
| 양성훈 | WS-01 맛집 탐색, WS-07 지도 탐색, WS-10 인기 맛집 | 프론트엔드 공통 Layout |
| 박진영 | WS-02 맛집 상세·콘텐츠, WS-06 개인 맛집, WS-09 개인 컬렉션 | Flyway 마이그레이션 순서 |
| 이우람 | WS-03 유튜버 기반 탐색, WS-08 유튜버 상세, WS-13 사용자 알림 | Spring Boot·Docker 실행 기반, [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) |
| 김인안 | WS-04 관리자 인증·등록, WS-05 회원 인증, WS-11 큐레이션, WS-12 제보·신고 | 인증 공통 설정 |

상세 책임과 협업 관계는 [roles.md](roles.md), 항목 단위 배정은 [ownership.md](ownership.md)를 따른다.

## 5. 협업 규칙

- 공통 파일은 동시에 수정하지 않는다. 위 표의 최종 병합자가 병합한다.
- 공동 담당 Task는 앞에 적힌 담당자가 최종 병합 책임을 갖고, 나머지는 자기 소유 계약 영역만 변경한다.
- API, DB, 인증 경계 또는 공유 설정을 바꾸려면 해당 소유자와 사전 합의하고 그 소유자에게 리뷰를 요청한다.
- 모든 PR은 작성자를 제외한 최소 두 명의 승인을 받는다. AI가 작성한 코드도 같다.

PR·브랜치·커밋 규칙 원문은 [구현 컨벤션 7절](../06-architecture/implementation-conventions.md#7-git-협업)이다.
