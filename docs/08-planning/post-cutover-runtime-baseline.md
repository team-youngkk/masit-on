---
status: Measured
measured_date: 2026-08-19
owners:
  - 이우람
related_documents:
  - deployment-hardening-impact-review.md
  - deployment-hardening-cutover-record.md
  - third-expansion-final-gate-result.md
  - issue-190-operational-performance-result.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../troubleshooting/pr-257-runtime-baseline-review.md
---

# 전환 후 런타임 실측 기준선

## 1. 문서 목적

2026-08-19 ALB·Blue-Green 전환 직후 운영 인스턴스와 전용 Redis에서 **부하를 주지 않고** 수집한 실측값을 남긴다.

목적은 셋이다.

1. [비용·일정 영향 검토](deployment-hardening-impact-review.md) 4.3절이 인스턴스 하향의 선행 조건으로 남긴 **RSS 실측**과 **JVM heap 고정 여부 결정**을 채운다. 하향은 이미 수행됐으므로 사후 확인이다.
2. [3차 확장 최종 게이트](third-expansion-final-gate-result.md) 4절 3번의 부하 측정을 설계하기 위한 기준선을 만든다. [#190 운영 직접 관찰](issue-190-operational-performance-result.md)은 t4g.medium 단일 인스턴스에서 수집해 현재 구성에 쓸 수 없다.
3. 실측 과정에서 드러난, 계획 문서에 없던 사실을 기록한다.

측정은 읽기 전용 조회와 SSM 명령으로 수행했고 부하를 발생시키지 않았다. 인스턴스 ID와 사설 IP는 `<...>`로 마스킹했다.

## 2. 앱 인스턴스 실측

t4g.small, 배포 `d-EGYARQWBK`로 기동한 인스턴스에서 수집했다.

| 항목 | 값 |
|---|---|
| 호스트 메모리 | 총 1,846 MB / 사용 891 MB / **available 787 MB** |
| swap | 없음 |
| backend 컨테이너 | 398.4 MiB / 1 GiB 제한 (38.9%) |
| frontend 컨테이너 | 64.4 MiB / 512 MiB 제한 (12.6%) |
| JVM heap 사용 | 약 84 MB (new 28 MB + tenured 56 MB), committed 약 124 MB |
| Metaspace | 138 MB (committed 140 MB) |
| load average | 0.00 / 0.04 / 0.01 |

### 2.1. heap 하향 우려는 전제가 틀렸다

```
MaxHeapSize         = 268435456   (256 MB)
MaxRAMPercentage    = 25.0
UseContainerSupport = true
```

`UseContainerSupport`가 켜져 있으므로 `MaxRAMPercentage`의 기준은 호스트가 아니라 **컨테이너 메모리 제한**이다. [app-run.sh](../../deploy/scripts/app-run.sh)가 backend를 `--memory 1024m`로 고정하므로 heap 상한은 그 25%인 256 MB이고, **호스트가 4 GiB(medium)든 2 GiB(small)든 같은 값이다.**

[비용 검토 4.1절](deployment-hardening-impact-review.md)은 "heap 1 GiB는 현재 설정의 결과이며 호스트를 2 GiB로 내리면 512 MB가 된다"고 적었다. **호스트 기준으로 계산한 오류다.** 실제로는 하향 전후 모두 256 MB였고, 같은 절이 산정한 JVM 상주 1,400 MB → 900 MB 변화도 성립하지 않는다.

따라서 4.3절이 남긴 **"JVM heap 명시 고정 여부 결정"은 별도 조치가 필요 없다.** 컨테이너 메모리 제한이 이미 고정 역할을 하고 있으며, `Dockerfile`을 바꾸지 않아도 호스트 크기 변화가 heap을 움직이지 않는다. 다만 **`--memory` 값을 바꾸면 heap이 따라 바뀐다**는 점은 남는다. 그 값이 실질적인 heap 정책이라는 사실을 `app-run.sh`를 수정할 때 인지해야 한다.

### 2.2. GC가 SerialGC다

heap 출력이 `def new generation` / `tenured generation` 형태다. **SerialGC**이며 G1이 아니다.

HotSpot은 사용 가능 메모리가 1,792 MB 미만이면 서버급으로 분류하지 않고 SerialGC를 선택한다. 컨테이너 제한 1 GiB가 그 조건에 걸린다. 이 역시 호스트 크기와 무관하므로 하향으로 생긴 변화가 아니라 **원래부터 그랬다.**

SerialGC의 full GC는 단일 스레드 stop-the-world다. 평상시 CPU 2% 구간에서는 드러나지 않지만 최대 부하에서는 지연 스파이크로 나타날 수 있다. **부하 측정에서 GC 로그를 함께 수집해야 지연의 원인을 GC와 애플리케이션으로 구분할 수 있다.**

### 2.3. CPU 크레딧

t4g는 버스터블이고 [AWS CPU 크레딧 표](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-credits-baseline-concepts.html)에 따르면 t4g.small과 t4g.medium은 모두 2 vCPU, 인스턴스당 시간당 24 크레딧(= vCPU당 시간당 12 크레딧)을 얻는다. **두 인스턴스 크기의 적립률은 같다.** 검토 4.3절이 미확인으로 남긴 항목이다.

| 시각(KST) | CPU 평균 | `CPUCreditBalance` | `CPUSurplusCreditBalance` |
|---|---:|---:|---:|
| 21:20 | 2.85% | 0.75 | **0.49** |
| 21:35 | 2.66% | 5.40 | 0 |
| 21:50 | 2.30% | 10.62 | 0 |
| 22:05 | 2.19% | 15.96 | 0 |

**기동 직후 잉여 크레딧을 사용한 구간이 있다.** 새 인스턴스는 크레딧 0에서 시작하는데 부팅·이미지 pull·JVM 기동·Flyway가 겹치기 때문이다. 이후 저부하에서 15분마다 약 5씩 회복했으며, 이는 24크레딧/시간의 이론상 15분당 최대 6크레딧과 같은 방향의 관측이다.

부하 측정에 직접 영향이 있다. **크레딧이 없는 갓 기동한 인스턴스에 최대 부하를 걸면 측정 대상이 애플리케이션 성능이 아니라 크레딧 고갈이 된다.** warmup과 크레딧 회복 대기를 측정 절차에 포함해야 한다.

## 3. 전용 Redis 실측

t4g.nano, 컨테이너 제한 384 MB, `maxmemory 256mb`.

| 항목 | 값 |
|---|---|
| `used_memory` | **1.78 MB** (maxmemory 대비 0.7%) |
| `used_memory_peak` | 1.80 MB |
| `used_memory_rss` | 13.59 MB |
| `mem_fragmentation_ratio` | 7.71 |
| `maxmemory_policy` | `noeviction` |
| `evicted_keys` / `rejected_connections` | 0 / 0 |
| 키 | 18개, **전부 만료 설정됨**(`expires=18`), 평균 TTL 약 13일 |
| AOF / RDB | `aof_last_write_status:ok`, `aof_last_bgrewrite_status:ok`, `rdb_last_bgsave_status:ok` |

단편화 비율 7.71은 높아 보이지만 데이터가 1.78 MB뿐이라 allocator·스레드 스택 같은 고정 오버헤드가 비율을 키운 결과다. RSS 13.59 MB 자체가 작다.

### 3.1. 메모리 고갈까지의 거리

`maxmemory 256mb` + `noeviction`은 한도에 닿으면 **키를 밀어내지 않고 쓰기에 오류를 반환**한다. 그 상태에서 막히는 것은 세션 저장·토큰 재발급·코스 quota permit 소비·rate limit `INCR`이다.

가장 빠르게 늘어날 수 있는 키는 rate limit이며 출처를 SHA-256으로 해싱한 64자 문자열에 60초 TTL이다. 키 하나를 오버헤드 포함 200~250 바이트로 보면 **256 MB를 채우는 데 약 100만 개**가 필요하고, 60초 창이므로 **초당 약 1만 7천 개의 서로 다른 출처**가 유입돼야 한다.

세션은 계열 6종에 세션당 1 KB 남짓으로 보면 25만 세션 규모다.

**부하 측정은 출처 수가 적으므로 이 경로로 한도에 닿지 않는다.** 현실적인 도달 경로는 대규모 분산 유입이나, 만료 없는 키 계열이 새로 추가되는 코드 변경이다.

### 3.2. 그래서 실제 위험은 가용성 쪽이다

메모리 여유는 충분하지만 다음 셋은 남는다.

1. **단일 장애점.** replica가 없다. 이 인스턴스가 멈추면 로그인·토큰 재발급·코스 quota가 동시에 멈춘다. AOF로 데이터는 보존되지만 그동안은 정지다.
2. **장애가 배포까지 막는다.** `masiton-prod-fleet-dependency-redis` 알람이 `treat_missing_data = breaching`이고 deployment group의 `ignore_poll_alarm_failure = false`다. **Redis가 멈추면 그것을 고치려는 배포도 `DEPLOYMENT_STOP_ON_ALARM`으로 막힌다.** [전환 기록](deployment-hardening-cutover-record.md) 4.3절의 순환과 같은 계열이며, 그때 사용한 `deployment_alarms_enabled` 완화가 장애 시에도 필요해진다.
3. **정책이 키 계열을 구분하지 않는다.** 세션·quota는 evict되면 안 되므로 `noeviction`이 맞는 선택이다. 그러나 rate limit 키도 같은 정책을 공유하므로, 예상 못 한 키 폭증에서 **버려도 되는 데이터 때문에 버리면 안 되는 쓰기가 막힌다.**

### 3.3. 감시가 없다

현재 배포 게이트 알람 4종 중 Redis 관련은 `FleetDependencyRedis` 하나이며 이는 **연결 가능 여부**만 본다. `used_memory`가 한도에 접근하는 것을 알리는 지표·알람이 없어, 3.1절의 경로로 한도에 닿으면 **쓰기가 막힌 뒤에야 알게 된다.**

현재 사용률이 0.7%로 확인됐으므로 임계값을 잡기 쉬운 시점이다.

## 4. 실측 과정에서 드러난 사실

### 4.1. 전용 Redis는 SSM 관리 인스턴스가 아니다

`aws ssm send-command`가 `InvalidInstanceId: Instances not in a valid state for account`로 거부된다. `describe-instance-information`에 앱 인스턴스만 있고 Redis는 없다.

[endpoints.tf](../../infra/production/terraform-redis/endpoints.tf)가 만든 인터페이스 엔드포인트는 `ssm` 하나다. Parameter Store 조회는 그것으로 되므로 기동과 `requirepass` 렌더링은 정상이지만, **RunCommand·Session Manager는 `ssmmessages` 엔드포인트를 함께 요구**한다. 그래서 관리 인스턴스로 등록되지 못한다.

현재 접근 경로는 EC2 Instance Connect Endpoint(SSH)뿐이다. 이 문서의 Redis 측정은 **앱 인스턴스에서 `/dev/tcp`로 6379에 직접 접속해** 수집했다. 앱 인스턴스는 Redis SG의 6379 ingress 대상이고 Parameter Store도 읽을 수 있어 추가 자원 없이 가능하다.

`ssmmessages` 엔드포인트를 추가하면 인터페이스 엔드포인트 요금이 늘어 예산에 영향이 있다. 추가 여부는 별도 결정으로 남긴다.

## 5. 부하 측정 설계에 반영할 것

| 항목 | 내용 |
|---|---|
| 성능 환경 사양 | 앱 컨테이너 제한(`--memory 1024m`)이 heap과 GC 선택을 결정하므로 **호스트 크기보다 컨테이너 제한을 운영과 일치시키는 것이 우선**이다. 호스트는 t4g.small로 맞춘다 |
| Redis 배치 | 운영은 전용 인스턴스다. 성능 환경이 앱 인스턴스에 Redis를 동거시키면 메모리 경합이 운영과 달라진다 |
| GC 로그 | SerialGC full GC 정지를 지연 스파이크와 구분하기 위해 수집한다 |
| CPU 크레딧 | 갓 기동한 인스턴스는 크레딧 0이다. warmup과 회복 대기를 절차에 넣고 측정 구간의 `CPUCreditBalance`·`CPUSurplusCreditBalance`를 기록한다 |
| Redis 지표 | `used_memory`, `evicted_keys`, `rejected_connections`, 키 개수 추이를 함께 기록한다. 앱 지표만 보면 한도 접근을 놓친다 |
| 대상 | 운영 인스턴스에 직접 부하를 걸지 않는다. [2차 확장 성능 검증](second-expansion-performance-verification.md)과 최종 게이트 4절 3번의 제약을 따른다 |

## 6. 이 측정이 확인하지 않은 것

- **부하 상태의 값이 아니다.** 전부 무부하 관찰이며 CPU 2% 남짓 구간의 값이다.
- **비용 실측을 하지 않았다.** 하향과 Redis 분리의 실제 청구액은 다음 청구 주기에 확인한다.
- **Redis 데이터 볼륨의 여유 공간을 수집하지 못했다.** 출력 필터에서 누락됐다. AOF 증가 추이와 함께 재수집 대상이다.
- **`ssmmessages` 엔드포인트 추가 비용을 산정하지 않았다.**
