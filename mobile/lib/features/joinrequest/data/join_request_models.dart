/// Mirrors `joinrequest.dto.JoinRequestResponse` (API Spec Section 8).
/// `applicantDisplayName`/`applicantPhotoUrl` are only ever populated on the
/// Organizer's queue (`GET /trips/{id}/join-requests`) — see backend
/// `JoinRequestResponse#withApplicantProfile`'s doc for why every other
/// endpoint returning this shape leaves them null.
class JoinRequestResponse {
  const JoinRequestResponse({
    required this.id,
    required this.tripId,
    required this.applicantId,
    required this.status,
    this.requestMessage,
    this.organizerResponseNote,
    this.waitlistPosition,
    this.decidedAt,
    required this.expiresAt,
    required this.createdAt,
    this.applicantDisplayName,
    this.applicantPhotoUrl,
  });

  factory JoinRequestResponse.fromJson(Map<String, dynamic> json) => JoinRequestResponse(
        id: json['id'] as String,
        tripId: json['trip_id'] as String,
        applicantId: json['applicant_id'] as String,
        status: json['status'] as String,
        requestMessage: json['request_message'] as String?,
        organizerResponseNote: json['organizer_response_note'] as String?,
        waitlistPosition: json['waitlist_position'] as int?,
        decidedAt: json['decided_at'] as String?,
        expiresAt: json['expires_at'] as String,
        createdAt: json['created_at'] as String,
        applicantDisplayName: json['applicant_display_name'] as String?,
        applicantPhotoUrl: json['applicant_photo_url'] as String?,
      );

  final String id;
  final String tripId;
  final String applicantId;

  /// `PENDING` / `ACCEPTED` / `REJECTED` / `WITHDRAWN` / `EXPIRED` / `WAITING_LIST`.
  final String status;
  final String? requestMessage;
  final String? organizerResponseNote;
  final int? waitlistPosition;
  final String? decidedAt;
  final String expiresAt;
  final String createdAt;
  final String? applicantDisplayName;
  final String? applicantPhotoUrl;
}

/// `GET /trips/{id}/join-status` — the caller's current relationship to a
/// trip, driving the Trip Details CTA state. `status` is one of
/// `NOT_REQUESTED` (synthetic — no row exists yet) or a `JoinRequestResponse.status` value.
/// `joinRequestId` is `null` when `status == NOT_REQUESTED`; otherwise it lets
/// the Trip Details "Withdraw" action call `POST /join-requests/{id}/withdraw`
/// directly instead of a second lookup (backend addition beyond the literal API Spec).
class JoinStatusResponse {
  const JoinStatusResponse({this.joinRequestId, required this.status, this.waitlistPosition, this.canReapplyAt});

  factory JoinStatusResponse.fromJson(Map<String, dynamic> json) => JoinStatusResponse(
        joinRequestId: json['join_request_id'] as String?,
        status: json['status'] as String,
        waitlistPosition: json['waitlist_position'] as int?,
        canReapplyAt: json['can_reapply_at'] as String?,
      );

  final String? joinRequestId;
  final String status;
  final int? waitlistPosition;
  final String? canReapplyAt;

  static const notRequested = 'NOT_REQUESTED';
}
