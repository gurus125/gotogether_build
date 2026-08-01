import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/chat_api.dart';
import '../data/chat_models.dart';

final chatApiProvider = Provider<ChatApi>((ref) => ChatApi(ref.watch(apiClientProvider)));

/// Chat List (Chapter 1 Section 18) — Trip Groups, most-recent-activity-first
/// (backend `ChatService.listChatRooms` already sorts).
final chatRoomsProvider = FutureProvider.autoDispose<List<ChatRoomSummary>>((ref) async {
  final page = await ref.watch(chatApiProvider).chatRooms();
  return page.items;
});

/// Resolves a trip's chat room from the Chat List — there's no dedicated
/// "get chat room by trip id" endpoint (API Spec Section 10 only lists `GET
/// /users/me/chat-rooms`), so "Open chat" entry points elsewhere (My Trips,
/// Trip Details) look it up this way. Acceptable at MVP list sizes — the same
/// "good enough for now" call as backend `TripService.explore`'s in-memory
/// filter.
final tripChatRoomProvider = FutureProvider.autoDispose.family<ChatRoomSummary?, String>((ref, tripId) async {
  final rooms = await ref.watch(chatRoomsProvider.future);
  for (final room in rooms) {
    if (room.tripId == tripId) return room;
  }
  return null;
});

/// Most recent page of a Trip Chat's messages, newest-first (matches the
/// backend's ordering) — "load older" pagination isn't wired into the UI
/// this pass, just the latest window.
final chatMessagesProvider = FutureProvider.autoDispose.family<List<ChatMessage>, String>((ref, chatRoomId) async {
  final page = await ref.watch(chatApiProvider).messages(chatRoomId);
  return page.items;
});
