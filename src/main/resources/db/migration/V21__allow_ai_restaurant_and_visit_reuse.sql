-- #419: 생성 여부는 reused_resources의 여집합으로 복원한다. 기존 감사 값은 보존한다.
CREATE OR REPLACE FUNCTION ai_registration_unit_reused_resources_are_valid(value jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF jsonb_typeof(value) <> 'array' THEN
        RETURN false;
    END IF;
    RETURN NOT EXISTS (
        SELECT 1
        FROM jsonb_array_elements(value) AS resource_element(resource_value)
        WHERE jsonb_typeof(resource_value) <> 'string'
           OR (resource_value #>> '{}') NOT IN ('restaurant', 'creator', 'video', 'visit')
    );
END;
$$;
