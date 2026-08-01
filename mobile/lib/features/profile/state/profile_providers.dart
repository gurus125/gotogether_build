import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/profile_api.dart';
import '../data/profile_models.dart';

final profileApiProvider = Provider<ProfileApi>((ref) => ProfileApi(ref.watch(apiClientProvider)));

/// The signed-in user's own profile (`GET /profile/me`). `autoDispose` for
/// the same reason as `currentUserProvider` — no stale data across sessions.
final profileProvider = FutureProvider.autoDispose<ProfileResponse>((ref) {
  return ref.watch(profileApiProvider).getMyProfile();
});
