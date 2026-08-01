import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/join_request_api.dart';
import '../data/join_request_models.dart';

final joinRequestApiProvider = Provider<JoinRequestApi>((ref) => JoinRequestApi(ref.watch(apiClientProvider)));

/// Trip Details' CTA state — refetched after any join/withdraw/leave action
/// via `ref.invalidate(joinStatusProvider(tripId))`.
final joinStatusProvider = FutureProvider.autoDispose.family<JoinStatusResponse, String>((ref, tripId) {
  return ref.watch(joinRequestApiProvider).joinStatus(tripId);
});

/// Organizer's request queue for a given trip (the un-mocked "manage
/// requests" screen — see `organizer_requests_screen.dart`'s class doc).
final organizerQueueProvider = FutureProvider.autoDispose.family<List<JoinRequestResponse>, String>((ref, tripId) {
  return ref.watch(joinRequestApiProvider).organizerQueue(tripId);
});
