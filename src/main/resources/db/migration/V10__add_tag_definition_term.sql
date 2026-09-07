CREATE FUNCTION normalize_tag_definition_term(value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT lower(btrim(regexp_replace(normalize(value, NFKC), '[\s   -     　]+', ' ', 'g')))
$$;

-- The legacy AI writer stored its display name again as the first alias. Remove only
-- that system-generated self-alias before enforcing global term uniqueness.
UPDATE tag_definition definition
   SET aliases = (
           SELECT COALESCE(
                   jsonb_agg(to_jsonb(alias.value) ORDER BY alias.ordinality)
                       FILTER (WHERE normalize_tag_definition_term(alias.value)
                               <> normalize_tag_definition_term(definition.display_name)),
                   '[]'::jsonb)
             FROM jsonb_array_elements_text(definition.aliases)
                  WITH ORDINALITY AS alias(value, ordinality)
       ),
       updated_at = CURRENT_TIMESTAMP
 WHERE definition.source = 'AI_AUTO'
   AND EXISTS (
           SELECT 1
             FROM jsonb_array_elements_text(definition.aliases) AS alias(value)
            WHERE normalize_tag_definition_term(alias.value)
                  = normalize_tag_definition_term(definition.display_name)
       );

DO $$
BEGIN
    IF EXISTS (
        WITH terms AS (
            SELECT normalize_tag_definition_term(display_name) AS normalized_term
              FROM tag_definition
            UNION ALL
            SELECT normalize_tag_definition_term(alias.value)
              FROM tag_definition definition
             CROSS JOIN LATERAL jsonb_array_elements_text(definition.aliases) AS alias(value)
        )
        SELECT 1 FROM terms
         WHERE normalized_term = '' OR char_length(normalized_term) > 200
    ) THEN
        RAISE EXCEPTION 'Tag definition contains an invalid normalized term.' USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        WITH terms AS (
            SELECT normalize_tag_definition_term(display_name) AS normalized_term
              FROM tag_definition
            UNION ALL
            SELECT normalize_tag_definition_term(alias.value)
              FROM tag_definition definition
             CROSS JOIN LATERAL jsonb_array_elements_text(definition.aliases) AS alias(value)
        )
        SELECT 1 FROM terms GROUP BY normalized_term HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Tag definition contains duplicate normalized terms.' USING ERRCODE = '23505';
    END IF;
END;
$$;

CREATE TABLE tag_definition_term
(
    id                uuid                        NOT NULL,
    tag_definition_id uuid                        NOT NULL,
    term_kind         varchar(16)                 NOT NULL,
    normalized_term   varchar(200)                NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tag_definition_term PRIMARY KEY (id),
    CONSTRAINT fk_tag_definition_term__definition FOREIGN KEY (tag_definition_id)
        REFERENCES tag_definition (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_tag_definition_term__kind CHECK (term_kind IN ('DISPLAY_NAME', 'ALIAS')),
    CONSTRAINT ck_tag_definition_term__normalized_not_blank CHECK (btrim(normalized_term) <> ''),
    CONSTRAINT ck_tag_definition_term__normalized_canonical CHECK (
        normalized_term = normalize_tag_definition_term(normalized_term)
    )
);

INSERT INTO tag_definition_term (id, tag_definition_id, term_kind, normalized_term, created_at)
SELECT md5(definition.id::text || ':DISPLAY_NAME')::uuid,
       definition.id,
       'DISPLAY_NAME',
       normalize_tag_definition_term(definition.display_name),
       definition.created_at
  FROM tag_definition definition
UNION ALL
SELECT md5(definition.id::text || ':ALIAS:' || alias.ordinality::text)::uuid,
       definition.id,
       'ALIAS',
       normalize_tag_definition_term(alias.value),
       definition.created_at
  FROM tag_definition definition
 CROSS JOIN LATERAL jsonb_array_elements_text(definition.aliases)
      WITH ORDINALITY AS alias(value, ordinality);

ALTER TABLE tag_definition_term
    ADD CONSTRAINT ux_tag_definition_term__normalized_term UNIQUE (normalized_term);

CREATE UNIQUE INDEX ux_tag_definition_term__display_name_owner
    ON tag_definition_term (tag_definition_id)
    WHERE term_kind = 'DISPLAY_NAME';

CREATE FUNCTION tag_definition_aliases_have_valid_length(value jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT NOT EXISTS (
        SELECT 1
          FROM jsonb_array_elements_text(value) AS alias_value(alias_text)
         WHERE char_length(btrim(alias_text)) > 100
    )
$$;

ALTER TABLE tag_definition
    ADD CONSTRAINT ck_tag_definition__code_format CHECK (
        tag_code ~ '^(MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(_[A-Z0-9]+)*$'
    ),
    ADD CONSTRAINT ck_tag_definition__code_type_prefix CHECK (
        tag_code LIKE tag_type || '\_%' ESCAPE '\'
    ),
    ADD CONSTRAINT ck_tag_definition__aliases_max_count CHECK (jsonb_array_length(aliases) <= 20),
    ADD CONSTRAINT ck_tag_definition__aliases_max_length
        CHECK (tag_definition_aliases_have_valid_length(aliases));
