import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../../trip/data/trip_models.dart' show CursorPage;
import '../data/trust_api.dart';
import '../data/trust_models.dart';

final trustApiProvider = Provider<TrustApi>((ref) => TrustApi(ref.watch(apiClientProvider)));

/// My Profile's single-bar Trust Score card + improvement tips.
final myTrustScoreProvider = FutureProvider.autoDispose<TrustScoreResponse>((ref) {
  return ref.watch(trustApiProvider).mine();
});

/// Other traveller's Trust+Reviews screen (public breakdown — `improvementTips` always empty here).
final trustBreakdownProvider = FutureProvider.autoDispose.family<TrustScoreResponse, String>((ref, userId) {
  return ref.watch(trustApiProvider).breakdown(userId);
});

/// "Recent activity" on the Trust Score breakdown screen — `TrustApi.history`
/// was built (Phase 5) but never actually wired to a provider/screen until
/// now. First page only (default limit 20); no pagination UI yet since this
/// screen shows it as a compact recent-activity log, not a full history browser.
final trustHistoryProvider = FutureProvider.autoDispose<CursorPage<TrustScoreHistoryEntry>>((ref) {
  return ref.watch(trustApiProvider).history();
});
