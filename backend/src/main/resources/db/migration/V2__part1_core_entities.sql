-- =============================================================================
-- V2: Part 1 — Core Entities
-- Source: Database Schema & Data Model Part 1 (Core Entities), v1.0.
-- Tables: users, user_profiles, verifications, destinations, trips,
--         trip_images, saved_trips.
--
-- Fixes from DB Schema Review & Validation baked in here (see backend/README
-- and project memory for the full list):
--   #5 (LOW): trips.requires_approval / allow_waitlist renamed to
--             is_approval_required / is_waitlist_allowed for is_/has_ convention.
--   #6 (LOW): trips.max_group_size given a sanity ceiling (CHECK <= 50).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- users — authentication identity and account-lifecycle state only.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number        TEXT,
    google_id           TEXT,
    email               TEXT,
    status              user_status NOT NULL DEFAULT 'registered',
    verification_level  verification_level NOT NULL DEFAULT 'none',
    role                account_role NOT NULL DEFAULT 'individual',
    last_login_at       TIMESTAMPTZ,
    deactivated_at      TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_identity_present CHECK (phone_number IS NOT NULL OR google_id IS NOT NULL)
);

CREATE UNIQUE INDEX ux_users_phone_number ON users (phone_number) WHERE phone_number IS NOT NULL;
CREATE UNIQUE INDEX ux_users_google_id ON users (google_id) WHERE google_id IS NOT NULL;
CREATE UNIQUE INDEX ux_users_email ON users (email) WHERE email IS NOT NULL;

-- ---------------------------------------------------------------------------
-- user_profiles — personal, display-facing, and compatibility-matching data.
-- ---------------------------------------------------------------------------
CREATE TABLE user_profiles (
    user_id                 UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name            TEXT NOT NULL,
    photo_url               TEXT,
    bio                     TEXT,
    city                    TEXT,
    date_of_birth           DATE,
    languages               JSONB NOT NULL DEFAULT '[]',
    travel_style            TEXT,
    food_preference         TEXT,
    smoking_preference      TEXT,
    drinking_preference     TEXT,
    preferred_budget_style  TEXT,
    adventure_level         SMALLINT,
    emergency_contact_name  TEXT,
    emergency_contact_phone TEXT,
    visibility               profile_visibility NOT NULL DEFAULT 'public',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_profiles_display_name_length CHECK (char_length(display_name) BETWEEN 2 AND 50),
    CONSTRAINT chk_user_profiles_bio_length CHECK (bio IS NULL OR char_length(bio) <= 250),
    CONSTRAINT chk_user_profiles_adventure_level CHECK (adventure_level IS NULL OR adventure_level BETWEEN 1 AND 5)
);

CREATE INDEX ix_user_profiles_display_name ON user_profiles (display_name);

-- ---------------------------------------------------------------------------
-- verifications — full history of every verification attempt.
-- ---------------------------------------------------------------------------
CREATE TABLE verifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type                    verification_type NOT NULL,
    status                  verification_status NOT NULL DEFAULT 'pending',
    document_type           TEXT,
    document_reference_hash TEXT,
    document_image_url      TEXT,
    rejection_reason        rejection_reason,
    reviewed_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_verifications_document_reference_hash ON verifications (document_reference_hash) WHERE document_reference_hash IS NOT NULL;
CREATE INDEX ix_verifications_user_type_status ON verifications (user_id, type, status);

-- ---------------------------------------------------------------------------
-- destinations — the curated, platform-controlled destination list.
-- ---------------------------------------------------------------------------
CREATE TABLE destinations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    category        destination_category NOT NULL,
    cover_image_url TEXT,
    popularity_rank INTEGER,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_destinations_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- trips — the core entity of the product; one lifecycle for both Community
-- and Verified Partner trips. trips.company_id is added in V4 once
-- travel_companies exists (Part 3), per the source documents' own ordering.
-- ---------------------------------------------------------------------------
CREATE TABLE trips (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id          UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    destination_id        UUID NOT NULL REFERENCES destinations (id) ON DELETE RESTRICT,
    kind                  trip_kind NOT NULL,
    status                trip_status NOT NULL DEFAULT 'draft',
    visibility            trip_visibility NOT NULL DEFAULT 'public',
    title                 TEXT NOT NULL,
    description           TEXT NOT NULL,
    trip_type             TEXT,
    is_flexible_dates     BOOLEAN NOT NULL DEFAULT false,
    start_date            DATE NOT NULL,
    end_date              DATE NOT NULL,
    budget_min            INTEGER,
    budget_max            INTEGER,
    fixed_price           INTEGER,
    min_group_size        SMALLINT NOT NULL DEFAULT 2,
    max_group_size        SMALLINT NOT NULL DEFAULT 6,
    is_approval_required  BOOLEAN NOT NULL DEFAULT true,
    is_waitlist_allowed   BOOLEAN NOT NULL DEFAULT true,
    meeting_point         TEXT,
    published_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   TEXT,
    completed_at          TIMESTAMPTZ,
    archived_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trips_title_length CHECK (char_length(title) BETWEEN 5 AND 60),
    CONSTRAINT chk_trips_description_length CHECK (char_length(description) <= 300),
    CONSTRAINT chk_trips_end_after_start CHECK (end_date >= start_date),
    CONSTRAINT chk_trips_budget_min_nonneg CHECK (budget_min IS NULL OR budget_min >= 0),
    CONSTRAINT chk_trips_budget_max_ge_min CHECK (budget_max IS NULL OR budget_min IS NULL OR budget_max >= budget_min),
    CONSTRAINT chk_trips_fixed_price_nonneg CHECK (fixed_price IS NULL OR fixed_price >= 0),
    CONSTRAINT chk_trips_min_group_size CHECK (min_group_size >= 1),
    CONSTRAINT chk_trips_max_group_size CHECK (max_group_size >= min_group_size AND max_group_size <= 50),
    CONSTRAINT chk_trips_pricing_model_by_kind CHECK (
        (kind = 'community' AND fixed_price IS NULL)
        OR (kind = 'verified_partner' AND budget_min IS NULL AND budget_max IS NULL)
    ),
    CONSTRAINT chk_trips_cancellation_reason_required CHECK (cancelled_at IS NULL OR cancellation_reason IS NOT NULL)
);

CREATE INDEX ix_trips_destination_status ON trips (destination_id, status);
CREATE INDEX ix_trips_organizer_status ON trips (organizer_id, status);
CREATE INDEX ix_trips_start_date_active ON trips (start_date)
    WHERE status IN ('published', 'accepting_requests', 'confirmed', 'full');

-- ---------------------------------------------------------------------------
-- trip_images — trip gallery storage with explicit ordering and one primary.
-- ---------------------------------------------------------------------------
CREATE TABLE trip_images (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id        UUID NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    image_url      TEXT NOT NULL,
    display_order  SMALLINT NOT NULL DEFAULT 0,
    is_primary     BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_trip_images_one_primary_per_trip ON trip_images (trip_id) WHERE is_primary;
CREATE INDEX ix_trip_images_trip_display_order ON trip_images (trip_id, display_order);

-- ---------------------------------------------------------------------------
-- saved_trips — bookmarking join table, pure N:N with no lifecycle of its own.
-- ---------------------------------------------------------------------------
CREATE TABLE saved_trips (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    trip_id    UUID NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_saved_trips_user_trip UNIQUE (user_id, trip_id)
);

CREATE INDEX ix_saved_trips_user_id ON saved_trips (user_id);
