-- =============================================================================
-- V1: Extensions and Enumerations
-- Source of truth: Database Schema & Data Model Parts 1-3 (DB Review verdict:
-- "Approved for implementation" 8.7/10, all enum value lists taken verbatim).
-- =============================================================================

-- gen_random_uuid() — every table's PK strategy per Part 1 Section 1.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Part 1 enums
-- ---------------------------------------------------------------------------
CREATE TYPE user_status AS ENUM ('registered', 'verified', 'restricted', 'suspended');
CREATE TYPE verification_level AS ENUM ('none', 'phone', 'email', 'id_approved');
CREATE TYPE account_role AS ENUM ('individual', 'moderator', 'admin');
CREATE TYPE verification_type AS ENUM ('phone', 'email', 'government_id', 'selfie_match');
CREATE TYPE verification_status AS ENUM ('pending', 'approved', 'rejected');
CREATE TYPE rejection_reason AS ENUM ('blurry_image', 'name_mismatch', 'expired_document', 'selfie_mismatch', 'unsupported_document_type');
CREATE TYPE destination_category AS ENUM ('mountains', 'beaches', 'weekend_escapes', 'adventure');
CREATE TYPE trip_kind AS ENUM ('community', 'verified_partner');
CREATE TYPE trip_status AS ENUM ('draft', 'published', 'accepting_requests', 'confirmed', 'full', 'in_progress', 'completed', 'cancelled', 'archived');
CREATE TYPE trip_visibility AS ENUM ('public', 'private');
CREATE TYPE profile_visibility AS ENUM ('public');

-- ---------------------------------------------------------------------------
-- Part 2 enums
-- ---------------------------------------------------------------------------
CREATE TYPE join_request_status AS ENUM ('pending', 'accepted', 'rejected', 'withdrawn', 'expired', 'waiting_list');
CREATE TYPE membership_status AS ENUM ('joined', 'left', 'removed', 'completed');
CREATE TYPE attendance_status AS ENUM ('attended', 'no_show');
CREATE TYPE review_status AS ENUM ('submitted', 'published', 'hidden', 'removed');
CREATE TYPE review_visibility AS ENUM ('blind', 'published', 'hidden');
CREATE TYPE trust_level AS ENUM ('excellent', 'good', 'building', 'caution', 'restricted_trigger');
CREATE TYPE chat_room_type AS ENUM ('trip', 'direct');
CREATE TYPE message_type AS ENUM ('text', 'image', 'voice', 'document', 'location', 'poll', 'expense', 'system');
CREATE TYPE attachment_type AS ENUM ('image', 'document', 'voice', 'location_pin');
CREATE TYPE notification_type AS ENUM ('join_request_received', 'join_request_accepted', 'join_request_rejected', 'chat_message', 'chat_mention', 'trip_update', 'departure_reminder', 'review_reminder', 'verification_decision', 'trust_update', 'announcement');
CREATE TYPE notification_status AS ENUM ('generated', 'queued', 'delivered', 'read', 'dismissed', 'archived', 'failed');

-- ---------------------------------------------------------------------------
-- Part 3 enums
-- ---------------------------------------------------------------------------
CREATE TYPE company_status AS ENUM ('application_submitted', 'under_review', 'verified', 'suspended', 'rejected', 'removed');
CREATE TYPE company_user_role AS ENUM ('owner', 'manager', 'support');
CREATE TYPE company_verification_status AS ENUM ('under_review', 'approved', 'rejected');
CREATE TYPE report_status AS ENUM ('open', 'in_review', 'resolved', 'dismissed');
CREATE TYPE report_priority AS ENUM ('emergency', 'safety', 'routine');
CREATE TYPE report_reason AS ENUM ('harassment', 'unsafe_behaviour', 'fraud', 'fake_profile', 'spam', 'inappropriate_content', 'no_show', 'identity_mismatch', 'other');
-- Fixed enum for reports.entity_type specifically (Part 3: "unlike notifications'
-- free TEXT, Reports is a structurally important table worth enforcing").
-- notifications.entity_type / analytics_events.entity_type / audit_logs.entity_type
-- remain plain TEXT by design (Part 2 Section 4 / Part 3 Sections 3, "loose
-- reference pattern") — validated against a shared application-level constant
-- list (DB Review "naming inconsistencies" risk item), not a DB enum, since
-- their target table set is broader/more likely to grow.
CREATE TYPE report_entity_type AS ENUM ('user', 'trip', 'message', 'review', 'company');
CREATE TYPE analytics_event_type AS ENUM ('trip_created', 'trip_published', 'trip_joined', 'trip_completed', 'trip_cancelled', 'search_performed', 'review_submitted', 'trust_score_updated', 'verification_approved', 'notification_opened');
CREATE TYPE audit_action AS ENUM ('user_restricted', 'user_suspended', 'user_removed', 'trip_hidden', 'trip_force_cancelled', 'review_hidden', 'review_removed', 'company_verified', 'company_suspended', 'verification_approved', 'verification_rejected', 'trust_score_frozen', 'role_assigned');
