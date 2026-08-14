---
status: Implemented
verification_status: pending_deployment
issue: 200
related_documents:
  - ../06-architecture/security-boundary.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/platform/runtime-001-docker.md
  - m2-provisioning-record.md
---

# 애플리케이션 포트 바인딩 경계

## 1. 문서 목적

운영 EC2에서 Spring Boot와 Next.js 애플리케이션 포트를 인터넷에서 도달할 수 없게 만드는 경계가 어떤 계층으로 구성되는지, 그리고 배포 후 그 경계를 어떤 명령으로 확인하는지 기록한다. 이슈 [#200](https://github.com/team-youngkk/masit-on/issues/200)의 결과 문서다.

이 저장소는 공개되어 있다. Elastic IP, 보안 그룹 ID, 작업자 공인 IP는 `<...>` 자리표시자로 마스킹한다. 실제 값은 AWS 콘솔에서 확인한다([운영 프로비저닝 기록](m2-provisioning-record.md) 1절과 같은 규칙이다).

**아래 4절 확인 명령은 이 변경을 운영에 배포한 뒤 실행한다. 작성 시점에는 실행하지 않았고 결과를 기록하지 않았다.**

## 2. 해결한 문제

운영 백엔드와 프론트엔드 컨테이너는 `--network host`로 실행된다([app-run.sh](../../deploy/scripts/app-run.sh)). [ADR-RUNTIME-001](../07-adr/platform/runtime-001-docker.md) 11절이 운영 설정의 Docker 서비스명을 금지하므로 애플리케이션이 저장소에 `127.0.0.1`로 붙어야 하고, Nginx도 `127.0.0.1`로 전달해야 한다.

프론트엔드는 `HOSTNAME=127.0.0.1`로 loopback에만 바인딩하고 있었지만, 백엔드는 바인딩 주소를 지정하지 않아 Spring Boot 기본값대로 호스트의 모든 인터페이스(`0.0.0.0:8080`)에 붙었다. 현재 보안 그룹이 `8080`을 열지 않아 실제 노출은 없었지만, 경계가 보안 그룹 규칙 하나에만 의존하는 상태였다. 규칙이 추가되거나 호스트 방화벽이 바뀌면 다음이 동시에 성립한다.

- Nginx를 건너뛴 `/api/**` 직결. 제한 공개 세션 `auth_request` gate와 유량 제한, `Authorization`·`Cookie` 정리가 모두 적용되지 않는다.
- 인터넷에 공개하지 않기로 한 `/internal/**` 노출. [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md) 6.5절의 상태 확인 세 경로는 애플리케이션 인증이 없고, 네트워크 경계만이 유일한 보호다.

## 3. 경계 구성

인터넷에서 애플리케이션 포트에 도달하려면 세 계층을 모두 통과해야 한다. 어느 한 계층의 변경만으로는 노출이 생기지 않는다.

| 계층 | 대상 | 규칙 | 소유 파일 |
|---|---|---|---|
| 보안 그룹 | 앱 인스턴스 인바운드 | `80`·`443` ← `0.0.0.0/0`, `22` ← `<작업자 공인 IP>/32`. `8080`·`3000`·`6379` 규칙 없음 | AWS 콘솔, [프로비저닝 기록](m2-provisioning-record.md) 3.2절 |
| 호스트 방화벽 | 앱 인스턴스 | Amazon Linux 2023 기본값에서 `firewalld`·`nftables` 규칙을 추가하지 않는다. 필터링은 보안 그룹이 담당한다 | 인스턴스 OS 상태. 저장소·콘솔 어디에도 선언이 없으므로 4.1절로 관측한다 |
| 애플리케이션 바인딩 | Spring Boot, Next.js | 애플리케이션 포트를 loopback에만 바인딩한다 | [application-prod.yml](../../src/main/resources/application-prod.yml), [app-run.sh](../../deploy/scripts/app-run.sh) |

애플리케이션 바인딩 계층의 실제 값은 다음과 같다.

| 프로세스 | 포트 | 바인딩 | 선언 위치 |
|---|---|---|---|
| Spring Boot | `8080` | `127.0.0.1` | `application-prod.yml`의 `server.address` |
| Next.js | `3000` | `127.0.0.1` | `app-run.sh` frontend 분기의 `HOSTNAME` |
| Redis | `6379` | 호스트 노출이 `127.0.0.1` | [masiton-redis.service](../../deploy/redis/masiton-redis.service)의 `--publish 127.0.0.1:6379:6379` |

Redis만 방식이 다르다. 애플리케이션 두 개는 `--network host`라 프로세스 바인딩 자체를 좁혀야 하지만, Redis는 브리지 네트워크에서 `--publish 127.0.0.1:6379:6379`로 실행되므로 [redis.conf](../../deploy/redis/redis.conf)의 `bind 0.0.0.0`은 컨테이너 내부 인터페이스를 뜻하고 호스트 밖으로 나가는 경로는 loopback 하나다.

`server.address`에는 환경 변수 placeholder를 두지 않는다. 이 값을 넓히는 것은 설정 실수로 일어날 일이 아니라 경계 변경이므로 ADR-WEB-003과 함께 바꿔야 한다.

**`server.address` 하나로 `/internal/**`까지 덮이는 것은 상태 확인이 애플리케이션 커넥터에 같이 올라가 있기 때문이다.** 공통 계층이 Actuator를 `base-path: /internal`로 두고 `management.server.port`를 선언하지 않으므로 listener가 하나뿐이다([application.yml](../../src/main/resources/application.yml)). 관리 포트를 분리하면 `server.address`가 그 listener를 덮지 않아 `/internal/**`이 다시 전 인터페이스에 열린다. 관리 포트를 도입하려면 `management.server.address`도 같이 loopback으로 고정한다.

운영 부하 측정에도 제약이 따른다. `app-run.sh`는 `SPRING_PROFILES_ACTIVE=prod`를 고정하므로, 배포 산출물로 띄운 인스턴스는 비loopback 주소로 8080에 도달하지 않는다. [ADR-PERF-001](../07-adr/quality/perf-001-k6-load-testing.md) 계열 문서가 기술한 `BASE_URL=http://<측정-대상>:8080` 외부 직결 측정은 이 인스턴스에 성립하지 않는다. 인스턴스 안에서 loopback으로 측정하거나([ADR-PERF-002](../07-adr/quality/perf-002-operational-participant-load-testing.md)), 측정 전용 인스턴스를 `docker-compose.yml`의 `local` 프로파일로 띄운다. **어느 쪽을 쓸지는 측정 절차 소유자가 정할 사항이며 이 변경에서 정하지 않았다.**

`--network host`이므로 컨테이너 안의 `127.0.0.1`은 호스트 loopback과 같다. 8080에 접근하는 정당한 호출자는 모두 같은 인스턴스의 loopback을 사용한다.

| 호출자 | 대상 |
|---|---|
| Nginx `/api/**` upstream | `127.0.0.1:8080` ([masiton.click.conf](../../deploy/nginx/masiton.click.conf)) |
| Next.js Server Component | `API_BASE_URL=http://127.0.0.1:8080` |
| 상태 지표 수집 | `HEALTH_BASE=http://127.0.0.1:8080` ([health-metrics.sh](../../deploy/scripts/health-metrics.sh)) |
| 배포 후 Smoke Test | `http://127.0.0.1:8080` ([app-deploy.sh](../../deploy/scripts/app-deploy.sh)) |
| Nginx 전환 전 gate | `http://127.0.0.1:8080/internal/verification/session` ([nginx-install.sh](../../deploy/scripts/nginx-install.sh)) |
| 운영 부하 측정 | `http://127.0.0.1:8080` ([ADR-PERF-002](../07-adr/quality/perf-002-operational-participant-load-testing.md)) |

Nginx 전환 전 gate는 단순 호출이 아니다. 그 확인이 `401`을 받지 못하면 전환이 중단되고 Basic Auth로 되돌아간다. 바인딩이나 실행 네트워크를 바꿀 때 이 경로를 함께 확인한다.

`/internal/**`은 이 loopback 경로에서만 도달하며, Nginx는 인터넷 진입점에서 `404`로 끊는다.

## 4. 배포 후 확인 명령

앱 EC2에서 SSM으로 실행한다. `<앱 EIP>`는 인스턴스의 Elastic IP다.

### 4.1. 바인딩 주소 확인

**이 절이 바인딩 계층의 유일한 직접 증거다.** 아래 명령이 `8080`·`3000`·`6379` 세 줄을 모두 출력하고, 세 줄의 `Local Address`가 전부 `127.0.0.1`이어야 통과다.

- `0.0.0.0`·`*`·`[::]`가 나오면 실패다.
- **줄이 하나라도 빠지면 실패다.** 출력이 비어 있는 것은 통과가 아니다. 프로세스가 죽어 있을 때도 `0.0.0.0`이 나오지 않으므로, "위반 문자열이 없다"를 기준으로 삼으면 애플리케이션이 내려간 상태를 통과로 기록한다.

```bash
ss -tlnp | grep -E ':(8080|3000|6379)\s'
```

### 4.2. loopback 경로 정상 동작 확인

바인딩을 좁힌 뒤에도 인스턴스 안의 정당한 호출은 그대로 성립해야 한다. 세 상태 확인이 `UP`을 반환해야 한다.

```bash
curl -sS http://127.0.0.1:8080/internal/health/live
```

```bash
curl -sS http://127.0.0.1:8080/internal/health/ready
```

```bash
curl -sS http://127.0.0.1:8080/internal/health/dependencies
```

### 4.3. Nginx 경유 정상 동작 확인

Nginx가 `127.0.0.1:8080`으로 전달하므로 공개 화면과 `/api`가 평소대로 응답해야 한다. 제한 공개 gate가 켜져 있는 동안 `/api`는 검증 세션 쿠키가 없으면 `401` 계약 JSON이고, 이는 백엔드에 도달했다는 증거다.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://masiton.click/api/restaurants
```

### 4.4. 인터넷 직결 차단 확인

인스턴스 밖(작업자 로컬)에서 실행한다. 두 명령 모두 연결이 성립하지 않아야 한다. **`200`이나 JSON 본문이 돌아오면 실패다.**

```bash
curl -sS -m 5 -o /dev/null -w 'exit=%{exitcode}\n' http://<앱 EIP>:8080/internal/health/live
```

```bash
curl -sS -m 5 -o /dev/null -w 'exit=%{exitcode}\n' http://<앱 EIP>:3000/
```

**이 두 명령만으로는 어느 계층이 막았는지 알 수 없다.** 현재 보안 그룹에 `8080`·`3000` 인바운드 규칙이 없으므로 SYN이 인스턴스에 닿기 전에 버려지고, 결과는 이 변경 전과 후가 같다. 즉 이 절은 바인딩 계층의 증거가 아니라 보안 그룹 계층의 회귀 확인이다. 관측한 curl 종료 코드를 기록해 어느 계층이 응답했는지 남긴다.

| 종료 코드 | 의미 |
|---|---|
| `28` | 시간 초과. 보안 그룹이나 호스트 방화벽이 호스트 도달 전에 버렸다 |
| `7` | 연결 거부. 호스트까지 도달했고 그 주소에 listener가 없다(바인딩이 loopback 전용) |
| `0` | 응답을 받았다. **실패다.** 어떤 계층도 막지 않았다 |

바인딩 계층만 분리해 확인하려면 보안 그룹이 개입하지 않는 경로로 시험한다. 인스턴스 안에서 자신의 비loopback 사설 주소로 호출하면 트래픽이 인스턴스를 떠나지 않는다. 바인딩이 loopback 전용일 때만 연결 거부이고, `0.0.0.0`이면 `200`이 돌아온다.

```bash
curl -sS -m 5 -o /dev/null -w 'exit=%{exitcode}\n' "http://$(hostname -I | awk '{print $1}'):8080/internal/health/live"
```

### 4.5. 상태 확인 경로의 외부 차단 확인

Nginx 진입점에서 `/internal/**`이 `404`여야 한다. 경로의 존재 여부도 드러내지 않는다.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://masiton.click/internal/health/live
```

## 5. 회귀 방지

`AppRunScriptContractTest`가 다음 지점을 한 테스트에서 대조한다. 어느 한쪽만 바뀌면 빌드가 실패한다.

- `application-prod.yml`의 `server.address`가 `127.0.0.1`이다.
- `docker run`에 `SERVER_ADDRESS`·`SERVER_PORT`를 전달하지 않는다. 환경 변수 property source가 패키징된 프로파일보다 우선하므로, placeholder를 뺀 것만으로는 값이 고정되지 않는다.
- `app-run.sh` 두 분기의 `docker run`이 `--network host`를 쓴다. 브리지로 바뀌면 컨테이너 안의 loopback이 호스트와 달라져 Nginx가 도달하지 못한다.
- frontend 분기가 `HOSTNAME=127.0.0.1`을 export하고 컨테이너에 전달한다. 없으면 Next.js standalone 서버가 모든 인터페이스에 붙는다.
- Nginx upstream이 `127.0.0.1:8080`과 `127.0.0.1:3000`이다.
- `health-metrics.sh`의 기본 대상이 `http://127.0.0.1:8080`이다.

바인딩만 좁히고 실행 네트워크나 프록시 대상을 옮기면 인터넷 차단은 유지되지만 정상 요청과 상태 지표가 끊긴다. 두 방향의 회귀를 같이 막는 것이 이 테스트의 목적이다.

보안 그룹과 호스트 방화벽은 저장소 산출물이 아니므로 테스트로 고정할 수 없다. 4.4절 확인을 운영 변경 뒤마다 수행한다.
