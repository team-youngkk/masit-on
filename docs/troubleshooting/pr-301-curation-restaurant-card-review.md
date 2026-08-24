---
related_documents:
  - ../04-product/traceability.md
  - ../04-product/prd/curation/admin-curation.md
  - ../05-specs/api/curation/curation-api.md
  - ../08-planning/curation-restaurant-card-fix.md
  - pr-142-public-curation-review.md
  - README.md
---

# PR #301 리뷰 트러블슈팅: 큐레이션 범위 밖 카드 요소 제거

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#301 큐레이션 구성 맛집 카드를 탐색 카드로 보완](https://github.com/team-youngkk/masit-on/pull/301) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-24 |
| 범위 | `w00lam`의 미해결 리뷰 2건과 승인 리뷰의 반영 상태 확인 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #142 공개 큐레이션 조회 계약과 화면 상태 보완](pr-142-public-curation-review.md), [공개 큐레이션 구성 맛집 카드 보완](../08-planning/curation-restaurant-card-fix.md)을 확인했다. PR #142의 공개 큐레이션 식별자·오류·표시 계약은 유지하고, 이번에는 WS-11 범위 경계를 추가로 반영했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [이미지 범위 제외](https://github.com/team-youngkk/masit-on/pull/301#discussion_r3840271074) | 큐레이션 확정 범위에서 제외된 이미지 UI를 추가하지 말 것 | 애플리케이션 | 수정 필요 | placeholder 이미지와 이미지 렌더링 CSS를 제거하고 텍스트 카드만 유지 | `docs/04-product/traceability.md`의 WS-11 범위, 구두 합의, 프런트 전체 테스트·타입 검사·빌드 |
| [찜 범위 제외](https://github.com/team-youngkk/masit-on/pull/301#discussion_r3840271078) | WS-06 개인 찜·로그인 CTA를 공개 큐레이션 카드에 결합하지 말 것 | 애플리케이션 | 수정 필요 | `FavoriteButton`과 로그인 진입 경로를 제거하고 상세 이동만 유지 | PRD의 개인화·개인 컬렉션 제외 범위, 큐레이션 API 필드 계약, 프런트 전체 테스트·타입 검사·빌드 |
| [승인 리뷰](https://github.com/team-youngkk/masit-on/pull/301#pullrequestreview-5004058190) | 공개 API 계약 안에서 카드·상세 이동·오류 처리·식별자 인코딩을 확인 | 애플리케이션 | 이미 해결 | 기존 이름·주소·상세 링크와 오류/빈 상태·불투명 ID 인코딩을 유지 | `npm.cmd test`, `npm.cmd run typecheck`, `npm.cmd run build` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음
- 발생 환경: Next.js 16.2.11, 브랜치 `fix/curation-restaurant-card`, 공개 큐레이션 상세 화면
- 재현 조건: 큐레이션 상세의 구성 맛집이 1개 이상이고, 이미지 또는 회원 찜 상태를 화면에 표시하는 경우
- 실제 결과: WS-11 공개 큐레이션 화면에 범위 밖 placeholder 이미지와 개인화 찜/로그인 동작이 추가되어 제품 추적표·PRD 범위와 구현이 어긋났다.
- 기대 결과: 공개 큐레이션 구성 맛집은 API 계약의 `restaurantId`, `name`, `roadAddress`와 상세 이동만 사용한다. 이미지·카테고리·방문 유튜버·찜 상태는 표시하지 않는다.
- 영향 범위: 큐레이션 공개 화면의 제품 범위, 개인화 API 호출 수, WS-06·WS-11 도메인 경계

## 4. 근본 원인

초기 구현에서 사용자가 제공한 맛집 탐색 카드 참고 이미지를 큐레이션 카드에도 그대로 확장하면서, WS-11의 확정 제외 범위인 이미지와 개인화 찜을 기존 Restaurant 화면의 공통 표시 요소로 잘못 해석했다. API 응답이 `restaurantId`, `name`, `roadAddress`만 제공한다는 계약을 확인했지만, 화면 범위 추적표와 구두 합의를 구현 판단의 우선 제약으로 연결하지 못한 것이 원인이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `gh api graphql`로 PR #301의 리뷰·미해결 스레드 조회 | `w00lam`의 미해결 스레드 2건, `jinyp01` 승인 리뷰 1건 확인 | 두 변경 요청을 모두 수정 대상으로 분류 |
| `docs/04-product/traceability.md` WS-11 범위 대조 | 관리자 큐레이션 범위에서 이미지가 제외됨 | 이미지 블록·placeholder import 제거 |
| `docs/04-product/prd/curation/admin-curation.md`와 `docs/05-specs/api/curation/curation-api.md` 대조 | 개인화·개인 컬렉션 재사용은 제외되고 공개 item은 세 필드만 제공 | `FavoriteButton`·로그인 CTA 제거, 상세 링크만 유지 |
| 기존 `pr-142-public-curation-review.md` 확인 | 공개 큐레이션의 불투명 식별자·오류·빈 상태 처리 근거 확인 | 기존 동작을 보존하고 카드 표현만 축소 |

## 6. 최종 해결

- 변경 내용: 큐레이션 구성 맛집 카드에서 placeholder 이미지·이미지 CSS·`FavoriteButton`·로그인 진입점을 제거하고, 이름·도로명 주소·맛집 상세 링크만 표시하도록 수정했다.
- 선택 이유: 구두로 합의된 WS-11 범위를 코드에 반영하고, 공개 큐레이션 API 계약과 WS-06 개인화 경계를 유지하기 위해서다.
- 변경 파일: `frontend/app/curations/[curationId]/page.tsx`, `frontend/app/curations/curations.module.css`, `docs/08-planning/curation-restaurant-card-fix.md`, `docs/troubleshooting/pr-301-curation-restaurant-card-review.md`, `docs/troubleshooting/README.md`
- 고려한 대안: 이미지·찜을 유지하려면 WS-11·WS-06 소유자 합의와 제품 추적표·PRD·API 계약을 먼저 변경해야 하므로 이번 PR에서는 선택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` (`frontend`) | 통과 | 프런트 전체 311건 |
| `npm.cmd run typecheck` (`frontend`) | 통과 | 큐레이션 상세의 서버/클라이언트 경계와 CSS module 참조 |
| `npm.cmd run build` (`frontend`) | 통과 | Next.js 운영 빌드 및 큐레이션 상세 라우트 생성 |
| `git diff --check` | 통과 | 코드·문서 공백 오류 없음 |
| `rg -n "FavoriteButton|getRestaurantPlaceholderImage|cardMedia" frontend/app/curations` | 통과 | 큐레이션 상세 범위에서 개인화·이미지 요소 미참조 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 큐레이션 카드 구현 문서에 이미지·개인화·카테고리·방문 유튜버를 임의로 추가하지 않는 계약 경계를 명시했다. 새 카드 요소를 추가할 때 WS-11 추적표·PRD·API 계약과 먼저 대조한다.
- 다음 확인: 없음. 이미지나 찜이 향후 필요하면 김인안(WS-11)과 관련 개인화 소유자의 합의 후 별도 범위 변경으로 추적한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---:|---|---:|---|---|
| WS-11 큐레이션 상세의 범위 밖 이미지·개인화 UI 요소 | 2개 | PR diff와 큐레이션 상세 소스의 요소/import 검색, PR #301 처리 시점 | 0개 | 확정 범위 밖 요소 제거 | `tjdgns0618`, PR #301 반영 직후 |

## 10. 남은 사항

없음. `w00lam`의 미해결 변경 요청 2건을 반영하고 원격 브랜치에 푸시했으며, 각 원래 인라인 스레드에 답글과 해결 처리를 완료했다. 동일 내용을 요약 리뷰로 남긴 `inan0226`의 검토에도 반영 커밋과 기록을 연결했다.
