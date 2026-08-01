package com.gotogether.joinrequest.dto;

import com.gotogether.membership.dto.TripMemberResponse;

/** {@code POST /join-requests/{id}/accept} response shape (API Spec Section 8: {@code { join_request, trip_member } }). */
public record JoinRequestAcceptResponse(JoinRequestResponse joinRequest, TripMemberResponse tripMember) {
}
