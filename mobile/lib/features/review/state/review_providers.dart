import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/review_api.dart';
import '../data/review_models.dart';

final reviewApiProvider = Provider<ReviewApi>((ref) => ReviewApi(ref.watch(apiClientProvider)));

/// Published reviews for a user's Trust+Reviews card (other traveller) —
/// most-recent-first (backend already sorts by `publishedAt desc`).
final publishedReviewsProvider = FutureProvider.autoDispose.family<List<ReviewResponse>, String>((ref, userId) async {
  final page = await ref.watch(reviewApiProvider).published(userId);
  return page.items;
});
