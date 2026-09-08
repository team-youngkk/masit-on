-- V4 seed는 자연어 파서의 초기 사전보다 적은 표시명만 저장했다. 동적 사전 전환에서도
-- 기존 P1 Golden 문장을 유지하도록 초기 18개 정의의 별칭을 데이터 정본으로 옮긴다.
WITH seed_aliases(tag_code, aliases) AS (
    VALUES
        ('MENU_NAENGMYEON', '["물냉면", "비빔냉면"]'::jsonb),
        ('MENU_GUKBAP', '[]'::jsonb),
        ('MENU_RAMEN', '[]'::jsonb),
        ('MENU_SUSHI', '["초밥"]'::jsonb),
        ('MENU_PIZZA', '[]'::jsonb),
        ('MENU_SAMGYEOPSAL', '[]'::jsonb),
        ('TASTE_SPICY', '["매운", "매콤"]'::jsonb),
        ('TASTE_SWEET', '["달콤", "달달"]'::jsonb),
        ('TASTE_SAVORY', '["고소"]'::jsonb),
        ('TASTE_LIGHT', '["담백", "깔끔한 맛"]'::jsonb),
        ('OCCASION_SOLO', '["혼자 식사", "혼자 먹기"]'::jsonb),
        ('OCCASION_DATE', '["연인과"]'::jsonb),
        ('OCCASION_GROUP', '["회식", "단체 모임"]'::jsonb),
        ('OCCASION_LATE_NIGHT', '["늦은 밤", "심야"]'::jsonb),
        ('ATMOSPHERE_CASUAL', '["편안한 분위기"]'::jsonb),
        ('ATMOSPHERE_QUIET', '["조용한 분위기"]'::jsonb),
        ('ATMOSPHERE_LIVELY', '["북적이는", "활기찬 분위기"]'::jsonb),
        ('ATMOSPHERE_BAR', '["바 분위기", "포차 분위기"]'::jsonb)
)
UPDATE tag_definition definition
   SET aliases = seed_aliases.aliases,
       updated_at = CURRENT_TIMESTAMP
  FROM seed_aliases
 WHERE definition.tag_code = seed_aliases.tag_code
   AND definition.source = 'SEED';

INSERT INTO tag_definition_term (id, tag_definition_id, term_kind, normalized_term, created_at)
SELECT md5(definition.id::text || ':ALIAS:' || alias.ordinality::text)::uuid,
       definition.id,
       'ALIAS',
       normalize_tag_definition_term(alias.value),
       definition.created_at
  FROM tag_definition definition
 CROSS JOIN LATERAL jsonb_array_elements_text(definition.aliases)
      WITH ORDINALITY AS alias(value, ordinality)
 WHERE definition.source = 'SEED';
