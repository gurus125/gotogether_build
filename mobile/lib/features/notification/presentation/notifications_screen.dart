import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../data/notification_models.dart';
import '../state/notification_providers.dart';

/// The approved Notifications Screen (`GoTogether Notifications Screen.dc.html`):
/// filter chips (All/Trips/Chats/Trust) narrowing within a fixed
/// Today/Earlier this week/Older time structure (a section header only
/// renders when it has items — the backend already omits empty groups),
/// swipe-left-to-archive per row, and a single blue dot for unread (never
/// bold-vs-not-bold text).
///
/// Tap-to-open routes every notification type that actually carries enough
/// information to resolve a real screen — which, as of this pass, is all of
/// them that are ever really created (see backend `NotificationService`'s
/// class doc for the handful of types with no trigger wired yet —
/// `DEPARTURE_REMINDER`, `VERIFICATION_DECISION`, `ANNOUNCEMENT`,
/// `CHAT_MENTION` — those fall through to the generic case below harmlessly
/// since none are ever actually created today):
/// - `TRUST_UPDATE` always means the recipient's own Trust Score just
///   changed, regardless of `entityType` — opens their own Profile.
/// - `REVIEW_REMINDER` carries `entityType=trips`/`entityId=<tripId>` — opens
///   that trip's "who can I review" screen directly.
/// - `ATTENDANCE_REMINDER` (organizer-only, sent when `TripLifecycleScheduler`
///   completes a trip) opens `AttendanceScreen` directly, same pattern as
///   `REVIEW_REMINDER`.
/// - `JOIN_REQUEST_RECEIVED` (organizer-only) opens that trip's Manage
///   Requests queue directly — backend `JoinRequestController.create` now
///   points `entityType`/`entityId` at the trip instead of the join
///   request's own id (which had no detail screen to link to anyway).
/// - `CHAT_MESSAGE`/`CHAT_MENTION` open that trip's Chat directly — backend
///   `ChatController.notifyOtherParticipants` now points `entityType`/
///   `entityId` at the trip (via `ChatService.getTripId`) instead of the
///   message's own id, since `TripChatScreen` opens by trip id and resolves
///   its own room from there.
/// - Every other `entityType=trips` notification (`TRIP_UPDATE`,
///   `JOIN_REQUEST_ACCEPTED`, `JOIN_REQUEST_REJECTED`) opens that Trip's
///   details — the generic fallback.
class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  String _filter = 'all';

  static const _filters = [('all', 'All'), ('trips', 'Trips'), ('chats', 'Chats'), ('trust', 'Trust')];

  @override
  Widget build(BuildContext context) {
    final pageAsync = ref.watch(notificationsListProvider(_filter));

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            _Header(
              onClearAll: () async {
                await ref.read(notificationApiProvider).markAllRead();
                ref.invalidate(notificationsListProvider(_filter));
                ref.invalidate(hasUnreadNotificationsProvider);
              },
            ),
            _FilterChips(
              filters: _filters,
              selected: _filter,
              onSelect: (key) => setState(() => _filter = key),
            ),
            Expanded(
              child: pageAsync.when(
                loading: () => const Center(child: CircularProgressIndicator(strokeWidth: 2)),
                error: (e, _) => Center(
                  child: Text('Could not load notifications.', style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
                ),
                data: (page) {
                  if (page.groups.isEmpty) {
                    return const _EmptyState();
                  }
                  return RefreshIndicator(
                    onRefresh: () async => ref.invalidate(notificationsListProvider(_filter)),
                    child: ListView(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                      children: [
                        for (final group in page.groups) ...[
                          _SectionHeader(label: group.label),
                          for (final item in group.items)
                            _NotificationRow(
                              item: item,
                              onArchive: () async {
                                await ref.read(notificationApiProvider).archive(item.id);
                                ref.invalidate(notificationsListProvider(_filter));
                                ref.invalidate(hasUnreadNotificationsProvider);
                              },
                              onTap: () => _handleTap(context, ref, item),
                            ),
                        ],
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleTap(BuildContext context, WidgetRef ref, NotificationItem item) async {
    if (item.unread) {
      await ref.read(notificationApiProvider).markRead(item.id);
      ref.invalidate(notificationsListProvider(_filter));
      ref.invalidate(hasUnreadNotificationsProvider);
    }
    if (!context.mounted) return;

    if (item.type == 'TRUST_UPDATE') {
      context.push('/profile');
      return;
    }
    if (item.type == 'REVIEW_REMINDER' && item.entityType == 'trips' && item.entityId != null) {
      context.push('/trip/${item.entityId}/reviewees');
      return;
    }
    if (item.type == 'ATTENDANCE_REMINDER' && item.entityType == 'trips' && item.entityId != null) {
      context.push('/trip/${item.entityId}/attendance');
      return;
    }
    if (item.type == 'JOIN_REQUEST_RECEIVED' && item.entityType == 'trips' && item.entityId != null) {
      context.push('/trip/${item.entityId}/requests');
      return;
    }
    if ((item.type == 'CHAT_MESSAGE' || item.type == 'CHAT_MENTION') && item.entityType == 'trips' && item.entityId != null) {
      context.push('/trip/${item.entityId}/chat');
      return;
    }
    if (item.entityType == 'trips' && item.entityId != null) {
      context.push('/trip/${item.entityId}');
      return;
    }
    // ANNOUNCEMENT and any type predating this fix with a stale entityType
    // (e.g. an already-delivered notification row from before this change)
    // just mark read — no resolvable deep link.
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.onClearAll});

  final VoidCallback onClearAll;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
      decoration: const BoxDecoration(color: AppColors.surface, border: Border(bottom: BorderSide(color: AppColors.border))),
      child: Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            IconButton(
              onPressed: () => context.pop(),
              icon: const Icon(Icons.arrow_back, size: 20, color: AppColors.textPrimary),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            ),
            const Text('Notifications', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            TextButton(
              onPressed: onClearAll,
              style: TextButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 8), minimumSize: Size.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
              child: const Text('Clear all', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.primary)),
            ),
          ],
        ),
      ),
    );
  }
}

class _FilterChips extends StatelessWidget {
  const _FilterChips({required this.filters, required this.selected, required this.onSelect});

  final List<(String, String)> filters;
  final String selected;
  final void Function(String) onSelect;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.surface,
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final (key, label) in filters) ...[
              _Chip(label: label, active: key == selected, onTap: () => onSelect(key)),
              const SizedBox(width: 6),
            ],
          ],
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.active, required this.onTap});

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: active ? AppColors.textPrimary : Colors.transparent,
          border: active ? null : Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(100),
        ),
        child: Text(
          label,
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: active ? Colors.white : AppColors.textSecondary),
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.label});

  final String label;

  static const _display = {'TODAY': 'TODAY', 'EARLIER_THIS_WEEK': 'EARLIER THIS WEEK', 'OLDER': 'OLDER'};

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 12, 4, 8),
      child: Text(
        _display[label] ?? label,
        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.5, color: AppColors.textSecondary),
      ),
    );
  }
}

class _NotificationRow extends StatelessWidget {
  const _NotificationRow({required this.item, required this.onArchive, required this.onTap});

  final NotificationItem item;
  final VoidCallback onArchive;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (icon, iconBg) = _iconFor(item.type);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Dismissible(
        key: ValueKey(item.id),
        direction: DismissDirection.endToStart,
        onDismissed: (_) => onArchive(),
        background: Container(
          alignment: Alignment.centerRight,
          padding: const EdgeInsets.only(right: 20),
          decoration: BoxDecoration(color: AppColors.border, borderRadius: BorderRadius.circular(14)),
          child: const Text('Archive', style: TextStyle(fontSize: 10.5, color: AppColors.textSecondary, fontWeight: FontWeight.w500)),
        ),
        child: GestureDetector(
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.all(11),
            decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
                  alignment: Alignment.center,
                  child: Text(icon, style: const TextStyle(fontSize: 15)),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500)),
                      const SizedBox(height: 2),
                      Text(item.body, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(_relativeTime(item.createdAt), style: const TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    const SizedBox(height: 6),
                    if (item.unread)
                      Container(width: 8, height: 8, decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle)),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Category → (emoji, background tint), transcribed from the mockup's
  /// per-category `iconBg` values (see `AppColors`' own OKLCH-approximation
  /// disclaimer — these reuse that same approximated palette).
  static (String, Color) _iconFor(String type) {
    return switch (type) {
      'JOIN_REQUEST_RECEIVED' => ('🙋', AppColors.primaryTint),
      'JOIN_REQUEST_ACCEPTED' => ('✅', AppColors.successTint),
      'JOIN_REQUEST_REJECTED' => ('🙅', AppColors.errorTint),
      'CHAT_MESSAGE' => ('💬', AppColors.communityTint),
      'CHAT_MENTION' => ('💬', AppColors.communityTint),
      'TRIP_UPDATE' => ('🗓️', AppColors.successTint),
      'DEPARTURE_REMINDER' => ('⏰', AppColors.background),
      'REVIEW_REMINDER' => ('📝', AppColors.background),
      'ATTENDANCE_REMINDER' => ('✅', AppColors.background),
      'VERIFICATION_DECISION' => ('🛡️', AppColors.accentTint),
      'TRUST_UPDATE' => ('⭐', AppColors.accentTint),
      'ANNOUNCEMENT' => ('📢', AppColors.background),
      _ => ('🔔', AppColors.background),
    };
  }

  /// The backend sends an absolute ISO timestamp, not the mockup's
  /// pre-formatted "9:40 AM" / "Mon" / "Last week" strings — approximated
  /// here as a plain relative label since there's no shared date-formatting
  /// utility elsewhere in this app to match against.
  static String _relativeTime(String createdAt) {
    final parsed = DateTime.tryParse(createdAt);
    if (parsed == null) return '';
    final diff = DateTime.now().toUtc().difference(parsed.toUtc());
    if (diff.inMinutes < 1) return 'Now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m';
    if (diff.inHours < 24) return '${diff.inHours}h';
    if (diff.inDays < 7) return '${diff.inDays}d';
    return '${(diff.inDays / 7).floor()}w';
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: AppColors.communityTint, width: 2)),
              child: const Icon(Icons.notifications_none_rounded, color: AppColors.communityText, size: 22),
            ),
            const SizedBox(height: 16),
            const Text('No notifications yet', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
            const SizedBox(height: 6),
            const Text(
              "You'll see trip updates, messages and trust changes here.",
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.6),
            ),
          ],
        ),
      ),
    );
  }
}
