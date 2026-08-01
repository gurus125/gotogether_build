import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/destination_api.dart';
import '../data/destination_models.dart';

final destinationApiProvider = Provider<DestinationApi>((ref) => DestinationApi(ref.watch(apiClientProvider)));

/// Full curated list, used by the Create Trip wizard's Destination step
/// (category browse) — cheap enough (16 seed rows) to fetch whole and filter
/// client-side rather than re-querying per category tap.
final allDestinationsProvider = FutureProvider.autoDispose<List<DestinationSummary>>((ref) {
  return ref.watch(destinationApiProvider).list();
});

/// "Popular from Delhi NCR" chips (Create Trip Destination step, Home's
/// destination shortcuts).
final popularDestinationsProvider = FutureProvider.autoDispose<List<DestinationSummary>>((ref) {
  return ref.watch(destinationApiProvider).popular(limit: 5);
});

/// Home Screen's "Popular destinations" 2x2 grid.
final featuredDestinationsProvider = FutureProvider.autoDispose<List<DestinationSummary>>((ref) {
  return ref.watch(destinationApiProvider).featured();
});
