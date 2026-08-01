import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/user_api.dart';
import '../data/user_models.dart';

final userApiProvider = Provider<UserApi>((ref) => UserApi(ref.watch(apiClientProvider)));

/// The signed-in user's own account record (`GET /users/me`). `autoDispose`
/// so a stale value isn't kept around across sign-out/sign-in cycles;
/// screens that need it call `ref.watch` or `ref.refresh` as usual.
final currentUserProvider = FutureProvider.autoDispose<UserResponse>((ref) {
  return ref.watch(userApiProvider).getMe();
});
