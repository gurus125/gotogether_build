import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../trip/state/trip_providers.dart';
import '../../user/state/user_providers.dart';
import '../data/chat_models.dart';
import '../state/chat_providers.dart';

/// Trip Chat (Chapter 1 Section 18, Chapter 3 Section 3.5, Business Rules
/// Module C) — text messages only this pass; organizer-only pin, per-user
/// mute, and self/moderator delete are real. Direct Messages, attachments
/// (image/voice/document/location), Polls/Expense, @mentions, quick actions,
/// and message editing are all deferred — see backend `ChatService`'s class
/// doc for the full list and why.
class TripChatScreen extends ConsumerWidget {
  const TripChatScreen({super.key, required this.tripId});

  final String tripId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final roomAsync = ref.watch(tripChatRoomProvider(tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      body: roomAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Scaffold(
          appBar: AppBar(),
          body: const Center(child: Text('Could not load this chat.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
        ),
        data: (room) {
          if (room == null) {
            return Scaffold(
              appBar: AppBar(),
              body: const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    "This trip's chat isn't available yet.",
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
                  ),
                ),
              ),
            );
          }
          return _ChatRoomBody(tripId: tripId, room: room);
        },
      ),
    );
  }
}

class _ChatRoomBody extends ConsumerStatefulWidget {
  const _ChatRoomBody({required this.tripId, required this.room});

  final String tripId;
  final ChatRoomSummary room;

  @override
  ConsumerState<_ChatRoomBody> createState() => _ChatRoomBodyState();
}

class _ChatRoomBodyState extends ConsumerState<_ChatRoomBody> {
  final _composerController = TextEditingController();
  bool _sending = false;
  bool _hasMarkedRead = false;

  @override
  void dispose() {
    _composerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final messagesAsync = ref.watch(chatMessagesProvider(widget.room.chatRoomId));
    final currentUserAsync = ref.watch(currentUserProvider);
    final tripDetailsAsync = ref.watch(tripDetailsProvider(widget.tripId));
    final isOrganizer = tripDetailsAsync.maybeWhen(
      data: (details) => currentUserAsync.maybeWhen(data: (u) => u.id == details.organizer.id, orElse: () => false),
      orElse: () => false,
    );
    final currentUserId = currentUserAsync.maybeWhen(data: (u) => u.id, orElse: () => null);

    // Fire-and-forget read receipt once messages have loaded for this build.
    // Deliberately does NOT invalidate chatRoomsProvider here: doing so would force
    // tripChatRoomProvider (which this screen watches via chatRoomsProvider.future) back
    // into a loading state, unmounting this entire subtree (including chatMessagesProvider)
    // and resetting _hasMarkedRead to false — causing mark-read to refire the instant
    // messages reload, invalidate again, unmount again... an infinite loop. The Chat List
    // screen doesn't need a push here: chatRoomsProvider is autoDispose and will fetch a
    // fresh, correct unread count the next time that screen actually mounts.
    messagesAsync.whenData((messages) {
      if (!_hasMarkedRead && messages.isNotEmpty) {
        _hasMarkedRead = true;
        Future.microtask(() {
          ref.read(chatApiProvider).markRead(widget.room.chatRoomId, messages.first.id);
        });
      }
    });

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        titleSpacing: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(widget.room.tripTitle, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600), maxLines: 1, overflow: TextOverflow.ellipsis),
            Text('${widget.room.memberCount} members', style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(widget.room.isMuted ? Icons.notifications_off_outlined : Icons.notifications_none, size: 20),
            tooltip: widget.room.isMuted ? 'Unmute' : 'Mute',
            onPressed: () async {
              await ref.read(chatApiProvider).setMuted(widget.room.chatRoomId, !widget.room.isMuted);
              ref.invalidate(tripChatRoomProvider(widget.tripId));
              ref.invalidate(chatRoomsProvider);
            },
          ),
          IconButton(
            icon: const Icon(Icons.people_outline, size: 20),
            tooltip: 'Members',
            onPressed: () => context.push('/trip/${widget.tripId}/members'),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: messagesAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => const Center(child: Text('Could not load messages.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
              data: (messages) {
                final pinned = messages.where((m) => m.isPinned).toList();
                return Column(
                  children: [
                    if (pinned.isNotEmpty) _PinnedBanner(message: pinned.first),
                    Expanded(
                      child: messages.isEmpty
                          ? const Center(
                              child: Padding(
                                padding: EdgeInsets.all(24),
                                child: Text(
                                  "No messages yet — say hello to the group!",
                                  textAlign: TextAlign.center,
                                  style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary),
                                ),
                              ),
                            )
                          : ListView.builder(
                              reverse: true,
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              itemCount: messages.length,
                              itemBuilder: (context, i) {
                                final message = messages[i];
                                final isMine = currentUserId != null && message.senderId == currentUserId;
                                return _MessageBubble(
                                  message: message,
                                  isMine: isMine,
                                  isOrganizer: isOrganizer,
                                  chatRoomId: widget.room.chatRoomId,
                                );
                              },
                            ),
                    ),
                  ],
                );
              },
            ),
          ),
          if (widget.room.isArchived)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: const BoxDecoration(color: AppColors.surface, border: Border(top: BorderSide(color: AppColors.border))),
              child: const Text(
                "This trip's chat is archived — no new messages can be sent.",
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary),
              ),
            )
          else
            _Composer(
              controller: _composerController,
              sending: _sending,
              onSend: () => _send(widget.room.chatRoomId),
            ),
        ],
      ),
    );
  }

  Future<void> _send(String chatRoomId) async {
    final body = _composerController.text.trim();
    if (body.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      await ref.read(chatApiProvider).send(chatRoomId, body);
      _composerController.clear();
      ref.invalidate(chatMessagesProvider(chatRoomId));
      ref.invalidate(chatRoomsProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }
}

class _PinnedBanner extends StatelessWidget {
  const _PinnedBanner({required this.message});

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: const BoxDecoration(color: AppColors.accentTint, border: Border(bottom: BorderSide(color: AppColors.border))),
      child: Row(
        children: [
          const Icon(Icons.push_pin_outlined, size: 14, color: AppColors.accentTextOnTint),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message.body ?? '',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11.5, color: AppColors.accentTextOnTint, fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends ConsumerWidget {
  const _MessageBubble({required this.message, required this.isMine, required this.isOrganizer, required this.chatRoomId});

  final ChatMessage message;
  final bool isMine;
  final bool isOrganizer;
  final String chatRoomId;

  bool get _withinSelfDeleteWindow {
    final created = DateTime.tryParse(message.createdAt);
    if (created == null) return false;
    return DateTime.now().difference(created).inMinutes < 10;
  }

  bool get _canDelete => isMine && _withinSelfDeleteWindow;

  bool get _canPin => isOrganizer;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return GestureDetector(
      onLongPress: (_canDelete || _canPin) ? () => _showActions(context, ref) : null,
      child: Align(
        alignment: isMine ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 4),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.72),
          decoration: BoxDecoration(
            color: isMine ? AppColors.primary : AppColors.surface,
            border: isMine ? null : Border.all(color: AppColors.border),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (!isMine && message.senderDisplayName != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Text(message.senderDisplayName!, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.primary)),
                ),
              Text(
                message.isDeleted ? 'This message was deleted' : (message.body ?? ''),
                style: TextStyle(
                  fontSize: 13,
                  fontStyle: message.isDeleted ? FontStyle.italic : FontStyle.normal,
                  color: message.isDeleted
                      ? (isMine ? Colors.white70 : AppColors.textTertiary)
                      : (isMine ? Colors.white : AppColors.textPrimary),
                ),
              ),
              const SizedBox(height: 3),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (message.isPinned) Padding(
                    padding: const EdgeInsets.only(right: 4),
                    child: Icon(Icons.push_pin, size: 10, color: isMine ? Colors.white70 : AppColors.textTertiary),
                  ),
                  Text(
                    DateFormat('h:mm a').format(DateTime.tryParse(message.createdAt)?.toLocal() ?? DateTime.now()),
                    style: TextStyle(fontSize: 9.5, color: isMine ? Colors.white70 : AppColors.textTertiary),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showActions(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_canPin)
              ListTile(
                leading: Icon(message.isPinned ? Icons.push_pin : Icons.push_pin_outlined),
                title: Text(message.isPinned ? 'Unpin message' : 'Pin message'),
                onTap: () async {
                  Navigator.of(sheetContext).pop();
                  await ref.read(chatApiProvider).pin(message.id, message.isPinned ? null : 'important_update');
                  ref.invalidate(chatMessagesProvider(chatRoomId));
                },
              ),
            if (_canDelete)
              ListTile(
                leading: const Icon(Icons.delete_outline, color: AppColors.error),
                title: const Text('Delete message', style: TextStyle(color: AppColors.error)),
                onTap: () async {
                  Navigator.of(sheetContext).pop();
                  await ref.read(chatApiProvider).delete(message.id);
                  ref.invalidate(chatMessagesProvider(chatRoomId));
                  ref.invalidate(chatRoomsProvider);
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  const _Composer({required this.controller, required this.sending, required this.onSend});

  final TextEditingController controller;
  final bool sending;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.fromLTRB(12, 8, 12, 8 + MediaQuery.of(context).padding.bottom),
      decoration: const BoxDecoration(color: AppColors.surface, border: Border(top: BorderSide(color: AppColors.border))),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              minLines: 1,
              maxLines: 4,
              decoration: InputDecoration(
                hintText: 'Message the group…',
                hintStyle: const TextStyle(fontSize: 13, color: AppColors.textTertiary),
                filled: true,
                fillColor: AppColors.background,
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(100), borderSide: BorderSide.none),
              ),
              style: const TextStyle(fontSize: 13),
              onSubmitted: (_) => onSend(),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: sending ? null : onSend,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
              alignment: Alignment.center,
              child: sending
                  ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.arrow_upward, size: 18, color: Colors.white),
            ),
          ),
        ],
      ),
    );
  }
}
