# GoTogether

Trust-first travel community platform. Flutter (Android + iOS) frontend,
Spring Boot 3 / Java 21 modular monolith backend, PostgreSQL, Redis, S3-compatible
object storage. Built module-by-module against the approved product,
design, and technical documentation — see `backend/docs-cross-reference.md`
for exactly which source document backs which part of the schema.

This is **Phase 0** (foundation): infrastructure, full database schema, the
backend's shared `common` module, and a running-but-placeholder Flutter app
shell. No feature module (auth, trips, chat, etc.) has been built yet —
that starts at Phase 1, one module at a time, per the agreed process.

## Prerequisites (your machine)

- Java 21 + Maven (backend)
- Flutter SDK + Android Studio (mobile) — already installed per our setup
- Docker (Postgres, Redis, MinIO for local dev) — or point at your own
  Postgres/Redis instances via the env vars in `backend/src/main/resources/application.yml`

## Getting the backend running locally

```sh
# 1. Start Postgres, Redis, and MinIO (S3-compatible local object storage)
docker compose up -d

# 2. Build and run — Flyway applies all 6 migrations automatically on boot
cd backend
mvn spring-boot:run
```

On first boot, Flyway creates all 27 tables (Parts 1–3 of the DB Schema),
seeds the destinations/badges reference data, and the app comes up on
`localhost:8080`. Check `backend/src/main/resources/db/migration/` if you
want to read the schema directly — it's organized to mirror the three
source documents (`V2` = Part 1, `V3` = Part 2, `V4` = Part 3) plus a
triggers file (`V5`) and seed data (`V6`).

**Already validated:** all 6 migrations were run against a real local
Postgres 14 instance during this build, including functional tests of the
trickier pieces — the per-room message sequence counter, the single-
organizer-per-trip constraint, the community/verified-partner trip
CHECK constraints, and the `updated_at` trigger. All passed. Re-run them
yourself with `mvn flyway:migrate` any time you want to double-check.

## Getting the mobile app running locally

See `mobile/README.md` — one extra one-time step (`flutter create .`) is
needed to generate the native Android/iOS platform folders before
`flutter run` works, since this environment doesn't have the Flutter SDK to
do that step for you.

## What's deliberately not here yet

Per the DB Schema Review's own findings (all baked into the migrations
already, not left as follow-up):

- Every table has its documented constraints, including the two the review
  flagged as blocking (single-organizer-per-trip; the `chat_participants.role`
  sync-drift fix, resolved by dropping the column and deriving role via
  `v_chat_participants_with_role` at read time).
- The one item genuinely deferred to application code (can't be a DB
  constraint): validating that a Verified Partner Trip's `organizer_id` is
  actually a `company_users` row for that `company_id`. This lands with the
  `company` module in Phase 7.

Two open questions from the kickoff report are still unresolved and will
affect later phases: no Chat screen mockup was in the design set, and the
Admin Panel / company staff portal has API coverage but no screens in the
(all-mobile) design set. Worth resolving before Phase 4 and Phase 7-8
respectively.

## Roadmap

Phase 0 (this commit) → Phase 1: Identity & Access (auth/user/profile) →
Phase 2: Core Trip Domain → Phase 3: Social Mechanics (join requests,
membership) → Phase 4: Communication (chat) → Phase 5: Trust & Reputation
(reviews, trust score) → Phase 6: Notifications → Phase 7: Travel Companies
→ Phase 8: Trust & Safety Operations (reports, admin) → Phase 9: Analytics
& Polish → Phase 10: Hardening & Launch Readiness.

Full detail in the engineering kickoff report from the documentation review.
