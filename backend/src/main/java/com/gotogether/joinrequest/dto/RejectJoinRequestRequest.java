package com.gotogether.joinrequest.dto;

/** {@code POST /join-requests/{id}/reject} — note is optional (Business Rules Core User Features Module B: "no reason required from Organizer but Organizer may optionally add one"). */
public record RejectJoinRequestRequest(String note) {
}
