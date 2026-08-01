import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/trip_api.dart';
import '../data/trip_models.dart';

final tripApiProvider = Provider<TripApi>((ref) => TripApi(ref.watch(apiClientProvider)));

/// Home's "Trips for you" — see backend `TripService#recommended`'s doc for
/// the placeholder (newest-first) ranking this currently returns pending
/// Chapter 4's Best Match formula.
final recommendedTripsProvider = FutureProvider.autoDispose<CursorPage<TripSummary>>((ref) {
  return ref.watch(tripApiProvider).recommended();
});

/// Home's "Verified partner trips" row.
final verifiedPartnerTripsProvider = FutureProvider.autoDispose<CursorPage<TripSummary>>((ref) {
  return ref.watch(tripApiProvider).list(kind: 'VERIFIED_PARTNER');
});

final tripDetailsProvider = FutureProvider.autoDispose.family<TripDetailsResponse, String>((ref, tripId) {
  return ref.watch(tripApiProvider).getDetails(tripId);
});

/// My Trips' four tabs — `tab` is one of `upcoming`/`past`/`created`/`saved`.
final myTripsProvider = FutureProvider.autoDispose.family<List<TripSummary>, String>((ref, tab) {
  return ref.watch(tripApiProvider).myTrips(tab);
});

/// My Profile's "Travel stats" card.
final travelStatsProvider = FutureProvider.autoDispose<TravelStats>((ref) {
  return ref.watch(tripApiProvider).travelStats();
});
