-- Fix: audit_logs.ip_address was created as native Postgres INET (V4), but
-- AuditLog.java maps it as a plain, unannotated `String` — the same
-- "loose TEXT" convention already used for `device_info` on this exact same
-- table and for every other plain-String column in this schema. Hibernate's
-- schema *validation* (ddl-auto: validate) checks structural column-type
-- compatibility at boot regardless of whether the column is ever actually
-- written to, so this broke `mvn spring-boot:run` even though `ipAddress` is
-- always null in this pass (no HttpServletRequest capture is wired into any
-- controller yet — see AuditLog.java's own doc comment, which flagged this
-- exact mismatch as a risk for "whoever wires real IP capture later" without
-- realizing it would actually block startup immediately, not just later).
--
-- INET was arguably the "more correct" Postgres type for this column, but
-- properly supporting it needs a custom Hibernate UserType/JdbcType (the
-- same category of work as the JSONB pattern already documented on
-- CompanyVerification#submittedDocuments) for a field nothing writes yet.
-- TEXT is the pragmatic fix now; revisit as a real INET mapping only if/when
-- IP capture is actually implemented.
ALTER TABLE audit_logs
    ALTER COLUMN ip_address TYPE TEXT USING ip_address::TEXT;
