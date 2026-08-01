import '../../destination/data/destination_models.dart';

enum CreateTripSubmitStatus { idle, submitting, success, error }

/// Local wizard state for the 7-step Create Trip flow (Destination,
/// Departure-locked, Dates, Budget, Title, Description, Review) — mirrors the
/// approved "GoTogether Create Trip Flow.dc.html" prototype's component state
/// shape as closely as Dart allows, so step logic stays traceable back to
/// the design doc's own `Component.state`.
class CreateTripState {
  const CreateTripState({
    this.step = 1,
    this.destinationQuery = '',
    this.destination,
    this.dateMode = 'flexible',
    this.months = const [],
    this.days = 5,
    this.fixedStartDate,
    this.budgetKey,
    this.customMin,
    this.customMax,
    this.title = '',
    this.titleTouched = false,
    this.description = '',
    this.submitStatus = CreateTripSubmitStatus.idle,
    this.errorMessage,
    this.createdTripId,
  });

  final int step;
  final String destinationQuery;
  final DestinationSummary? destination;

  /// `'flexible'` or `'fixed'`.
  final String dateMode;
  final List<String> months;
  final int days;
  final DateTime? fixedStartDate;

  /// One of `'5-10'`, `'10-15'`, `'15-20'`, `'20-plus'`, `'custom'`, or null (not yet chosen).
  final String? budgetKey;
  final String? customMin;
  final String? customMax;
  final String title;
  final bool titleTouched;
  final String description;
  final CreateTripSubmitStatus submitStatus;
  final String? errorMessage;
  final String? createdTripId;

  bool get isFlexible => dateMode == 'flexible';
  bool get isFixed => dateMode == 'fixed';

  /// "Weekend Escape to Manali" / "Sep Trip to Manali" — matches the design's
  /// `suggestedTitle()` exactly, including the fallback string.
  String get suggestedTitle {
    if (destination == null) return 'Your trip';
    final month = months.isNotEmpty ? months.first : null;
    return month != null ? '$month Trip to ${destination!.name}' : 'Weekend Escape to ${destination!.name}';
  }

  /// Matches the design's `suggestedDescription()` exactly.
  String get suggestedDescription {
    final dest = destination?.name ?? 'this destination';
    final when = isFlexible
        ? (months.isNotEmpty ? 'sometime in ${months.first}' : 'in the coming weeks')
        : 'on fixed dates';
    return 'Planning a $days-day trip to $dest, $when. Looking for a few chill, like-minded '
        'travellers to explore together — open to co-planning the stay, food and activities as a group.';
  }

  String get effectiveTitle => title.isNotEmpty ? title : suggestedTitle;

  bool get requiredMetForCurrentStep {
    switch (step) {
      case 1:
        return destination != null;
      case 3:
        return isFlexible ? months.isNotEmpty : fixedStartDate != null;
      case 4:
        return budgetKey != null;
      default:
        return true;
    }
  }

  CreateTripState copyWith({
    int? step,
    String? destinationQuery,
    DestinationSummary? destination,
    bool clearDestination = false,
    String? dateMode,
    List<String>? months,
    int? days,
    DateTime? fixedStartDate,
    bool clearFixedStartDate = false,
    String? budgetKey,
    String? customMin,
    String? customMax,
    String? title,
    bool? titleTouched,
    String? description,
    CreateTripSubmitStatus? submitStatus,
    String? errorMessage,
    bool clearError = false,
    String? createdTripId,
  }) {
    return CreateTripState(
      step: step ?? this.step,
      destinationQuery: destinationQuery ?? this.destinationQuery,
      destination: clearDestination ? null : (destination ?? this.destination),
      dateMode: dateMode ?? this.dateMode,
      months: months ?? this.months,
      days: days ?? this.days,
      fixedStartDate: clearFixedStartDate ? null : (fixedStartDate ?? this.fixedStartDate),
      budgetKey: budgetKey ?? this.budgetKey,
      customMin: customMin ?? this.customMin,
      customMax: customMax ?? this.customMax,
      title: title ?? this.title,
      titleTouched: titleTouched ?? this.titleTouched,
      description: description ?? this.description,
      submitStatus: submitStatus ?? this.submitStatus,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      createdTripId: createdTripId ?? this.createdTripId,
    );
  }
}
