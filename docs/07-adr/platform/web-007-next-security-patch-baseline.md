---
id: ADR-WEB-007
title: Next.js 16.3.8 보안 패치 기준선
status: Proposed
decision_date: 2026-10-01
owners:
  - 양성훈
  - 김인안
related_documents:
  - web-001-frontend-platform.md
  - ci-001-github-actions-quality-gate.md
  - ../../06-architecture/technology-policy.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-WEB-007 Next.js 16.3.8 보안 패치 기준선

## 1. 상태와 범위

Proposed — 사용자에게 별도 보안 패치와 버전 정책 갱신의 진행 승인을 받았으며, 병합 전 프론트·보안 소유자 리뷰를 요청한다. 팀 승인을 이미 받았다는 뜻은 아니다. [ADR-WEB-001](web-001-frontend-platform.md)의 프레임워크 선택·Node·TypeScript·React 결정은 유지하고 Next.js 패치 기준선만 16.3.4에서 16.3.8로 갱신하는 변경안이다. 과거 보안 패치 이력은 삭제하지 않는다.

## 2. 배경과 근거

[PR #395의 최초 CI](https://github.com/team-youngkk/masit-on/actions/runs/36841080899/job/110300084167)는 기능 빌드 전에 보안 감사에서 실패했다. 원인은 기존 Next.js 16.3.4이며 해당 기능은 의존성이나 lockfile을 변경하지 않았다.

- [GHSA-vcvr-r3jv-pc5j](https://github.com/advisories/GHSA-vcvr-r3jv-pc5j)는 Node.js `next/og` ImageResponse의 취약한 의존성으로 인한 Critical 원격 코드 실행 문제다. 영향 범위는 `>=16.2.0 <16.3.6`이며 수정은 16.3.6이다.
- [공식 16.3.8 릴리즈](https://github.com/vercel/next.js/releases/tag/v16.3.8)는 이미지 최적화 SSRF 등 추가 보안 수정을 포함한다. 최소 수정판 16.3.6 대신 같은 minor의 16.3.8로 정확히 고정한다.
- npm의 16.3.8 manifest는 Node `>=20.9.0`, React·React DOM `^19.0.0`을 지원한다. 현재 Node 24.18.0과 React 19.2.0은 이 범위에 포함된다. 선언상 호환성과 실제 회귀 검증은 구분한다.

소스 검색에서는 `next/og`·`ImageResponse` 직접 사용을 찾지 못했지만 이것만으로 안전을 단정하거나 감사를 예외 처리하지 않는다.

## 3. 결정안과 대안

`next`를 범위 없는 `16.3.8`로 변경하고 잠금 파일을 재생성한다. Node 24.18.0, TypeScript 7.0.2, React 19.2.0과 기존 보안 override는 유지하고 Next 자체의 전이 의존성만 함께 갱신한다.

감사 임계값 완화·검사 생략·`npm audit fix --force`의 무차별 적용은 채택하지 않는다. 지역 기능과 무관한 보안 변경은 별도 커밋·PR로 리뷰하되 기능 PR에도 동일 패치를 적용해 통합 검증한다. 공개 API·DB·기능 정책은 변경하지 않는다.

## 4. 검증과 적용

고정 Node 컨테이너에서 `npm ci`, `npm audit --omit=dev --audit-level=high`, `npm run build`를 실행한다. Next·TypeScript·React·sharp 버전과 lockfile 변경 범위를 확인하고 기존 프론트 테스트·타입 검사·프로덕션 빌드를 유지한다. 실제 브라우저 인수와 운영 배포는 별도 절차이며 로컬/CI 통과를 운영 적용으로 보고하지 않는다.

## 5. 복구와 후속 조건

회귀가 발견되면 배포를 보류하고 수정된 패치 버전을 검토한다. 취약한 16.3.4를 장기 복구 기준선으로 재고정하거나 감사 게이트를 우회하지 않는다. 긴급 롤백은 기존 운영 승인 절차로 영향·보완 통제를 먼저 판단한다. 소유자 승인 후 상태와 인덱스를 함께 확정한다. 새 보안 공지, 고정 런타임 회귀 또는 기존 override를 제거할 수 있는 upstream 갱신이 재검토 조건이다.
