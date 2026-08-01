/// `GET /trips/{id}/members` row — mirrors backend `RosterMemberResponse`.
/// `trustScore` is always null — the `trust` module (Phase 5) doesn't exist
/// yet; showing a fabricated number would undermine the trust-first premise
/// (same reasoning as Home's omitted greeting trust score, Phase 2).
class RosterMember {
  const RosterMember({
    required this.userId,
    required this.displayName,
    this.photoUrl,
    required this.isOrganizer,
    required this.joinedAt,
    this.trustScore,
    this.attendanceStatus,
  });

  factory RosterMember.fromJson(Map<String, dynamic> json) => RosterMember(
        userId: json['user_id'] as String,
        displayName: json['display_name'] as String,
        photoUrl: json['photo_url'] as String?,
        isOrganizer: json['is_organizer'] as bool,
        joinedAt: json['joined_at'] as String,
        trustScore: (json['trust_score'] as num?)?.toDouble(),
        attendanceStatus: json['attendance_status'] as String?,
      );

  final String userId;
  final String displayName;
  final String? photoUrl;
  final bool isOrganizer;
  final String joinedAt;
  final double? trustScore;

  /// `"ATTENDED"` / `"NO_SHOW"` / `null` (not yet recorded — including for
  /// a trip that's still in progress, where nothing should be recorded yet).
  final String? attendanceStatus;
}
