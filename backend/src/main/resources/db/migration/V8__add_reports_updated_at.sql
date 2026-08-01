-- Fix: Report.java (Phase 8) extends AuditableEntity, which requires a
-- trigger-maintained `updated_at` column (see AuditableEntity's doc: "every
-- table gets created_at and updated_at, defaulted and trigger-maintained") —
-- but the `reports` table (V4__part3_operations_platform.sql) was only ever
-- given `created_at`/`resolved_at`, never `updated_at`. Unlike ip_address
-- (V7), this wasn't a wrong type, it was a genuinely missing column: V5's
-- trigger-attaching loop covers every Part 1/Part 2 mutable table plus the
-- three Part 3 company tables (travel_companies, company_users,
-- company_verifications — Phase 7 got this right), but `reports` was never
-- added to that list when Phase 8 introduced it. A report's own fields
-- (status, assigned_moderator_id, resolution, resolution_action) are exactly
-- the kind of "table that changes after creation" this trigger exists for —
-- caught only once the user ran a real `mvn spring-boot:run` against a live
-- Postgres instance (this sandbox has no DB to validate schema against).
ALTER TABLE reports
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TRIGGER trg_reports_updated_at
    BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
