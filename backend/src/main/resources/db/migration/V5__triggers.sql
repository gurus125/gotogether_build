-- =============================================================================
-- V5: Triggers
-- Implements the two behaviors the schema docs describe as "trigger-maintained"
-- but leave to the migration author to implement:
--   1. updated_at auto-maintenance on every table that has the column
--      (Part 1 Section 1: "every table gets created_at and updated_at,
--      defaulted and trigger-maintained").
--   2. messages.sequence_number — monotonic per-room ordering (Part 2:
--      "nextval per-room sequence"), implemented via an atomic counter on
--      chat_rooms.last_sequence_number rather than one Postgres SEQUENCE
--      object per room, which would not scale to room creation volume.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. updated_at auto-maintenance
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users', 'user_profiles', 'verifications', 'destinations', 'trips',
        'join_requests', 'trip_members', 'reviews', 'notification_preferences',
        'travel_companies', 'company_users', 'company_verifications'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();',
            t, t
        );
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 2. messages.sequence_number — atomic per-room counter.
-- Locks the owning chat_rooms row for the duration of the increment so two
-- concurrent sends into the same room can never receive the same sequence
-- number, then stamps it onto the new message row before insert.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_messages_assign_sequence() RETURNS TRIGGER AS $$
DECLARE
    next_seq BIGINT;
BEGIN
    UPDATE chat_rooms
    SET last_sequence_number = last_sequence_number + 1
    WHERE id = NEW.chat_room_id
    RETURNING last_sequence_number INTO next_seq;

    IF next_seq IS NULL THEN
        RAISE EXCEPTION 'messages.chat_room_id % does not reference an existing chat_rooms row', NEW.chat_room_id;
    END IF;

    NEW.sequence_number = next_seq;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_assign_sequence
    BEFORE INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION trg_messages_assign_sequence();
