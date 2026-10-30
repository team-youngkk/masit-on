---
id: ADR-WEB-008
title: Next.js 16.4.3 보안 기준선
status: Accepted
decision_date: 2026-10-30
owners:
  - 양성훈
  - 김인안
related_documents:
  - web-001-frontend-platform.md
  - web-007-next-security-patch-baseline.md
  - ../../06-architecture/technology-policy.md
  - ci-001-github-actions-quality-gate.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes:
  - ADR-WEB-007
superseded_by: null
---

# ADR-WEB-008 Next.js 16.4.3 보안 기준선

## 1. 결정

Next.js를 정확히 `16.4.3`으로 고정한다. [ADR-WEB-001](web-001-frontend-platform.md)의 프레임워크, Node.js 24.18.0, TypeScript 7.0.2, React 19.2.0 결정은 유지한다. 이 ADR은 [ADR-WEB-007](web-007-next-security-patch-baseline.md)의 16.3.8 보안 기준선을 대체한다. 이전 결정은 당시의 채택 이력으로 보존한다.

## 2. 근거

[PR #446의 프론트 CI](https://github.com/team-youngkk/masit-on/actions/runs/41060291370/job/124078035248)는 빌드 전에 `npm audit --omit=dev --audit-level=high`에서 실패했다. 기존 고정 버전 16.3.8이 다음 Critical 공지의 영향 범위에 포함된다.

- [GHSA-2464-fhr4-w9xw](https://github.com/advisories/GHSA-2464-fhr4-w9xw): 이미지 최적화 SVG 처리의 인증 없는 원격 코드 실행.
- [GHSA-p3m5-59mp-jv5r](https://github.com/advisories/GHSA-p3m5-59mp-jv5r): App Router Server Actions 서비스 거부.
- [GHSA-673q-mjm6-r5xr](https://github.com/advisories/GHSA-673q-mjm6-r5xr): `use cache` typed arrays 처리의 인증 없는 원격 코드 실행.

16.4.3은 감사가 제시한 수정 버전이며, Node `>=20.9.0`과 React `^19.0.0`을 지원한다. CI 감사를 우회하거나 취약한 16.3.8을 유지하는 선택은 채택하지 않는다.

## 3. 적용과 검증

`frontend/package.json`과 `package-lock.json`을 함께 갱신한다. 기존 보안 `overrides`는 감사와 설치 결과를 확인한 뒤 유지한다. 고정 Node 컨테이너에서 `npm ci`, `npm audit --omit=dev --audit-level=high`, `npm run build`를 실행하고 기존 프론트 테스트와 타입 검사를 포함한다. API·DB·기능 동작은 변경하지 않는다.

새 보안 공지나 프레임워크 회귀가 발견되면 수정 버전을 다시 검토한다. 취약한 버전으로 복구하거나 감사 게이트를 낮추지 않는다.
