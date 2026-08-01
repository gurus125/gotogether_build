/// Mirrors `review.dto.ReviewResponse` (API Spec Section 11) — one published
/// or (from the reviewer's own POST response) freshly-submitted review.
/// `highlightedTraits` is only ever populated on the `GET /users/{id}/reviews`
/// listing (Trust & Discovery Module B's "recurring positive traits... 3+
/// reviews"), empty on a fresh submission response.
class ReviewResponse {
  const ReviewResponse({
    required this.id,
    required this.tripId,
    required this.reviewerId,
    this.reviewerDisplayName,
    this.reviewerPhotoUrl,
    required this.revieweeId,
    required this.ratingBehaviour,
    required this.ratingPunctuality,
    required this.ratingCommunication,
    required this.ratingCooperation,
    required this.ratingSafety,
    required this.ratingReliability,
    required this.overallRating,
    this.comment,
    required this.status,
    required this.visibility,
    this.publishedAt,
    required this.createdAt,
    required this.highlightedTraits,
  });

  factory ReviewResponse.fromJson(Map<String, dynamic> json) => ReviewResponse(
        id: json['id'] as String,
        tripId: json['trip_id'] as String,
        reviewerId: json['reviewer_id'] as String,
        reviewerDisplayName: json['reviewer_display_name'] as String?,
        reviewerPhotoUrl: json['reviewer_photo_url'] as String?,
        revieweeId: json['reviewee_id'] as String,
        ratingBehaviour: json['rating_behaviour'] as int,
        ratingPunctuality: json['rating_punctuality'] as int,
        ratingCommunication: json['rating_communication'] as int,
        ratingCooperation: json['rating_cooperation'] as int,
        ratingSafety: json['rating_safety'] as int,
        ratingReliability: json['rating_reliability'] as int,
        overallRating: json['overall_rating'] as int,
        comment: json['comment'] as String?,
        status: json['status'] as String,
        visibility: json['visibility'] as String,
        publishedAt: json['published_at'] as String?,
        createdAt: json['created_at'] as String,
        highlightedTraits: (json['highlighted_traits'] as List<dynamic>? ?? const []).cast<String>(),
      );

  final String id;
  final String tripId;
  final String reviewerId;
  final String? reviewerDisplayName;
  final String? reviewerPhotoUrl;
  final String revieweeId;
  final int ratingBehaviour;
  final int ratingPunctuality;
  final int ratingCommunication;
  final int ratingCooperation;
  final int ratingSafety;
  final int ratingReliability;
  final int overallRating;
  final String? comment;

  /// `SUBMITTED` / `PUBLISHED` / `HIDDEN` / `REMOVED`.
  final String status;

  /// `BLIND` / `PUBLISHED` / `HIDDEN`.
  final String visibility;
  final String? publishedAt;
  final String createdAt;
  final List<String> highlightedTraits;
}

/// `POST /trips/{id}/reviews` request body — mirrors
/// `review.dto.SubmitReviewRequest`. All six sub-scores and `overallRating`
/// are required, 1-5 (Section 20); `comment` is optional, capped at 280
/// chars.
class SubmitReviewRequest {
  const SubmitReviewRequest({
    required this.revieweeId,
    required this.ratingBehaviour,
    required this.ratingPunctuality,
    required this.ratingCommunication,
    required this.ratingCooperation,
    required this.ratingSafety,
    required this.ratingReliability,
    required this.overallRating,
    this.comment,
  });

  final String revieweeId;
  final int ratingBehaviour;
  final int ratingPunctuality;
  final int ratingCommunication;
  final int ratingCooperation;
  final int ratingSafety;
  final int ratingReliability;
  final int overallRating;
  final String? comment;

  Map<String, dynamic> toJson() => {
        'reviewee_id': revieweeId,
        'rating_behaviour': ratingBehaviour,
        'rating_punctuality': ratingPunctuality,
        'rating_communication': ratingCommunication,
        'rating_cooperation': ratingCooperation,
        'rating_safety': ratingSafety,
        'rating_reliability': ratingReliability,
        'overall_rating': overallRating,
        if (comment != null && comment!.isNotEmpty) 'comment': comment,
      };
}
