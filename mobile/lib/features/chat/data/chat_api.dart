import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import 'chat_models.dart';

/// Chat APIs (API Spec Section 10) — core Trip Chat only; see backend
/// `ChatService`'s class doc for what's deferred (Direct Messages,
/// attachments, Polls/Expense, mentions, message editing).
class ChatApi {
  ChatApi(this._apiClient);

  final ApiClient _apiClient;

  Future<CursorPage<ChatRoomSummary>> chatRooms({String? cursor, int limit = 50}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/users/me/chat-rooms',
          queryParameters: {if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, ChatRoomSummary.fromJson);
  }

  /// `beforeSequence` paginates strictly older messages (DB Schema Part 2's
  /// per-room sequence number) — not built into a "load older" UI yet this
  /// pass, just the most recent page.
  Future<CursorPage<ChatMessage>> messages(String chatRoomId, {int? beforeSequence, int limit = 50}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/chat-rooms/$chatRoomId/messages',
          queryParameters: {if (beforeSequence != null) 'beforeSequence': beforeSequence, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, ChatMessage.fromJson);
  }

  Future<ChatMessage> send(String chatRoomId, String body, {String? replyToMessageId}) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/chat-rooms/$chatRoomId/messages',
          data: {'type': 'TEXT', 'body': body, 'reply_to_message_id': replyToMessageId},
        ));
    return ChatMessage.fromJson(response.data!);
  }

  /// `pinCategory: null` clears the pin. Organizer-only server-side.
  Future<ChatMessage> pin(String messageId, String? pinCategory) async {
    final response = await _run(() => _apiClient.dio.patch<Map<String, dynamic>>(
          '/messages/$messageId/pin',
          data: {'pin_category': pinCategory},
        ));
    return ChatMessage.fromJson(response.data!);
  }

  Future<void> delete(String messageId) {
    return _run(() => _apiClient.dio.delete<void>('/messages/$messageId'));
  }

  Future<void> markRead(String chatRoomId, String upToMessageId) {
    return _run(() => _apiClient.dio.post<void>('/chat-rooms/$chatRoomId/read', data: {'up_to_message_id': upToMessageId}));
  }

  Future<void> setMuted(String chatRoomId, bool isMuted) {
    return _run(() => _apiClient.dio.patch<void>('/chat-rooms/$chatRoomId/mute', data: {'is_muted': isMuted}));
  }

  Future<T> _run<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      final data = e.response?.data;
      final message = data is Map<String, dynamic> ? data['message'] as String? : null;
      throw ApiException(message ?? 'Something went wrong. Please try again.', statusCode: e.response?.statusCode);
    }
  }
}
