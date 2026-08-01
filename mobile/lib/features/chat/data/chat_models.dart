/// Mirrors `chat.dto.ChatRoomSummary` (API Spec Section 10) — one row of the
/// Chat List (Chapter 1 Section 18: Trip Groups above Direct Messages). Only
/// Trip Groups exist this pass — no Direct Messages yet (see backend
/// `ChatService`'s class doc).
class ChatRoomSummary {
  const ChatRoomSummary({
    required this.chatRoomId,
    required this.tripId,
    required this.tripTitle,
    required this.tripKind,
    required this.tripStatus,
    this.tripCoverImageUrl,
    required this.memberCount,
    this.lastMessagePreview,
    this.lastMessageSenderName,
    this.lastMessageAt,
    required this.unreadCount,
    required this.isMuted,
    required this.isArchived,
  });

  factory ChatRoomSummary.fromJson(Map<String, dynamic> json) => ChatRoomSummary(
        chatRoomId: json['chat_room_id'] as String,
        tripId: json['trip_id'] as String,
        tripTitle: json['trip_title'] as String,
        tripKind: json['trip_kind'] as String,
        tripStatus: json['trip_status'] as String,
        tripCoverImageUrl: json['trip_cover_image_url'] as String?,
        memberCount: json['member_count'] as int,
        lastMessagePreview: json['last_message_preview'] as String?,
        lastMessageSenderName: json['last_message_sender_name'] as String?,
        lastMessageAt: json['last_message_at'] as String?,
        unreadCount: json['unread_count'] as int,
        isMuted: json['is_muted'] as bool,
        isArchived: json['is_archived'] as bool,
      );

  final String chatRoomId;
  final String tripId;
  final String tripTitle;

  /// `COMMUNITY` or `VERIFIED_PARTNER`.
  final String tripKind;
  final String tripStatus;
  final String? tripCoverImageUrl;
  final int memberCount;
  final String? lastMessagePreview;
  final String? lastMessageSenderName;
  final String? lastMessageAt;
  final int unreadCount;
  final bool isMuted;
  final bool isArchived;
}

/// Mirrors `chat.dto.MessageResponse`.
class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.sequenceNumber,
    required this.chatRoomId,
    this.senderId,
    this.senderDisplayName,
    this.senderPhotoUrl,
    required this.type,
    this.body,
    this.replyToMessageId,
    required this.isPinned,
    this.pinCategory,
    required this.isEdited,
    required this.isDeleted,
    required this.createdAt,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
        id: json['id'] as String,
        sequenceNumber: json['sequence_number'] as int,
        chatRoomId: json['chat_room_id'] as String,
        senderId: json['sender_id'] as String?,
        senderDisplayName: json['sender_display_name'] as String?,
        senderPhotoUrl: json['sender_photo_url'] as String?,
        type: json['type'] as String,
        body: json['body'] as String?,
        replyToMessageId: json['reply_to_message_id'] as String?,
        isPinned: json['is_pinned'] as bool,
        pinCategory: json['pin_category'] as String?,
        isEdited: json['is_edited'] as bool,
        isDeleted: json['is_deleted'] as bool,
        createdAt: json['created_at'] as String,
      );

  final String id;
  final int sequenceNumber;
  final String chatRoomId;
  final String? senderId;
  final String? senderDisplayName;
  final String? senderPhotoUrl;

  /// Always `TEXT` this pass — see backend `ChatService`'s class doc for the
  /// other `MessageType` values, all rejected server-side for now.
  final String type;
  final String? body;
  final String? replyToMessageId;
  final bool isPinned;
  final String? pinCategory;
  final bool isEdited;
  final bool isDeleted;
  final String createdAt;
}
