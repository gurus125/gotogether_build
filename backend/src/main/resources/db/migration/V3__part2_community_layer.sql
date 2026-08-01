-- =============================================================================
-- V3: Part 2 — Community Layer
-- Source: Database Schema & Data Model Part 2 (Community Layer), v1.0.
-- Tables: join_requests, trip_members, reviews, trust_scores,
--         trust_score_history, badges, user_badges, chat_rooms, messages,
--         chat_participants, message_attachments, notifications,
--         notification_preferences.
--
-- Fixes from DB Schema Review & Validation baked in here:
--   #1 (HIGH):   trip_members — partial unique index enforcing exactly one
--                organizer per trip (no constraint existed in the source doc).
--   #2 (MEDIUM): chat_participants.role dropped entirely — the review's
--                recommended fix over documenting a sync mechanism. Role is
--                derived at read time via a join to trip_members.is_organizer
--                (see v_chat_participants_with_role view at the end of this file).
--   #3 (MEDIUM): messages — added (sender_id, created_at DESC) index.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- join_requests — every request a Verified User makes to join a Trip,
-- tracked as full history (never overwritten).
-- ---------------------------------------------------------------------------
CREATE TABLE join_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    trip_id                 UUID NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    status                  join_request_status NOT NULL DEFAULT 'pending',
    request_message         TEXT,
    organizer_response_note TEXT,
    waitlist_position        INTEGER,
    decided_at               TIMESTAMPTZ,
    expires_at               TIMESTAMPTZ NOT NULL,
    withdrawn_at              TIMESTAMPTZ,
    reopened_from_id          UUID REFERENCES join_requests (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_join_requests_message_length CHECK (request_message IS NULL OR char_length(request_message) <= 300)
);

-- "No duplicate open Join Request" — allows historical rejected/withdrawn
-- rows to coexist while blocking a second simultaneous open request.
CREATE UNIQUE INDEX ux_join_requests_one_open_per_applicant_trip
    ON join_requests (applicant_id, trip_id) WHERE status IN ('pending', 'waiting_list');
CREATE INDEX ix_join_requests_trip_status ON join_requests (trip_id, status);
CREATE INDEX ix_join_requests_applicant_status ON join_requests (applicant_id, status);

-- ---------------------------------------------------------------------------
-- trip_members — the confirmed roster of a Trip; source of truth for chat
-- access, review eligibility, and capacity counters.
-- ---------------------------------------------------------------------------
CREATE TABLE trip_members (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id          UUID NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    join_request_id  UUID REFERENCES join_requests (id) ON DELETE SET NULL,
    status           membership_status NOT NULL DEFAULT 'joined',
    is_organizer     BOOLEAN NOT NULL DEFAULT false,
    attendance_status attendance_status,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at          TIMESTAMPTZ,
    removed_at       TIMESTAMPTZ,
    removed_reason   TEXT,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_trip_members_trip_user UNIQUE (trip_id, user_id),
    CONSTRAINT chk_trip_members_removed_reason_required CHECK (removed_at IS NULL OR removed_reason IS NOT NULL)
);

-- Fix #1 (HIGH, DB Review): exactly one organizer per trip, mechanically enforced.
CREATE UNIQUE INDEX ux_trip_members_one_organizer_per_trip
    ON trip_members (trip_id) WHERE is_organizer;
CREATE INDEX ix_trip_members_trip_status ON trip_members (trip_id, status);
CREATE INDEX ix_trip_members_user_status ON trip_members (user_id, status);

-- ---------------------------------------------------------------------------
-- reviews — directional, double-blind peer trust records; the primary
-- evidence behind Trust Score.
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id                UUID NOT NULL REFERENCES trips (id) ON DELETE RESTRICT,
    reviewer_id            UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    reviewee_id            UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    rating_behaviour       SMALLINT NOT NULL,
    rating_punctuality     SMALLINT NOT NULL,
    rating_communication   SMALLINT NOT NULL,
    rating_cooperation     SMALLINT NOT NULL,
    rating_safety          SMALLINT NOT NULL,
    rating_reliability     SMALLINT NOT NULL,
    overall_rating         SMALLINT NOT NULL,
    comment                TEXT,
    status                 review_status NOT NULL DEFAULT 'submitted',
    visibility              review_visibility NOT NULL DEFAULT 'blind',
    published_at            TIMESTAMPTZ,
    moderation_notes         TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reviews_not_self CHECK (reviewee_id <> reviewer_id),
    CONSTRAINT chk_reviews_rating_behaviour CHECK (rating_behaviour BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_rating_punctuality CHECK (rating_punctuality BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_rating_communication CHECK (rating_communication BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_rating_cooperation CHECK (rating_cooperation BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_rating_safety CHECK (rating_safety BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_rating_reliability CHECK (rating_reliability BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_overall_rating CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_comment_length CHECK (comment IS NULL OR char_length(comment) <= 280),
    CONSTRAINT ux_reviews_trip_reviewer_reviewee UNIQUE (trip_id, reviewer_id, reviewee_id)
);

CREATE INDEX ix_reviews_reviewee_status ON reviews (reviewee_id, status);

-- ---------------------------------------------------------------------------
-- trust_scores — single current-state row per user; a materialized, fast-read
-- snapshot. Never written directly by any application code path except the
-- Trust Score recalculation job.
-- ---------------------------------------------------------------------------
CREATE TABLE trust_scores (
    user_id                         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    current_score                   NUMERIC(3,1) NOT NULL DEFAULT 6.5,
    level                           trust_level NOT NULL DEFAULT 'building',
    reviews_component               NUMERIC(3,1),
    completion_component            NUMERIC(3,1),
    verification_component          NUMERIC(3,1),
    organizer_component             NUMERIC(3,1),
    reports_penalty                 NUMERIC(3,1) NOT NULL DEFAULT 0,
    account_activity_component      NUMERIC(3,1),
    profile_completeness_component  NUMERIC(3,1),
    is_frozen                       BOOLEAN NOT NULL DEFAULT false,
    manual_override_by              UUID REFERENCES users (id) ON DELETE SET NULL,
    manual_override_reason          TEXT,
    last_calculated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trust_scores_current_score_range CHECK (current_score BETWEEN 0.0 AND 10.0),
    CONSTRAINT chk_trust_scores_reports_penalty_nonpositive CHECK (reports_penalty <= 0),
    CONSTRAINT chk_trust_scores_override_reason_required CHECK (manual_override_by IS NULL OR manual_override_reason IS NOT NULL)
);

CREATE INDEX ix_trust_scores_current_score_desc ON trust_scores (current_score DESC);

-- ---------------------------------------------------------------------------
-- trust_score_history — append-only ledger of every Trust Score change.
-- ---------------------------------------------------------------------------
CREATE TABLE trust_score_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    old_score         NUMERIC(3,1) NOT NULL,
    new_score         NUMERIC(3,1) NOT NULL,
    reason            TEXT NOT NULL,
    related_review_id UUID REFERENCES reviews (id) ON DELETE SET NULL,
    related_trip_id   UUID REFERENCES trips (id) ON DELETE SET NULL,
    updated_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_trust_score_history_user_created ON trust_score_history (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- badges / user_badges — master badge lookup + N:N award join.
-- ---------------------------------------------------------------------------
CREATE TABLE badges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    description  TEXT NOT NULL,
    icon_url     TEXT,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_badges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id     UUID NOT NULL REFERENCES badges (id) ON DELETE RESTRICT,
    awarded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    award_reason TEXT,
    expires_at   TIMESTAMPTZ,
    CONSTRAINT ux_user_badges_user_badge UNIQUE (user_id, badge_id)
);

-- ---------------------------------------------------------------------------
-- chat_rooms — one row per Trip Chat at MVP; generic enough to add Direct
-- Message rooms later without a schema change.
-- last_sequence_number backs the per-room monotonic message ordering
-- (see trg_messages_assign_sequence in V5).
-- ---------------------------------------------------------------------------
CREATE TABLE chat_rooms (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                 chat_room_type NOT NULL DEFAULT 'trip',
    trip_id              UUID REFERENCES trips (id) ON DELETE CASCADE,
    is_archived          BOOLEAN NOT NULL DEFAULT false,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_chat_rooms_trip_id ON chat_rooms (trip_id) WHERE type = 'trip';

-- ---------------------------------------------------------------------------
-- messages — individual chat messages, designed for scale from day one.
-- ---------------------------------------------------------------------------
CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_number     BIGINT NOT NULL,
    chat_room_id        UUID NOT NULL REFERENCES chat_rooms (id) ON DELETE CASCADE,
    sender_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    type                message_type NOT NULL DEFAULT 'text',
    body                TEXT,
    reply_to_message_id UUID REFERENCES messages (id) ON DELETE SET NULL,
    is_pinned           BOOLEAN NOT NULL DEFAULT false,
    pin_category        TEXT,
    is_edited           BOOLEAN NOT NULL DEFAULT false,
    edited_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    deleted_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_messages_room_sequence UNIQUE (chat_room_id, sequence_number)
);

CREATE INDEX ix_messages_room_sequence_desc ON messages (chat_room_id, sequence_number DESC);
CREATE INDEX ix_messages_room_pinned ON messages (chat_room_id) WHERE is_pinned;
-- Fix #3 (MEDIUM, DB Review): dedicated index for "all my sent messages" /
-- moderation-by-sender queries.
CREATE INDEX ix_messages_sender_created ON messages (sender_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- chat_participants — per-user state within a Chat Room.
-- Fix #2 (MEDIUM, DB Review): no `role` column here. Role is derived at read
-- time via v_chat_participants_with_role (join to trip_members.is_organizer)
-- at the bottom of this file, eliminating the sync-drift risk the review
-- flagged for a duplicated role column.
-- ---------------------------------------------------------------------------
CREATE TABLE chat_participants (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_room_id         UUID NOT NULL REFERENCES chat_rooms (id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at              TIMESTAMPTZ,
    is_muted             BOOLEAN NOT NULL DEFAULT false,
    last_read_message_id UUID REFERENCES messages (id) ON DELETE SET NULL,
    last_read_at         TIMESTAMPTZ,
    CONSTRAINT ux_chat_participants_room_user UNIQUE (chat_room_id, user_id)
);

CREATE INDEX ix_chat_participants_user_id ON chat_participants (user_id);

-- Read-time role derivation replacing the dropped chat_participants.role
-- column (Fix #2). Joins through trip_members via chat_rooms.trip_id, which
-- is cheap (both sides are indexed) and can never drift out of sync.
CREATE VIEW v_chat_participants_with_role AS
SELECT
    cp.*,
    CASE WHEN COALESCE(tm.is_organizer, false) THEN 'organizer' ELSE 'member' END AS role
FROM chat_participants cp
JOIN chat_rooms cr ON cr.id = cp.chat_room_id
LEFT JOIN trip_members tm ON tm.trip_id = cr.trip_id AND tm.user_id = cp.user_id;

-- ---------------------------------------------------------------------------
-- message_attachments — metadata only for images/documents/voice notes; the
-- file itself lives in object storage, never in Postgres.
-- ---------------------------------------------------------------------------
CREATE TABLE message_attachments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id        UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    type              attachment_type NOT NULL,
    file_url          TEXT NOT NULL,
    storage_key       TEXT NOT NULL,
    file_size_bytes   INTEGER,
    mime_type         TEXT,
    upload_status     TEXT NOT NULL DEFAULT 'pending',
    duration_seconds  INTEGER,
    latitude          NUMERIC(9,6),
    longitude         NUMERIC(9,6),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_message_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_message_attachments_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes <= 8388608),
    CONSTRAINT chk_message_attachments_upload_status CHECK (upload_status IN ('pending', 'completed', 'failed'))
);

-- ---------------------------------------------------------------------------
-- notifications — in-app notification center; the durable source of truth
-- regardless of push delivery success.
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    type               notification_type NOT NULL,
    -- entity_type / entity_id: intentionally loose (no FK) — the target table
    -- varies per notification type. Validate against a shared application
    -- constant list of {trips, join_requests, messages, reviews, verifications}
    -- (see com.gotogether.common) rather than a DB enum, per Part 2 Section 4.
    entity_type        TEXT,
    entity_id          UUID,
    title              TEXT NOT NULL,
    body               TEXT,
    priority           TEXT NOT NULL DEFAULT 'medium',
    status             notification_status NOT NULL DEFAULT 'generated',
    read_at            TIMESTAMPTZ,
    dismissed_at       TIMESTAMPTZ,
    delivery_attempts  SMALLINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_notifications_priority CHECK (priority IN ('low', 'medium', 'high')),
    CONSTRAINT chk_notifications_delivery_attempts CHECK (delivery_attempts <= 3)
);

CREATE INDEX ix_notifications_recipient_status_created ON notifications (recipient_id, status, created_at DESC);

-- ---------------------------------------------------------------------------
-- notification_preferences — per-user delivery-channel configuration.
-- ---------------------------------------------------------------------------
CREATE TABLE notification_preferences (
    user_id            UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    push_enabled       BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled     BOOLEAN NOT NULL DEFAULT true,
    email_enabled      BOOLEAN NOT NULL DEFAULT false,
    marketing_enabled  BOOLEAN NOT NULL DEFAULT false,
    reminders_enabled  BOOLEAN NOT NULL DEFAULT true,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
