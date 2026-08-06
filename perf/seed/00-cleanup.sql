-- perf/seed/00-cleanup.sql
-- 목적: 부하 테스트 시드가 만든 행만 제거한다. V1이 넣은 region(25건)·food_category(10건)
--       기준 데이터와 애플리케이션이 실제로 만든 행은 건드리지 않는다.
--
-- 시드가 만든 행은 아래 마커로만 식별한다(실제 운영 데이터가 이 마커를 쓸 수 없게 설계했다).
--   restaurant       : kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
--   creator          : external_channel_id LIKE 'PERF-SEED-CREATOR-%'
--   video            : external_video_id LIKE 'PERFSEEDVID%'
--   member_account   : email LIKE 'perf-seed-member-%@example.invalid'
--   admin_account    : login_id = 'perf-seed-admin'
--   curation         : title LIKE '부하테스트 큐레이션 %' AND created_by = 시드 관리자
--
-- 실행 절차: 재시드 전에 이 파일을 먼저 실행한다(01~08은 ON CONFLICT DO NOTHING도 갖고 있어
-- 이 파일을 건너뛰어도 깨지지는 않지만, 분포를 새로 설계해 바꿀 때는 반드시 먼저 비운다).
-- 선행 조건: V1~V3 마이그레이션이 이미 적용된 스키마.
--
-- 자식 행 정리 범위(중요):
-- restaurant를 참조하는 FK 5개(collection_restaurant, curation_restaurant, favorite,
-- recent_restaurant_view, visit)는 전부 ON DELETE RESTRICT다. member_account의
-- member_action_token, admin_account의 confirmation_token·moderation_history도 같다.
-- 따라서 "시드가 만든 자식"만 지우면 부족하다. 측정 환경에서 실제 회원이 시드 맛집을
-- 찜하거나 상세를 열어 recent_restaurant_view가 생기면, 그 행 하나 때문에 DELETE FROM
-- restaurant가 FK 위반으로 실패해 재시드가 영구히 막힌다.
-- 그래서 시드 부모를 가리키는 자식은 누가 만들었든 전부 지운다. 부모가 사라지므로 그
-- 자식 행은 어차피 남을 수 없다.
--
-- 삭제 순서는 FK 참조의 역순이다(자식 -> 부모).
--
-- 전체가 하나의 트랜잭션이다. 중간에 실패하면 아무것도 지워지지 않는다.

BEGIN;

-- restaurant를 가리키는 RESTRICT 자식 5종. 소유자를 가리지 않고 시드 맛집 참조를 전부 지운다.
DELETE FROM curation_restaurant
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
 );

DELETE FROM collection_restaurant
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
 );

DELETE FROM recent_restaurant_view
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
 );

DELETE FROM favorite
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
 );

DELETE FROM visit
 WHERE restaurant_id IN (
     SELECT id FROM restaurant WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%'
 )
    OR creator_id IN (
     SELECT id FROM creator WHERE external_channel_id LIKE 'PERF-SEED-CREATOR-%'
 )
    OR video_id IN (
     SELECT id FROM video WHERE external_video_id LIKE 'PERFSEEDVID%'
 );

-- 시드 관리자가 만든 큐레이션 및 관계. 상단에서 시드 맛집 참조 관계는 이미 삭제되었으나,
-- 향후 시드 큐레이션이 시드 외 맛집을 참조하는 구조 변경에 대비해 방어적으로 중복 처리한다.
-- curation.title은 관리자가 화면에서 자유롭게 입력하는 값이라 제목만으로 좁히면 같은
-- 접두사를 쓴 실제 큐레이션까지 지울 수 있으므로 admin_account 마커와 조합해 지운다.
DELETE FROM curation_restaurant
 WHERE curation_id IN (
     SELECT id FROM curation
      WHERE title LIKE '부하테스트 큐레이션 %'
        AND created_by = (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin')
 );

DELETE FROM curation
 WHERE title LIKE '부하테스트 큐레이션 %'
   AND created_by = (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin');

DELETE FROM video
 WHERE external_video_id LIKE 'PERFSEEDVID%';

DELETE FROM creator
 WHERE external_channel_id LIKE 'PERF-SEED-CREATOR-%';

DELETE FROM restaurant
 WHERE kakao_place_id LIKE 'PERF-SEED-RESTAURANT-%';

-- member_account를 가리키는 RESTRICT 자식. 나머지(favorite, recent_restaurant_view,
-- personal_collection, notification)는 CASCADE, submission·report는 SET NULL이라
-- 별도 정리가 필요 없다.
DELETE FROM member_action_token
 WHERE member_id IN (
     SELECT id FROM member_account WHERE email LIKE 'perf-seed-member-%@example.invalid'
 );

DELETE FROM member_account
 WHERE email LIKE 'perf-seed-member-%@example.invalid';

-- admin_account를 가리키는 RESTRICT 자식. curation은 위에서 이미 지웠다.
DELETE FROM moderation_history
 WHERE admin_account_id = (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin');

DELETE FROM confirmation_token
 WHERE admin_account_id = (SELECT id FROM admin_account WHERE login_id = 'perf-seed-admin');

DELETE FROM admin_account
 WHERE login_id = 'perf-seed-admin';

COMMIT;
