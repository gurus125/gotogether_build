import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../auth/data/auth_models.dart';
import '../../destination/data/destination_models.dart';
import '../data/trip_api.dart';
import '../data/trip_models.dart';
import 'create_trip_state.dart';
import 'trip_providers.dart';

const _monthNumbers = {
  'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
  'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12,
};

/// Drives the 7-step Create Trip wizard, following the same
/// `StateNotifier`-per-flow pattern as `AuthController`.
class CreateTripController extends StateNotifier<CreateTripState> {
  CreateTripController(this._tripApi) : super(const CreateTripState());

  final TripApi _tripApi;

  void setDestinationQuery(String query) => state = state.copyWith(destinationQuery: query);

  void selectDestination(DestinationSummary destination) =>
      state = state.copyWith(destination: destination, destinationQuery: '');

  void setDateMode(String mode) => state = state.copyWith(dateMode: mode);

  void toggleMonth(String month) {
    final months = List<String>.from(state.months);
    months.contains(month) ? months.remove(month) : months.add(month);
    state = state.copyWith(months: months);
  }

  void incrementDays() => state = state.copyWith(days: (state.days + 1).clamp(1, 30));

  void decrementDays() => state = state.copyWith(days: (state.days - 1).clamp(1, 30));

  void setFixedStartDate(DateTime date) => state = state.copyWith(fixedStartDate: date);

  void selectBudget(String key) => state = state.copyWith(budgetKey: key);

  void setCustomMin(String value) => state = state.copyWith(customMin: value);

  void setCustomMax(String value) => state = state.copyWith(customMax: value);

  void setTitle(String value) => state = state.copyWith(title: value, titleTouched: true);

  void useSuggestedTitle() => state = state.copyWith(title: state.suggestedTitle, titleTouched: true);

  void setDescription(String value) => state = state.copyWith(description: value);

  void useSuggestedDescription() => state = state.copyWith(description: state.suggestedDescription);

  void goBack() => state = state.copyWith(step: (state.step - 1).clamp(1, 7));

  void goToStep(int step) => state = state.copyWith(step: step);

  Future<void> goNextOrSubmit() async {
    if (!state.requiredMetForCurrentStep) return;
    if (state.step < 7) {
      state = state.copyWith(step: state.step + 1);
      return;
    }
    await _submit();
  }

  void restart() => state = const CreateTripState();

  /// Resolves the wizard's date inputs into a concrete `(start_date,
  /// end_date)` pair the backend requires (`trips.start_date`/`end_date` are
  /// `NOT NULL DATE`). The approved Create Trip design's "Flexible" mode only
  /// ever collects month chips + a day count — never an exact date — so this
  /// derivation was explicitly flagged and confirmed during the Phase 2 docs
  /// review (2026-07-22): start_date = the 1st of the earliest selected
  /// month's next future occurrence; end_date = start_date + (days - 1),
  /// keeping `isFlexibleDates: true` so the trip is still labeled approximate.
  ({DateTime start, DateTime end}) _resolveDates() {
    final DateTime start;
    if (state.isFixed && state.fixedStartDate != null) {
      start = state.fixedStartDate!;
    } else {
      final monthAbbr = state.months.isNotEmpty ? state.months.first : null;
      start = monthAbbr != null ? _nextOccurrenceOfMonth(monthAbbr) : DateTime.now().add(const Duration(days: 14));
    }
    final end = start.add(Duration(days: state.days - 1));
    return (start: start, end: end);
  }

  DateTime _nextOccurrenceOfMonth(String monthAbbr) {
    final monthNum = _monthNumbers[monthAbbr]!;
    final tomorrow = DateTime.now().add(const Duration(days: 1));
    var candidate = DateTime(tomorrow.year, monthNum, 1);
    if (candidate.isBefore(DateTime(tomorrow.year, tomorrow.month, tomorrow.day))) {
      candidate = DateTime(tomorrow.year + 1, monthNum, 1);
    }
    return candidate;
  }

  int? _parseBudget(String? raw) => raw == null || raw.isEmpty ? null : int.tryParse(raw);

  Future<void> _submit() async {
    final destination = state.destination;
    if (destination == null) return;

    state = state.copyWith(submitStatus: CreateTripSubmitStatus.submitting, clearError: true);
    try {
      final dates = _resolveDates();
      final dateFormat = DateFormat('yyyy-MM-dd');

      int? budgetMin;
      int? budgetMax;
      switch (state.budgetKey) {
        case '5-10':
          budgetMin = 5000;
          budgetMax = 10000;
        case '10-15':
          budgetMin = 10000;
          budgetMax = 15000;
        case '15-20':
          budgetMin = 15000;
          budgetMax = 20000;
        case '20-plus':
          budgetMin = 20000;
          budgetMax = null;
        case 'custom':
          budgetMin = _parseBudget(state.customMin);
          budgetMax = _parseBudget(state.customMax);
      }

      final request = CreateTripRequest(
        destinationId: destination.id,
        startDate: dateFormat.format(dates.start),
        endDate: dateFormat.format(dates.end),
        isFlexibleDates: state.isFlexible,
        budgetMin: budgetMin,
        budgetMax: budgetMax,
        title: state.effectiveTitle,
        description: state.description.isNotEmpty ? state.description : state.suggestedDescription,
      );

      final draft = await _tripApi.createDraft(request);
      await _tripApi.publish(draft.id);

      state = state.copyWith(submitStatus: CreateTripSubmitStatus.success, createdTripId: draft.id);
    } on ApiException catch (e) {
      state = state.copyWith(submitStatus: CreateTripSubmitStatus.error, errorMessage: e.message);
    }
  }
}

final createTripControllerProvider = StateNotifierProvider.autoDispose<CreateTripController, CreateTripState>((ref) {
  return CreateTripController(ref.watch(tripApiProvider));
});
