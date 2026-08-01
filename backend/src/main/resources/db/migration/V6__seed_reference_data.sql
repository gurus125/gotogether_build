-- =============================================================================
-- V6: Seed reference data
-- Destinations and badges are platform-controlled lookup data, not user
-- content — seeding them here (rather than via a separate script) means a
-- fresh dev database is immediately usable. Destination names/categories are
-- taken directly from the approved Create Trip Flow design (Destination step:
-- "Popular from Delhi NCR" chips + 4 category groups), not invented.
-- =============================================================================

INSERT INTO destinations (name, category, popularity_rank, is_active) VALUES
    ('Manali', 'mountains', 1, true),
    ('Kasol', 'mountains', 2, true),
    ('Spiti', 'mountains', 4, true),
    ('Leh', 'mountains', 5, true),
    ('Jibhi', 'mountains', NULL, true),
    ('Tirthan Valley', 'mountains', NULL, true),
    ('Goa', 'beaches', 3, true),
    ('Gokarna', 'beaches', NULL, true),
    ('Andaman', 'beaches', NULL, true),
    ('Rishikesh', 'weekend_escapes', NULL, true),
    ('Mussoorie', 'weekend_escapes', NULL, true),
    ('Jaipur', 'weekend_escapes', NULL, true),
    ('Nainital', 'weekend_escapes', NULL, true),
    ('Bir', 'adventure', NULL, true),
    ('Kedarnath', 'adventure', NULL, true),
    ('Dharamshala', 'adventure', NULL, true);

-- Badge codes referenced by name in the design set (Profile screen "Badges"
-- section) and Business Rules Trust & Discovery Module A.
INSERT INTO badges (code, display_name, description, is_active) VALUES
    ('reliable_traveller', 'Reliable Traveller', 'Consistently high Punctuality and Reliability sub-scores across completed trips.', true),
    ('top_organizer', 'Top Organizer', 'Organized multiple highly-rated trips with strong completion rates.', true),
    ('early_adopter', 'Early Adopter', 'Joined GoTogether in its first launch cohort.', true),
    ('highly_rated', 'Highly Rated', 'Maintains an excellent Trust Score across a meaningful number of reviews.', true),
    ('id_verified', 'ID Verified', 'Completed Government ID verification.', true);
