import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/membership_api.dart';
import '../data/membership_models.dart';

final membershipApiProvider = Provider<MembershipApi>((ref) => MembershipApi(ref.watch(apiClientProvider)));

final rosterProvider = FutureProvider.autoDispose.family<List<RosterMember>, String>((ref, tripId) {
  return ref.watch(membershipApiProvider).roster(tripId);
});
