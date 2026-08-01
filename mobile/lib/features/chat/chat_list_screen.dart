import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_colors.dart';
import 'data/chat_models.dart';
import 'state/chat_providers.dart';

/// The Chat List (Chapter 1 Section 18) — Trip Groups only this pass; no
/// Direct Messages section yet (see backend `ChatService`'s class doc). No
/// search bar — the approved mockup (chatv1.pdf) has one, but with a single
/// flat Trip Groups list at MVP dataset sizes it isn't worth the added
/// surface yet; flagged here rather than silently dropped.
class ChatListScreen extends ConsumerWidget {
  const ChatListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final roomsAsync = ref.watch(chatRoomsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Chats')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(chatRoomsProvider),
        child: roomsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(children: const [
            SizedBox(height: 120),
            Center(child: Text('Could not load chats.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
          ]),
          data: (rooms) {
            if (rooms.isEmpty) {
              return ListView(
                padding: const EdgeInsets.fromLTRB(24, 100, 24, 24),
                children: const [
                  Center(
                    child: Text(
                      "Trip chats unlock once you're accepted onto a trip (or someone joins yours).",
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary, height: 1.6),
                    ),
                  ),
                ],
              );
            }
            return ListView(
              padding: const EdgeInsets.symmetric(vertical: 8),
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
                  child: Text('TRIP GROUPS', style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.textTertiary)),
                ),
                ...rooms.map((room) => _ChatRoomTile(room: room)),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _ChatRoomTile extends StatelessWidget {
  const _ChatRoomTile({required this.room});

  final ChatRoomSummary room;

  @override
  Widget build(BuildContext context) {
    final lastAt = room.lastMessageAt != null ? DateTime.tryParse(room.lastMessageAt!) : null;
    final timeLabel = lastAt != null ? DateFormat('MMM d').format(lastAt) : '';
    final preview = room.lastMessagePreview != null
        ? (room.lastMessageSenderName != null ? '${room.lastMessageSenderName}: ${room.lastMessagePreview}' : room.lastMessagePreview!)
        : 'No messages yet — say hello!';

    return ListTile(
      onTap: () => context.push('/trip/${room.tripId}/chat'),
      leading: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: AppColors.primaryLight,
          borderRadius: BorderRadius.circular(12),
          image: room.tripCoverImageUrl != null ? DecorationImage(image: NetworkImage(room.tripCoverImageUrl!), fit: BoxFit.cover) : null,
        ),
        child: room.tripCoverImageUrl == null ? const Icon(Icons.landscape_outlined, color: AppColors.primary, size: 20) : null,
      ),
      title: Row(
        children: [
          Expanded(child: Text(room.tripTitle, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600), maxLines: 1, overflow: TextOverflow.ellipsis)),
          if (room.tripKind == 'VERIFIED_PARTNER')
            Container(
              margin: const EdgeInsets.only(left: 6),
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(color: AppColors.successTint, borderRadius: BorderRadius.circular(5)),
              child: const Text('VERIFIED', style: TextStyle(fontSize: 8.5, fontWeight: FontWeight.w600, color: AppColors.success)),
            ),
          if (room.isMuted) const Padding(padding: EdgeInsets.only(left: 6), child: Icon(Icons.notifications_off_outlined, size: 13, color: AppColors.textTertiary)),
        ],
      ),
      subtitle: Text(preview, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(timeLabel, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
          const SizedBox(height: 4),
          if (room.unreadCount > 0)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(100)),
              child: Text('${room.unreadCount}', style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: Colors.white)),
            ),
        ],
      ),
    );
  }
}
