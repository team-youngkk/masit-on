CREATE TABLE tag_definition_merge
(
    id uuid NOT NULL PRIMARY KEY,
    source_tag_definition_id uuid NOT NULL
        REFERENCES tag_definition(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    target_tag_definition_id uuid NOT NULL
        REFERENCES tag_definition(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    source_before_snapshot jsonb NOT NULL CHECK (jsonb_typeof(source_before_snapshot) = 'object'),
    source_after_snapshot jsonb NOT NULL CHECK (jsonb_typeof(source_after_snapshot) = 'object'),
    target_snapshot jsonb NOT NULL CHECK (jsonb_typeof(target_snapshot) = 'object'),
    source_version bigint NOT NULL CHECK (source_version >= 0),
    target_version bigint NOT NULL CHECK (target_version >= 0),
    preview_fingerprint varchar(64) NOT NULL
        CHECK (preview_fingerprint ~ '^[0-9a-f]{64}$'),
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    merged_by_member_id uuid
        REFERENCES member_account(id) ON DELETE SET NULL ON UPDATE RESTRICT,
    merged_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    affected_visit_count bigint NOT NULL CHECK (affected_visit_count >= 0),
    moved_visit_tag_count bigint NOT NULL CHECK (moved_visit_tag_count >= 0),
    deduplicated_visit_tag_count bigint NOT NULL CHECK (deduplicated_visit_tag_count >= 0),
    CONSTRAINT ux_tag_definition_merge__source UNIQUE (source_tag_definition_id),
    CONSTRAINT ck_tag_definition_merge__different_definitions
        CHECK (source_tag_definition_id <> target_tag_definition_id),
    CONSTRAINT ck_tag_definition_merge__affected_count
        CHECK (affected_visit_count = moved_visit_tag_count + deduplicated_visit_tag_count)
);

CREATE INDEX ix_tag_definition_merge__target
    ON tag_definition_merge (target_tag_definition_id, merged_at DESC);

CREATE TABLE visit_tag_merge_provenance
(
    id uuid NOT NULL PRIMARY KEY,
    tag_definition_merge_id uuid NOT NULL
        REFERENCES tag_definition_merge(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    visit_id uuid NOT NULL REFERENCES visit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    visit_tag_id uuid NOT NULL,
    snapshot_role varchar(16) NOT NULL CHECK (snapshot_role IN ('SOURCE', 'TARGET')),
    outcome varchar(16) NOT NULL CHECK (outcome IN ('MOVED', 'DEDUPLICATED')),
    visit_tag_snapshot jsonb NOT NULL CHECK (jsonb_typeof(visit_tag_snapshot) = 'object'),
    recorded_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_visit_tag_merge_provenance__merge_tag_role
        UNIQUE (tag_definition_merge_id, visit_tag_id, snapshot_role),
    CONSTRAINT ck_visit_tag_merge_provenance__outcome_role CHECK (
        (outcome = 'MOVED' AND snapshot_role = 'SOURCE')
        OR outcome = 'DEDUPLICATED'
    )
);

CREATE INDEX ix_visit_tag_merge_provenance__visit
    ON visit_tag_merge_provenance (visit_id, recorded_at DESC);

CREATE FUNCTION prevent_tag_definition_merge_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.merged_by_member_id IS NOT NULL AND NEW.merged_by_member_id IS NULL
            AND (to_jsonb(OLD) - 'merged_by_member_id') = (to_jsonb(NEW) - 'merged_by_member_id')
            AND NOT EXISTS (SELECT 1 FROM member_account WHERE id = OLD.merged_by_member_id) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'tag_definition_merge is append-only';
END;
$$;

CREATE TRIGGER tr_tag_definition_merge__append_only
BEFORE UPDATE OR DELETE ON tag_definition_merge
FOR EACH ROW EXECUTE FUNCTION prevent_tag_definition_merge_mutation();

CREATE FUNCTION prevent_visit_tag_merge_provenance_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'visit_tag_merge_provenance is append-only';
END;
$$;

CREATE TRIGGER tr_visit_tag_merge_provenance__append_only
BEFORE UPDATE OR DELETE ON visit_tag_merge_provenance
FOR EACH ROW EXECUTE FUNCTION prevent_visit_tag_merge_provenance_mutation();

CREATE FUNCTION enforce_visit_tag_active_definition()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.tag_definition_id IS DISTINCT FROM OLD.tag_definition_id THEN
        IF NOT EXISTS (
            SELECT 1 FROM tag_definition
             WHERE id = NEW.tag_definition_id AND status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'new visit_tag requires an active tag definition'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_visit_tag__active_definition
BEFORE INSERT OR UPDATE OF tag_definition_id ON visit_tag
FOR EACH ROW EXECUTE FUNCTION enforce_visit_tag_active_definition();
