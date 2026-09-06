CREATE TABLE visit_tag_revision
(
    id uuid NOT NULL PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES visit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    revision bigint NOT NULL CHECK (revision > 0),
    before_tag_codes jsonb NOT NULL CHECK (jsonb_typeof(before_tag_codes) = 'array'),
    after_tag_codes jsonb NOT NULL CHECK (jsonb_typeof(after_tag_codes) = 'array'),
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    changed_by_member_id uuid REFERENCES member_account(id) ON DELETE SET NULL ON UPDATE RESTRICT,
    changed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_visit_tag_revision__visit_revision UNIQUE (visit_id, revision)
);

CREATE FUNCTION prevent_visit_tag_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.changed_by_member_id IS NOT NULL AND NEW.changed_by_member_id IS NULL
            AND (to_jsonb(OLD) - 'changed_by_member_id') = (to_jsonb(NEW) - 'changed_by_member_id')
            AND NOT EXISTS (SELECT 1 FROM member_account WHERE id = OLD.changed_by_member_id) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'visit_tag_revision is append-only';
END;
$$;

CREATE TRIGGER tr_visit_tag_revision__append_only
BEFORE UPDATE OR DELETE ON visit_tag_revision
FOR EACH ROW EXECUTE FUNCTION prevent_visit_tag_revision_mutation();
