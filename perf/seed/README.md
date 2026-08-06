# 성능 부하 테스트 기준 데이터 시드

`NFR-PERFORMANCE-006` 정상 부하 측정에 쓰는 기준 데이터를 적재한다. [ADR-PERF-001](../../docs/07-adr/quality/perf-001-k6-load-testing.md) 6.5절이 소유하는 산출물이다.

**이 SQL은 Flyway 마이그레이션이 아니다.** `src/main/resources/db/migration/`에 옮기지 않는다. 측정 환경에만 적재하는 별도 스크립트다.

## 선행 조건

- 대상 DB에 Flyway 마이그레이션이 `V3`까지 적용돼 있어야 한다.
- 대상은 **측정 전용 임시 환경**이다. 제한 공개 중인 운영 DB에 적재하지 않는다.

## 실행 순서

파일명 순서대로 실행한다. `00-cleanup.sql`을 먼저 돌려 이전 시드를 비우고 시작한다.

```bash
set -e; for f in perf/seed/*.sql; do psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f"; done
```

로컬 컨테이너에 적재할 때는 다음과 같다.

```bash
set -e; for f in perf/seed/*.sql; do docker exec -i masiton-postgres psql -U masiton -d masiton -v ON_ERROR_STOP=1 < "$f"; done
```

`set -e`를 빼지 않는다. 없으면 앞 파일이 실패해도 뒤 파일이 계속 돌아 **부분 적재된 DB**가 남는다. 대상을 잘못 지정했을 때 가장 나쁜 결과가 그것이다.

01~08은 전부 `ON CONFLICT DO NOTHING`이라 중복 실행해도 행이 늘지 않는다(`09-analyze.sql`은 `ANALYZE`만 하고 INSERT가 없다). 다만 분포를 바꿔 다시 설계할 때는 `00-cleanup.sql`을 반드시 먼저 돌린다.

| 파일 | 적재 대상 | 건수 |
|---|---|---|
| `00-cleanup.sql` | 시드가 만든 행만 FK 역순 삭제 | — |
| `01-admin-account.sql` | `admin_account` (큐레이션 FK 대상) | 1 |
| `02-restaurant.sql` | `restaurant` | 1,000 |
| `03-creator.sql` | `creator` | 200 |
| `04-video.sql` | `video` | 5,000 |
| `05-visit.sql` | `visit` | 10,000 |
| `06-member-account.sql` | `member_account` | 1,000 |
| `07-favorite.sql` | `favorite` | 20,000 |
| `08-curation.sql` | `curation` / `curation_restaurant` | 5 / 100 |
| `09-analyze.sql` | `ANALYZE` | — |

`09-analyze.sql`을 건너뛰지 않는다. 통계가 없으면 실행계획이 운영과 달라져 측정이 무의미해진다.

## 식별자와 정리 경계

모든 UUID는 `md5(마커 문자열 || 순번)`에서 결정론적으로 만든다. 같은 시드는 몇 번을 돌려도 같은 식별자를 만들어 측정을 재현할 수 있다.

`00-cleanup.sql`은 아래 마커에 해당하는 행만 지운다. `V1`이 넣은 `region` 25건과 `food_category` 10건은 건드리지 않는다.

| 테이블 | 마커 |
|---|---|
| `restaurant` | `kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'` |
| `creator` | `external_channel_id LIKE 'PERF-SEED-CREATOR-%'` |
| `video` | `external_video_id LIKE 'PERFSEEDVID%'` |
| `member_account` | `email LIKE 'perf-seed-member-%@example.invalid'` |
| `admin_account` | `login_id = 'perf-seed-admin'` |
| `curation` | `title LIKE '부하테스트 큐레이션 %'` **이면서** `created_by`가 `perf-seed-admin` |

`visit`·`favorite`·`curation_restaurant`은 자체 마커 열이 없어 부모 행의 마커로 식별한다.
시드 맛집·회원을 가리키는 `favorite`·`recent_restaurant_view`·`collection_restaurant` 행은 소유자를 가리지 않고 전부 삭제한다(부모인 시드 맛집/회원이 삭제되므로 의존 자식 행은 남아있을 수 없다. 단, 실제 맛집/회원 자체 데이터는 보존된다).

## 찜 분포

균등 분포가 아니다. 인기 맛집 집계가 `ORDER BY count(*) DESC, r.id ASC`이므로, 모든 맛집이 같은 찜 수를 가지면 동점 타이브레이커만 검사되고 정렬 자체가 검증되지 않는다.
상위 50개 맛집에는 `(226 - r)`건(225~176건)의 기울기 편차를 부여하여 상위 20개 응답이 서로 다른 찜 수로 차별화되도록 구성한다.

| 맛집 순번 | 맛집당 찜 | 소계 |
|---|---|---|
| 1~50 | 225~176 (선형 편차) | 10,025 |
| 51~200 | 40 | 6,000 |
| 201~225 | 4 | 100 |
| 226~1,000 | 5 | 3,875 |
| **합계** | — | **20,000** |

맛집당 최댓값 225는 회원 수 1,000보다 낮아 `favorite`의 PK `(member_id, restaurant_id)` 상한을 넘지 않는다.

## 검증된 사실

`postgres:17.10-alpine`에 `V1`~`V3`를 적용한 DB에서 전 파일 실행을 확인했다.

- 적재 건수: `restaurant=1000`, `creator=200`, `video=5000`, `visit=10000`, `member_account=1000`, `favorite=20000`, `curation=5`, `curation_restaurant=100`
- 재실행 시 추가 적재 0건 (멱등)
- `00-cleanup.sql` 실행 후 `region=25`, `food_category=10`만 남고 재적재 시 같은 건수로 복원
