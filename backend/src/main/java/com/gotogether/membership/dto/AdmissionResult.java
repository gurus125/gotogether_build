package com.gotogether.membership.dto;

/**
 * Result of {@code MembershipService.admitOrWaitlist} — {@code admitted =
 * false} means the trip filled up between the caller's initial checks and
 * this atomic operation (the Accept-vs-capacity race API Spec Section 23
 * flags); {@code tripMember} is {@code null} in that case since no row was
 * inserted. See {@code JoinRequestService.accept}'s doc for why the caller
 * translates a lost race into an HTTP response itself rather than this
 * method throwing (throwing here would roll back the waiting-list state this
 * same operation is responsible for persisting).
 */
public record AdmissionResult(boolean admitted, TripMemberResponse tripMember) {
}
