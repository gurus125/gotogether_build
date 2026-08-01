-- =============================================================================
-- V4: Part 3 — Operations & Platform
-- Source: Database Schema & Data Model Part 3 (Operations & Platform), v1.0.
-- Tables: travel_companies, company_users, company_verifications, reports,
--         report_evidence, analytics_events, audit_logs.
-- Also resolves Part 1's deferred trips.company_id column now that
-- travel_companies exists, per Part 3 Section 2's explanation of why there is
-- no separate "company trips" table.
--
-- Fix #4 (MEDIUM, DB Review) note: the application-level validation tying a
-- Verified Partner Trip's organizer_id to a valid company_users row for its
-- company_id cannot be expressed as a CHECK constraint (would require a
-- cross-table join) — it is implemented in the trip/company module Service
-- layer, not here. Tracked in project memory / kickoff report.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- travel_companies — the business entity behind Verified Partner Trips.
-- ---------------------------------------------------------------------------
CREATE TABLE travel_companies (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name         TEXT NOT NULL,
    legal_name           TEXT NOT NULL,
    registration_number  TEXT NOT NULL,
    gst_number           TEXT,
    logo_url             TEXT,
    description          TEXT,
    website_url          TEXT,
    support_email        TEXT NOT NULL,
    support_phone        TEXT NOT NULL,
    cancellation_policy  TEXT,
    terms_accepted_at    TIMESTAMPTZ,
    status               company_status NOT NULL DEFAULT 'application_submitted',
    suspended_at         TIMESTAMPTZ,
    suspension_reason    TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_travel_companies_registration_number UNIQUE (registration_number),
    CONSTRAINT chk_travel_companies_suspension_reason_required CHECK (suspended_at IS NULL OR suspension_reason IS NOT NULL)
);

CREATE UNIQUE INDEX ux_travel_companies_gst_number ON travel_companies (gst_number) WHERE gst_number IS NOT NULL;
CREATE INDEX ix_travel_companies_status ON travel_companies (status);
CREATE INDEX ix_travel_companies_display_name ON travel_companies (display_name);

-- ---------------------------------------------------------------------------
-- company_users — links human users to the Company they administer.
-- Schema permits multiple admins; MVP business rule (Business Rules
-- Operations Module A) restricts this to exactly one active owner per
-- company at the application layer, not here — lifting that limit later is a
-- business-rule change, not a migration.
-- ---------------------------------------------------------------------------
CREATE TABLE company_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES travel_companies (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    role        company_user_role NOT NULL DEFAULT 'owner',
    status      TEXT NOT NULL DEFAULT 'active',
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_company_users_company_user UNIQUE (company_id, user_id),
    CONSTRAINT chk_company_users_status CHECK (status IN ('active', 'removed'))
);

CREATE INDEX ix_company_users_user_id ON company_users (user_id);

-- ---------------------------------------------------------------------------
-- company_verifications — history of every business-verification attempt.
-- ---------------------------------------------------------------------------
CREATE TABLE company_verifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES travel_companies (id) ON DELETE CASCADE,
    submitted_documents JSONB NOT NULL DEFAULT '[]',
    status              company_verification_status NOT NULL DEFAULT 'under_review',
    reviewed_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    decision_notes      TEXT,
    approved_at         TIMESTAMPTZ,
    is_reverification   BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_company_verifications_company_status ON company_verifications (company_id, status);

-- ---------------------------------------------------------------------------
-- reports — every report filed against a user, trip, message, review, or
-- company; the single moderation intake table regardless of what's reported.
-- ---------------------------------------------------------------------------
CREATE TABLE reports (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id           UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    entity_type           report_entity_type NOT NULL,
    entity_id             UUID NOT NULL,
    reason                report_reason NOT NULL,
    details               TEXT,
    status                report_status NOT NULL DEFAULT 'open',
    priority              report_priority NOT NULL DEFAULT 'routine',
    assigned_moderator_id UUID REFERENCES users (id) ON DELETE SET NULL,
    resolution            TEXT,
    resolution_action     TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at           TIMESTAMPTZ,

    CONSTRAINT chk_reports_resolution_action CHECK (resolution_action IS NULL OR resolution_action IN
        ('dismissed', 'warned', 'restricted', 'suspended', 'removed', 'content_removed')),
    CONSTRAINT chk_reports_resolved_at_status CHECK (resolved_at IS NULL OR status IN ('resolved', 'dismissed'))
);

CREATE INDEX ix_reports_status_priority_created ON reports (status, priority, created_at);
CREATE INDEX ix_reports_assigned_moderator_status ON reports (assigned_moderator_id, status);
CREATE INDEX ix_reports_entity ON reports (entity_type, entity_id);

-- ---------------------------------------------------------------------------
-- report_evidence — metadata-only supporting evidence attached to a Report.
-- ---------------------------------------------------------------------------
CREATE TABLE report_evidence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id       UUID NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    storage_key     TEXT NOT NULL,
    mime_type       TEXT,
    file_size_bytes INTEGER,
    uploaded_by     UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_report_evidence_storage_key UNIQUE (storage_key)
);

-- ---------------------------------------------------------------------------
-- analytics_events — append-only product event stream.
-- ---------------------------------------------------------------------------
CREATE TABLE analytics_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type   analytics_event_type NOT NULL,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    entity_type  TEXT,
    entity_id    UUID,
    metadata     JSONB NOT NULL DEFAULT '{}',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_analytics_events_type_occurred ON analytics_events (event_type, occurred_at);
CREATE INDEX ix_analytics_events_user_occurred ON analytics_events (user_id, occurred_at);

-- ---------------------------------------------------------------------------
-- audit_logs — immutable record of every consequential Moderator/Admin
-- action. Never updated or deleted under any circumstance, including by a
-- System Administrator.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action      audit_action NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id   UUID NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  INET,
    device_info TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_id, created_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- ---------------------------------------------------------------------------
-- trips.company_id — deferred from Part 1 until travel_companies exists.
-- Populated only when kind = 'verified_partner'.
-- ---------------------------------------------------------------------------
ALTER TABLE trips ADD COLUMN company_id UUID REFERENCES travel_companies (id) ON DELETE RESTRICT;

ALTER TABLE trips ADD CONSTRAINT chk_trips_company_id_by_kind CHECK (
    (kind = 'community' AND company_id IS NULL)
    OR (kind = 'verified_partner' AND company_id IS NOT NULL)
);
