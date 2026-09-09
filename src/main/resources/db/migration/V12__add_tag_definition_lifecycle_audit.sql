ALTER TABLE tag_definition
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_tag_definition__version CHECK (version >= 0);

CREATE TABLE tag_definition_audit
(
    id uuid NOT NULL PRIMARY KEY,
    tag_definition_id uuid NOT NULL REFERENCES tag_definition(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    action varchar(16) NOT NULL CHECK (action IN ('UPDATE', 'DEPRECATE', 'REACTIVATE')),
    before_snapshot jsonb NOT NULL CHECK (jsonb_typeof(before_snapshot) = 'object'),
    after_snapshot jsonb NOT NULL CHECK (jsonb_typeof(after_snapshot) = 'object'),
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    changed_by_member_id uuid REFERENCES member_account(id) ON DELETE SET NULL ON UPDATE RESTRICT,
    changed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL CHECK (version > 0),
    CONSTRAINT ux_tag_definition_audit__definition_version UNIQUE (tag_definition_id, version)
);

CREATE INDEX ix_tag_definition_audit__definition_changed
    ON tag_definition_audit (tag_definition_id, version DESC);

CREATE FUNCTION prevent_tag_definition_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.changed_by_member_id IS NOT NULL AND NEW.changed_by_member_id IS NULL
            AND (to_jsonb(OLD) - 'changed_by_member_id') = (to_jsonb(NEW) - 'changed_by_member_id')
            AND NOT EXISTS (SELECT 1 FROM member_account WHERE id = OLD.changed_by_member_id) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'tag_definition_audit is append-only';
END;
$$;

CREATE TRIGGER tr_tag_definition_audit__append_only
BEFORE UPDATE OR DELETE ON tag_definition_audit
FOR EACH ROW EXECUTE FUNCTION prevent_tag_definition_audit_mutation();
