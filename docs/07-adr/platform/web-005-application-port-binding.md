---
id: ADR-WEB-005
title: 운영 애플리케이션 포트 loopback 바인딩
status: Accepted
decision_date: 2026-08-14
owners:
  - 양성훈
  - 김인안
  - 이우람
related_requirements:
  - NFR-SECURITY-001
  - NFR-AVAILABILITY-001
  - NFR-DEPLOYMENT-002
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - ../../06-architecture/security-boundary.md
  - ../../08-planning/issue-200-application-port-binding.md
  - runtime-001-docker.md
  - web-003-routing-boundary.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-WEB-005 운영 애플리케이션 포트 loopback 바인딩

## 1. 상태

Accepted

어떤 ADR도 대체하지 않는다. [ADR-WEB-003](web-003-routing-boundary.md)은 Accepted를 유지하며 경로 소유권·화면 경로·관리자 인증 순서·인증 상태 복구·상태 확인 경로를 계속 소유한다.

이 문서는 그 문서의 결론을 바꾸지 않고 운영 애플리케이션 포트의 바인딩 주소라는 새 결정만 추가한다. ADR-WEB-003 6.1·6.5절이 정한 "Nginx가 유일한 외부 진입점"과 "`/internal/**`은 인터넷에 공개하지 않는다"를 Nginx 경로 규칙 하나가 아니라 네트워크 계층에서도 성립하게 만드는 보강 결정이다. 따라서 [README 9절](../README.md#9-변경-및-대체-절차)의 대체 절차 대상이 아니다.

## 2. 문제

운영 백엔드와 프론트엔드는 Docker `--network host`로 실행된다. 바인딩 주소가 비어 있으면 Spring Boot와 Next.js가 호스트의 모든 인터페이스에 열릴 수 있고, 보안 그룹이나 호스트 방화벽 규칙 하나만 변경돼도 Nginx를 거치지 않는 `/api/**` 직결과 `/internal/**` 노출이 가능해진다.

## 3. 결정

- 운영 Spring Boot 프로파일의 애플리케이션 주소는 `127.0.0.1`로 고정한다. 관리 포트를 별도로 두지 않으므로 API와 `/internal/**` 상태 확인은 같은 loopback listener 경계를 따른다.
- 운영 Next.js 실행은 `HOSTNAME=127.0.0.1`과 host 네트워크를 유지한다.
- 운영 백엔드의 `SERVER_ADDRESS`·`SERVER_PORT`를 Docker 컨테이너에 전달하지 않는다. 운영 프로파일에는 바인딩 주소를 확장하는 환경 변수 placeholder를 두지 않는다.
- Nginx upstream과 상태 지표 수집 대상은 백엔드 `127.0.0.1:8080`, 프론트엔드 `127.0.0.1:3000`을 사용한다.
- 이 경계를 넓히려면 배포 스크립트, 계약 테스트, Nginx·상태 지표 경로와 이 ADR을 함께 변경하고 소유자 합의를 거친다.

## 4. 선택지와 근거

보안 그룹만으로 8080을 차단하는 방식은 네트워크 설정 변경에 따라 우회 경계가 다시 생기므로 채택하지 않는다. 별도 Docker bridge 네트워크는 초기 운영의 `--network host`, Nginx upstream, 상태 지표 계약을 동시에 바꾸고 운영 복잡도를 늘린다. loopback 고정은 기존 실행 구조를 유지하면서 Nginx 우회 경로를 운영 애플리케이션 계층에서 제거한다.

## 5. 영향

- 인스턴스 외부에서는 애플리케이션 포트에 직접 연결할 수 없고, 외부 진입은 Nginx 경로만 사용한다.
- 운영 배포 후에는 인스턴스 내부에서 loopback health check와 Nginx 경유 요청을 각각 확인해야 한다.
- 인스턴스 간 8080 직결을 전제로 한 성능 측정은 사용할 수 없다. 필요하면 측정 전용 프로파일·실행 방식에 대한 별도 결정을 먼저 추가한다.

## 6. 검증

- `application-prod.yml`의 `server.address`가 `127.0.0.1`인지 확인한다.
- `app-run.sh`가 `SERVER_ADDRESS`·`SERVER_PORT`를 전달하지 않고 backend를 `--network host`로 실행하는지 계약 테스트로 확인한다.
- frontend의 `HOSTNAME`, Nginx upstream, `health-metrics.sh`의 대상이 같은 loopback 주소를 사용하는지 확인한다.
- 운영 배포 후 인스턴스 외부의 8080 직결이 차단되고, Nginx 경유 API·화면·내부 health check가 정상인지 확인한다([운영 애플리케이션 포트 바인딩 계획](../../08-planning/issue-200-application-port-binding.md)).

## 7. 재검토 조건

ALB·서비스 디스커버리·bridge 네트워크로 배포 토폴로지를 바꾸거나, 외부 Load Balancer가 애플리케이션 listener를 직접 호출해야 하거나, 인스턴스 간 성능 측정이 필요해지면 재검토한다.
