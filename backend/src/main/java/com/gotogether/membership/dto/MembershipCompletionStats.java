package com.gotogether.membership.dto;

/**
 * Aggregate trip-completion behaviour for one user, across every trip they've
 * ever held a {@code trip_members} row on (organizer or regular member) —
 * feeds the {@code trust} module's "Trip completion behaviour" component
 * (20% weight, Business Rules Trust & Discovery Module A). See {@code
 * MembershipService#getCompletionStats}'s doc for exactly how {@code
 * lateLeaves} is distinguished from {@code gracefulLeaves}.
 *
 * <p>{@code noShows}: a {@code trip_members} row that reached {@code
 * COMPLETED} status (the trip finished, they were still on the roster) but
 * whose organizer-recorded {@code attendance_status} is {@code NO_SHOW} —
 * "the group went, this person didn't." Counted separately from {@code
 * completed} and excluded from {@link #totalConcluded}'s "quality units" in
 * {@code TrustService#completionComponent} the same way {@code removed} is,
 * so a trip you joined but never actually attended no longer credits your
 * Trust Score (or the Profile "Travel stats" COMPLETED count) as if you'd
 * gone. Still counted in {@link #totalConcluded} itself, since it's a real,
 * concluded outcome that should dilute the completion ratio, not vanish from
 * the denominator entirely.
 */
public record MembershipCompletionStats(int completed, int removed, int lateLeaves, int gracefulLeaves, int noShows) {

    public int totalConcluded() {
        return completed + removed + lateLeaves + gracefulLeaves + noShows;
    }
}
