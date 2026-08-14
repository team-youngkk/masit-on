---
related_documents:
  - ../../docs/08-planning/third-expansion-test-matrix.md
  - ../../docs/08-planning/third-expansion-final-gate-result.md
  - ../../docs/07-adr/quality/perf-001-k6-load-testing.md
  - ../../docs/07-adr/quality/perf-002-operational-participant-load-testing.md
  - ../../docs/troubleshooting/pr-185-e3-t13-final-gate-review.md
---

# 검증 참여자 전용 운영 성능 fixture

[#190](https://github.com/team-youngkk/masit-on)의 일회성 운영 직접 성능 검증을 위한 최소 합성 데이터다. 기존 `perf/seed/`는 측정 전용 환경 전용이며 운영 DB에 사용하지 않는다.

이 fixture는 운영 공개 조회·자연어 검색·좌표 기반 코스 입력을 만들기 위해 다음만 추가한다.

- 합성 공개·활성 맛집 25건
- 합성 `example.invalid` 회원 25건
- 합성 찜 500건(맛집당 20건)
- 코스용 합성 좌표 맛집 5건
- 기존 활성 관리자 계정을 작성자로 재사용한 합성 공개 큐레이션 1건과 맛집 연결 20건

기존 관리자 계정 자체·크리에이터·영상·Visit는 추가하지 않는다. 공개 큐레이션이 없을 수 있으므로 기존 활성 관리자 계정을 작성자로 재사용해 합성 큐레이션을 1건 추가한다. 기존 DRAFT 큐레이션은 수정하지 않는다.

## 실행 전 조건

1. 운영 RDS 스냅샷을 생성한다.
2. `00-preflight.sql`로 DB 이름, 동일 `RUN_ID` 잔존 여부, 활성 관리자 계정을 확인한다.
3. 검증 참여자에게 합성 데이터 노출과 측정 시간대를 공지한다.
4. 실행 중 합성 맛집에 대한 찜·상세 조회·컬렉션 사용을 금지한다.
5. 실행 커밋, DB 행 수, `RUN_ID`, 적재·정리 시각을 #190에 기록한다.

## 실행 순서

모든 명령은 PostgreSQL 17.10에 연결되는 승인된 `psql` 실행기에서 수행한다. 비밀번호를 명령행이나 로그에 넣지 않는다.

```bash
RUN_ID=20260814
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v RUN_ID="$RUN_ID" -f perf/operational-fixture/00-preflight.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v RUN_ID="$RUN_ID" -v PRODUCTION_PERF_APPLY_APPROVED=true -f perf/operational-fixture/01-apply.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v RUN_ID="$RUN_ID" -f perf/operational-fixture/02-verify.sql
```

적재 후 인기 맛집 응답이 20건 이상인지 확인한다. 코스에는 `02-verify.sql`에서 확인한 5개 합성 맛집 ID를 `COURSE_RESTAURANT_IDS`로 명시한다. auto-discovery에 의존하지 않는다.

## 정리

부하 측정 후 먼저 합성 맛집을 참조한 `favorite`, `recent_restaurant_view`, `collection_restaurant`, `curation_restaurant`, `visit`, `ai_candidate_snapshot`과 합성 회원의 `member_action_token`, `personal_collection`, `notification`, `submission`, `report`, `idempotency_record`, `member_deletion_job`을 확인한다. 실제 참여자 참조가 있으면 cleanup은 중단하고 운영자 판단을 남긴다. cleanup 실행 중에는 참여자 요청과 회원 작업 Worker를 중지해 신규 쓰기를 만들지 않는다. cleanup의 두 테이블 잠금은 삭제 트랜잭션 동안만 유지하고, `COMMIT` 후 `ANALYZE`를 실행한다.

참조가 없을 때만 다음을 실행한다.

```bash
psql "$DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -v RUN_ID=20260814 \
  -v PRODUCTION_PERF_CLEANUP_APPROVED=true \
  -f perf/operational-fixture/99-cleanup.sql
```

정리 후 marker 잔존 건수 0, 기존 행 수 복원, `ANALYZE` 완료를 확인한다. 정리하지 않기로 결정하면 합성 데이터가 운영에 남는 사실과 후속 담당자를 #190에 기록한다.
