---
related_documents:
  - ../00-overview/scope.md
  - ../05-specs/README.md
  - ../06-architecture/technology-policy.md
  - adr-index.md
  - adr-backlog.md
  - adr-traceability.md
---

# 맛잇온 Architecture Decision Records

## 1. 문서 목적

이 디렉터리는 맛잇온의 장기적이고 변경 비용이 큰 기술·아키텍처 결정을 기록한다. 기능 범위는 [docs/00-overview/scope.md](../00-overview/scope.md), 외부 계약과 데이터 정의는 `docs/05-specs/`가 소유한다.

## 2. 기술 스펙과 ADR의 관계

`맛잇온 기술 스펙 결정`에서 고정·확정된 항목은 MVP 범위와 대조한 뒤 Accepted ADR로 전환한다. 반복 규칙은 [../06-architecture/technology-policy.md](../06-architecture/technology-policy.md), 조건부·Post-MVP·충돌 항목은 [adr-backlog.md](adr-backlog.md)에서 관리한다. 기술 스펙의 버전은 재선정하거나 최신화하지 않는다.

## 3. ADR 상태

- `Accepted`: 현재 범위에서 승인되어 구현 기준으로 사용한다.
- `Proposed`: 검토 중이며 구현 기준이 아니다.
- `Conditional`: 명시된 활성화 조건 충족 전에는 도입하지 않는다.
- `Post-MVP`: 현재 MVP 범위 밖이며 범위 변경 후 검토한다.
- `Rejected`: 검토 후 채택하지 않았다.
- `Superseded`: 후속 ADR로 대체됐다.

이 저장소는 `Conditional`과 `Post-MVP`를 정식 상태로 사용한다. Backlog 항목은 개별 ADR 파일을 만들기 전의 결정 후보이며 Accepted ADR 목록에 포함하지 않는다.

## 4. ADR ID 규칙

형식은 `ADR-{CATEGORY}-{NNN}`이다. 번호는 카테고리 안에서 증가하며 삭제된 번호를 재사용하지 않는다. 파일명은 `{소문자-category}-{번호}-{kebab-case-slug}.md`를 사용하고, 결정의 주 책임 영역 디렉터리에 둔다. 파일을 이동해도 ADR ID는 바꾸지 않는다.

## 5. ADR 카테고리

| 카테고리 | 의미 |
|---|---|
| `LANG` | 언어·런타임 |
| `BUILD` | 빌드·의존성 체계 |
| `FRAME` | 애플리케이션 프레임워크 |
| `WEB` | 프론트엔드 런타임·구조 |
| `ARCH` | 애플리케이션 구조·경계 |
| `DATA` | 데이터베이스·데이터 접근·마이그레이션 |
| `AUTH` | 인증·인가·세션 |
| `EXT` | 외부 서비스 연동 |
| `TEST` | 테스트 전략 |
| `OBS` | 로그·관측성 |
| `SEC` | 비밀정보·워크로드 인증 |
| `RUNTIME` | 실행·컨테이너 환경 |
| `CI` | 지속적 통합·배포 검증 |

### 디렉터리 구조

```text
docs/07-adr/
├─ README.md
├─ adr-index.md
├─ adr-backlog.md
├─ adr-traceability.md
├─ platform/
├─ architecture/
├─ data/
├─ security/
├─ integration/
└─ quality/
```

| 디렉터리 | 책임 영역 | 포함 카테고리 |
|---|---|---|
| `platform/` | 언어, 빌드, 프레임워크, 웹 런타임, 컨테이너, CI | LANG, BUILD, FRAME, WEB, RUNTIME, CI |
| `architecture/` | 애플리케이션 구조와 의존 경계 | ARCH |
| `data/` | 데이터베이스, ORM, 마이그레이션, 데이터 저장소 | DATA |
| `security/` | 인증·인가, 비밀정보, 워크로드 신원 | AUTH, SEC |
| `integration/` | 외부 서비스·제공자 연동 | EXT |
| `quality/` | 테스트, 로그, 관측성 | TEST, OBS |

각 하위 디렉터리의 `README.md`는 해당 영역의 탐색용 목록이다. 전체 상태·우선순위·공식 경로는 루트 [adr-index.md](adr-index.md)만을 기준으로 한다.

## 6. Accepted ADR 작성 기준

- 기술 또는 구조가 확정됐고 현재 MVP 범위와 일치한다.
- 변경 비용이 크거나 여러 Workstream에 영향을 준다.
- 결정 문제, 고려 선택지, 근거, 트레이드오프, 검증과 재검토 조건이 있다.
- 미확정 요구사항이나 운영 수치를 확정값으로 만들지 않는다.

## 7. Conditional 및 Post-MVP ADR 기준

- Conditional은 구체적 활성화 조건과 도입 전 검증을 가진다.
- Post-MVP는 상위 범위 문서의 제외 기능과 연결한다.
- 둘 다 조건 충족 전 의존성, 설정, 스키마 또는 선행 구조를 추가하지 않는다.
- 활성화할 때 개별 ADR을 작성하거나 Backlog 항목을 Accepted로 전환하고 추적성을 갱신한다.

## 8. 기술 정책과 ADR의 구분

ADR은 하나의 결정 문제와 선택 이유를 기록한다. 버전 범위 금지, 환경 분리, BOM 사용, 비밀정보 커밋 금지처럼 여러 결정에 반복 적용되는 규칙은 기술 정책에서 관리한다. 운영 수치와 변경이 쉬운 설정은 ADR이 아니라 운영 설정 대상으로 둔다.

## 9. 변경 및 대체 절차

Accepted ADR은 내용만 덮어써서 결론을 바꾸지 않는다. 변경 제안은 영향과 테스트 결과를 갖춘 새 ADR을 만들고 기존 ADR의 `superseded_by`와 새 ADR의 `supersedes`를 연결한다. 상태 변경 시 [adr-index.md](adr-index.md), [adr-traceability.md](adr-traceability.md)와 기술 정책을 함께 갱신한다.

## 10. 현재 ADR 목록

현재 상태와 경로의 기준은 [ADR 인덱스](adr-index.md)다. 조건부·Post-MVP·범위 충돌은 [ADR Backlog](adr-backlog.md), 기술·요구사항 연결은 [ADR 추적성](adr-traceability.md)을 따른다.
