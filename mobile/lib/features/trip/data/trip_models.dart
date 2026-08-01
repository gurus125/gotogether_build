import '../../destination/data/destination_models.dart';

/// Generic wrapper for the backend's cursor-pagination envelope
/// (`common.dto.CursorPageResponse`) — note this is `{items, next_cursor,
/// has_more}`, not the API Specification's documented `{data, pagination:
/// {next_cursor, has_more}}` shape; the actual Phase 1/2 backend code uses
/// the flatter shape, so this mirrors what's really returned.
class CursorPage<T> {
  const CursorPage({required this.items, this.nextCursor, required this.hasMore});

  factory CursorPage.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) fromJson) => CursorPage(
        items: (json['items'] as List<dynamic>? ?? const []).map((e) => fromJson(e as Map<String, dynamic>)).toList(),
        nextCursor: json['next_cursor'] as String?,
        hasMore: json['has_more'] as bool? ?? false,
      );

  final List<T> items;
  final String? nextCursor;
  final bool hasMore;
}

/// Mirrors `trip.dto.TravelStatsResponse` (`GET /users/me/travel-stats`) —
/// the Profile screen's "Travel stats" card (JOINED/COMPLETED/ORGANIZED).
class TravelStats {
  const TravelStats({required this.joined, required this.completed, required this.organized});

  factory TravelStats.fromJson(Map<String, dynamic> json) => TravelStats(
        joined: json['joined'] as int,
        completed: json['completed'] as int,
        organized: json['organized'] as int,
      );

  final int joined;
  final int completed;
  final int organized;
}

/// Compact card representation (Home's "Trips for you" / "Verified partner
/// trips" rows, Explore results) — mirrors `trip.dto.TripSummary`.
class TripSummary {
  const TripSummary({
    required this.id,
    required this.title,
    required this.kind,
    required this.status,
    required this.destination,
    required this.startDate,
    required this.endDate,
    this.budgetMin,
    this.budgetMax,
    this.fixedPrice,
    required this.maxGroupSize,
    required this.joinedCount,
    this.coverImageUrl,
    required this.organizerId,
    required this.organizerDisplayName,
    this.organizerPhotoUrl,
    required this.organizerVerified,
    this.companyId,
  });

  factory TripSummary.fromJson(Map<String, dynamic> json) => TripSummary(
        id: json['id'] as String,
        title: json['title'] as String,
        kind: json['kind'] as String,
        status: json['status'] as String,
        destination: DestinationSummary.fromJson(json['destination'] as Map<String, dynamic>),
        startDate: json['start_date'] as String,
        endDate: json['end_date'] as String,
        budgetMin: json['budget_min'] as int?,
        budgetMax: json['budget_max'] as int?,
        fixedPrice: json['fixed_price'] as int?,
        maxGroupSize: json['max_group_size'] as int,
        joinedCount: json['joined_count'] as int,
        coverImageUrl: json['cover_image_url'] as String?,
        organizerId: json['organizer_id'] as String,
        organizerDisplayName: json['organizer_display_name'] as String,
        organizerPhotoUrl: json['organizer_photo_url'] as String?,
        organizerVerified: json['organizer_verified'] as bool,
        companyId: json['company_id'] as String?,
      );

  final String id;
  final String title;

  /// `COMMUNITY` or `VERIFIED_PARTNER`.
  final String kind;

  /// `DRAFT` / `PUBLISHED` / `ACCEPTING_REQUESTS` / `CONFIRMED` / `FULL` /
  /// `IN_PROGRESS` / `COMPLETED` / `CANCELLED` / `ARCHIVED`.
  final String status;
  final DestinationSummary destination;
  final String startDate;
  final String endDate;
  final int? budgetMin;
  final int? budgetMax;
  final int? fixedPrice;
  final int maxGroupSize;

  /// Real as of Phase 3 — the backend overlays a live `trip_members` count
  /// onto every card-producing endpoint (see backend `TripController`'s
  /// `withLiveCounts`). Always 0 for a trip with only its organizer seated.
  final int joinedCount;
  final String? coverImageUrl;
  final String organizerId;
  final String organizerDisplayName;
  final String? organizerPhotoUrl;
  final bool organizerVerified;

  /// Phase 7: non-null exactly when `kind == VERIFIED_PARTNER` — the trip is
  /// run by this Travel Company (`organizerDisplayName`/`organizerPhotoUrl`
  /// are already the Company's own branding in that case, not a named
  /// employee's — see backend `TripService.buildOrganizerSummary`'s doc).
  final String? companyId;
}

class TripImage {
  const TripImage({required this.id, required this.imageUrl, required this.displayOrder, required this.primary});

  factory TripImage.fromJson(Map<String, dynamic> json) => TripImage(
        id: json['id'] as String,
        imageUrl: json['image_url'] as String,
        displayOrder: json['display_order'] as int,
        primary: json['primary'] as bool,
      );

  final String id;
  final String imageUrl;
  final int displayOrder;
  final bool primary;
}

/// Full trip payload — mirrors `trip.dto.TripResponse`.
class TripDetails {
  const TripDetails({
    required this.id,
    required this.organizerId,
    this.companyId,
    required this.destination,
    required this.kind,
    required this.status,
    required this.visibility,
    required this.title,
    required this.description,
    this.tripType,
    required this.isFlexibleDates,
    required this.startDate,
    required this.endDate,
    this.budgetMin,
    this.budgetMax,
    this.fixedPrice,
    required this.minGroupSize,
    required this.maxGroupSize,
    required this.isApprovalRequired,
    required this.isWaitlistAllowed,
    this.meetingPoint,
    this.publishedAt,
    this.cancelledAt,
    this.cancellationReason,
    this.completedAt,
    required this.images,
  });

  factory TripDetails.fromJson(Map<String, dynamic> json) => TripDetails(
        id: json['id'] as String,
        organizerId: json['organizer_id'] as String,
        companyId: json['company_id'] as String?,
        destination: DestinationSummary.fromJson(json['destination'] as Map<String, dynamic>),
        kind: json['kind'] as String,
        status: json['status'] as String,
        visibility: json['visibility'] as String,
        title: json['title'] as String,
        description: json['description'] as String?,
        tripType: json['trip_type'] as String?,
        isFlexibleDates: json['is_flexible_dates'] as bool,
        startDate: json['start_date'] as String,
        endDate: json['end_date'] as String,
        budgetMin: json['budget_min'] as int?,
        budgetMax: json['budget_max'] as int?,
        fixedPrice: json['fixed_price'] as int?,
        minGroupSize: json['min_group_size'] as int,
        maxGroupSize: json['max_group_size'] as int,
        isApprovalRequired: json['is_approval_required'] as bool,
        isWaitlistAllowed: json['is_waitlist_allowed'] as bool,
        meetingPoint: json['meeting_point'] as String?,
        publishedAt: json['published_at'] as String?,
        cancelledAt: json['cancelled_at'] as String?,
        cancellationReason: json['cancellation_reason'] as String?,
        completedAt: json['completed_at'] as String?,
        images: (json['images'] as List<dynamic>? ?? const [])
            .map((e) => TripImage.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  final String id;
  final String organizerId;

  /// Phase 7: non-null exactly when `kind == VERIFIED_PARTNER` — see
  /// `TripSummary.companyId`'s doc.
  final String? companyId;
  final DestinationSummary destination;
  final String kind;
  final String status;
  final String visibility;
  final String title;
  final String? description;
  final String? tripType;
  final bool isFlexibleDates;
  final String startDate;
  final String endDate;
  final int? budgetMin;
  final int? budgetMax;
  final int? fixedPrice;
  final int minGroupSize;
  final int maxGroupSize;
  final bool isApprovalRequired;
  final bool isWaitlistAllowed;
  final String? meetingPoint;
  final String? publishedAt;
  final String? cancelledAt;
  final String? cancellationReason;
  final String? completedAt;
  final List<TripImage> images;
}

/// A single row of Trip Details' "who else is going" preview — mirrors
/// backend `TripDetailsResponse.MemberPreview`. Real as of Phase 3 (the
/// `membership` module); always empty before that.
class MemberPreview {
  const MemberPreview({required this.displayName, this.photoUrl});

  factory MemberPreview.fromJson(Map<String, dynamic> json) => MemberPreview(
        displayName: json['display_name'] as String,
        photoUrl: json['photo_url'] as String?,
      );

  final String displayName;
  final String? photoUrl;
}

class OrganizerSummary {
  const OrganizerSummary({required this.id, required this.displayName, this.photoUrl, required this.idVerified});

  factory OrganizerSummary.fromJson(Map<String, dynamic> json) => OrganizerSummary(
        id: json['id'] as String,
        displayName: json['display_name'] as String,
        photoUrl: json['photo_url'] as String?,
        idVerified: json['id_verified'] as bool,
      );

  final String id;
  final String displayName;
  final String? photoUrl;
  final bool idVerified;
}

/// `GET /trips/{id}` — the Trip Details screen's full payload. `membersPreview`
/// and `joinStatus` are real as of Phase 3 (`membership`/`joinrequest`
/// modules); `compatibilityScore` stays always null — Chapter 4's
/// declared-preference matching formula still isn't defined (same Phase 2
/// docs-review flag, still open). The screen hides that one section rather
/// than rendering a fake value.
class TripDetailsResponse {
  const TripDetailsResponse({
    required this.trip,
    required this.organizer,
    required this.membersPreview,
    this.compatibilityScore,
    this.joinStatus,
  });

  factory TripDetailsResponse.fromJson(Map<String, dynamic> json) => TripDetailsResponse(
        trip: TripDetails.fromJson(json['trip'] as Map<String, dynamic>),
        organizer: OrganizerSummary.fromJson(json['organizer'] as Map<String, dynamic>),
        membersPreview: (json['members_preview'] as List<dynamic>? ?? const [])
            .map((e) => MemberPreview.fromJson(e as Map<String, dynamic>))
            .toList(),
        compatibilityScore: json['compatibility_score'] as int?,
        joinStatus: json['join_status'] as String?,
      );

  final TripDetails trip;
  final OrganizerSummary organizer;
  final List<MemberPreview> membersPreview;
  final int? compatibilityScore;
  final String? joinStatus;
}

/// `POST /trips` — mirrors `trip.dto.CreateTripRequest`. `startDate`/`endDate`
/// are always concrete `YYYY-MM-DD` strings — the wizard's "Flexible" date
/// mode (month chips + a day count) is resolved into a concrete date pair
/// client-side before this is sent; see `CreateTripDraft.resolvedDates` in
/// `create_trip_controller.dart` for the exact derivation rule.
class CreateTripRequest {
  const CreateTripRequest({
    required this.destinationId,
    required this.startDate,
    required this.endDate,
    required this.isFlexibleDates,
    this.budgetMin,
    this.budgetMax,
    required this.title,
    this.description,
  });

  final String destinationId;
  final String startDate;
  final String endDate;
  final bool isFlexibleDates;
  final int? budgetMin;
  final int? budgetMax;
  final String title;
  final String? description;

  Map<String, dynamic> toJson() => {
        'destination_id': destinationId,
        'start_date': startDate,
        'end_date': endDate,
        'is_flexible_dates': isFlexibleDates,
        'budget_min': budgetMin,
        'budget_max': budgetMax,
        'title': title,
        'description': description,
      };
}

/// `PATCH /trips/{id}` — mirrors backend `trip.dto.UpdateTripRequest`. "Manage
/// Trip": the fields the Create Trip wizard's Review step always promised
/// were editable "right after publishing" but had no screen wired to them —
/// see `EditTripScreen`'s class doc. PATCH semantics: every field is
/// nullable/omittable and only non-null ones are applied server-side, so a
/// field left untouched in the edit form simply isn't sent.
class UpdateTripRequest {
  const UpdateTripRequest({
    this.title,
    this.description,
    this.startDate,
    this.endDate,
    this.budgetMin,
    this.budgetMax,
    this.minGroupSize,
    this.maxGroupSize,
    this.meetingPoint,
    this.isApprovalRequired,
    this.isWaitlistAllowed,
  });

  final String? title;
  final String? description;
  final String? startDate;
  final String? endDate;
  final int? budgetMin;
  final int? budgetMax;
  final int? minGroupSize;
  final int? maxGroupSize;
  final String? meetingPoint;
  final bool? isApprovalRequired;
  final bool? isWaitlistAllowed;

  Map<String, dynamic> toJson() => {
        if (title != null) 'title': title,
        if (description != null) 'description': description,
        if (startDate != null) 'start_date': startDate,
        if (endDate != null) 'end_date': endDate,
        if (budgetMin != null) 'budget_min': budgetMin,
        if (budgetMax != null) 'budget_max': budgetMax,
        if (minGroupSize != null) 'min_group_size': minGroupSize,
        if (maxGroupSize != null) 'max_group_size': maxGroupSize,
        if (meetingPoint != null) 'meeting_point': meetingPoint,
        if (isApprovalRequired != null) 'is_approval_required': isApprovalRequired,
        if (isWaitlistAllowed != null) 'is_waitlist_allowed': isWaitlistAllowed,
      };
}
