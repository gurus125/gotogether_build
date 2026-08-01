package com.gotogether.joinrequest.dto;

import jakarta.validation.constraints.Size;

/** {@code POST /trips/{id}/join-requests} (API Spec Section 8). {@code requestMessage} is optional, ≤300 chars (Section 20). */
public record CreateJoinRequestRequest(@Size(max = 300) String requestMessage) {
}
