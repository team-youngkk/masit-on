-- perf/seed/09-analyze.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (9/9, 마지막 단계).
--
-- 선행 조건: 01~08을 모두 실행함.
--
-- ANALYZE로 통계를 갱신한다. 통계가 갱신되지 않으면 플래너가 옛(또는 빈) 분포를 보고
-- 실행계획을 짜서 실제 운영과 다른 계획(예: Seq Scan vs Index Scan 선택이 뒤바뀜)이
-- 나올 수 있어 부하 측정이 무의미해진다. 같은 이유로
-- PopularRestaurantQueryPlanPostgreSqlIntegrationTest(대표 데이터 삽입 후 ANALYZE 실행)도
-- 매번 통계를 갱신한다.
--
-- 이번 이슈가 측정 대상으로 지정한 GET /api/restaurants/popular, GET /api/curations,
-- GET /api/curations/{id}가 직접·간접으로 스캔하는 테이블만 명시적으로 ANALYZE한다.

ANALYZE restaurant;
ANALYZE favorite;
ANALYZE curation;
ANALYZE curation_restaurant;
ANALYZE visit;
ANALYZE video;
ANALYZE creator;
ANALYZE member_account;
