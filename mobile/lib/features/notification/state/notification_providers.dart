import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/notification_api.dart';
import '../data/notification_models.dart';

final notificationApiProvider = Provider<NotificationApi>((ref) => NotificationApi(ref.watch(apiClientProvider)));

/// Notifications screen's grouped list for a given filter chip (`all` /
/// `trips` / `chats` / `trust`) — most recent page only, matching every
/// other list screen in this app (no "load more" wired yet at MVP volumes).
final notificationsListProvider = FutureProvider.autoDispose.family<NotificationsPage, String>((ref, filter) async {
  return ref.watch(notificationApiProvider).list(filter: filter);
});

/// Whether the Home Screen bell icon should show an unread dot. There is no
/// dedicated unread-count endpoint (API Spec Section 13 only has the
/// pre-grouped list), so this is a "does the most recent page contain any
/// unread item" check, not a true lifetime unread count — acceptable
/// approximation at MVP volumes, same class of call as `TrustReviewsScreen`'s
/// page-only sub-score averages.
final hasUnreadNotificationsProvider = FutureProvider.autoDispose<bool>((ref) async {
  final page = await ref.watch(notificationsListProvider('all').future);
  return page.groups.any((g) => g.items.any((n) => n.unread));
});

final notificationPreferencesProvider = FutureProvider.autoDispose<NotificationPreferences>((ref) async {
  return ref.watch(notificationApiProvider).getPreferences();
});
