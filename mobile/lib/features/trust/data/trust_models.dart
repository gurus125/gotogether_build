/// Mirrors `trust.dto.TrustScoreComponents` — every field nullable since a
/// brand-new user's row has none computed yet (just the seeded 6.5
/// `currentScore`). Not the same thing as the mockup's six review sub-score
/// bars (Behaviour/Punctuality/.../Reliability) — those are computed
/// client-side from the reviewee's published reviews (see
/// `trust_reviews_screen.dart`'s doc comment for why).
class TrustScoreComponents {
  const TrustScoreComponents({
    this.reviews,
    this.completion,
    this.verification,
    this.organizer,
    this.reportsPenalty,
    this.accountActivity,
    this.profileCompleteness,
  });

  factory TrustScoreComponents.fromJson(Map<String, dynamic> json) => TrustScoreComponents(
        reviews: (json['reviews'] as num?)?.toDouble(),
        completion: (json['completion'] as num?)?.toDouble(),
        verification: (json['verification'] as num?)?.toDouble(),
        organizer: (json['organizer'] as num?)?.toDouble(),
        reportsPenalty: (json['reports_penalty'] as num?)?.toDouble(),
        accountActivity: (json['account_activity'] as num?)?.toDouble(),
        profileCompleteness: (json['profile_completeness'] as num?)?.toDouble(),
      );

  final double? reviews;
  final double? completion;
  final double? verification;
  final double? organizer;
  final double? reportsPenalty;
  final double? accountActivity;
  final double? profileCompleteness;
}

/// Mirrors `trust.dto.TrustScoreResponse` — serves both `GET
/// /users/{id}/trust-score` (public breakdown, `improvementTips` always
/// null) and `GET /users/me/trust-score` (self view, populates
/// `improvementTips`).
class TrustScoreResponse {
  const TrustScoreResponse({required this.currentScore, required this.level, required this.components, required this.improvementTips});

  factory TrustScoreResponse.fromJson(Map<String, dynamic> json) => TrustScoreResponse(
        currentScore: (json['current_score'] as num).toDouble(),
        level: json['level'] as String,
        components: TrustScoreComponents.fromJson(json['components'] as Map<String, dynamic>? ?? const {}),
        improvementTips: (json['improvement_tips'] as List<dynamic>? ?? const []).cast<String>(),
      );

  final double currentScore;

  /// One of `EXCELLENT` / `GOOD` / `BUILDING` / `CAUTION` / `RESTRICTED_TRIGGER`.
  final String level;
  final TrustScoreComponents components;
  final List<String> improvementTips;
}

/// One row of `GET /users/me/trust-score/history`.
class TrustScoreHistoryEntry {
  const TrustScoreHistoryEntry({
    required this.id,
    required this.oldScore,
    required this.newScore,
    required this.reason,
    this.relatedReviewId,
    this.relatedTripId,
    required this.createdAt,
  });

  factory TrustScoreHistoryEntry.fromJson(Map<String, dynamic> json) => TrustScoreHistoryEntry(
        id: json['id'] as String,
        oldScore: (json['old_score'] as num).toDouble(),
        newScore: (json['new_score'] as num).toDouble(),
        reason: json['reason'] as String,
        relatedReviewId: json['related_review_id'] as String?,
        relatedTripId: json['related_trip_id'] as String?,
        createdAt: json['created_at'] as String,
      );

  final String id;
  final double oldScore;
  final double newScore;
  final String reason;
  final String? relatedReviewId;
  final String? relatedTripId;
  final String createdAt;
}
