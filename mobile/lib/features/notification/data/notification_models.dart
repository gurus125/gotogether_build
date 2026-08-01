/// Mirrors `notification.dto.NotificationResponse` (API Spec Section 13).
class NotificationItem {
  const NotificationItem({
    required this.id,
    this.actorId,
    required this.type,
    required this.entityType,
    this.entityId,
    required this.title,
    required this.body,
    required this.priority,
    required this.status,
    required this.unread,
    required this.createdAt,
  });

  factory NotificationItem.fromJson(Map<String, dynamic> json) => NotificationItem(
        id: json['id'] as String,
        actorId: json['actor_id'] as String?,
        type: json['type'] as String,
        entityType: json['entity_type'] as String?,
        entityId: json['entity_id'] as String?,
        title: json['title'] as String,
        body: json['body'] as String,
        priority: json['priority'] as String,
        status: json['status'] as String,
        unread: json['unread'] as bool,
        createdAt: json['created_at'] as String,
      );

  final String id;
  final String? actorId;

  /// One of the 11 `notification_type` enum values (e.g. `CHAT_MESSAGE`,
  /// `TRUST_UPDATE`) — see backend `NotificationService`'s class doc for
  /// which are actually wired this pass vs. deferred.
  final String type;
  final String? entityType;
  final String? entityId;
  final String title;
  final String body;
  final String priority;
  final String status;
  final bool unread;
  final String createdAt;
}

/// Mirrors `notification.dto.NotificationGroup` — one recency section
/// (`TODAY` / `EARLIER_THIS_WEEK` / `OLDER`), omitted entirely by the backend
/// when it has no items this page.
class NotificationGroup {
  const NotificationGroup({required this.label, required this.items});

  factory NotificationGroup.fromJson(Map<String, dynamic> json) => NotificationGroup(
        label: json['label'] as String,
        items: (json['items'] as List<dynamic>? ?? const [])
            .map((e) => NotificationItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  final String label;
  final List<NotificationItem> items;
}

/// Mirrors `notification.dto.NotificationsPageResponse` — a deliberately
/// custom, pre-grouped envelope (NOT the codebase's usual `CursorPageResponse`)
/// since the API Specification documents `GET /notifications`'s response
/// shape as "Paginated, pre-grouped".
class NotificationsPage {
  const NotificationsPage({required this.groups, this.nextCursor, required this.hasMore});

  factory NotificationsPage.fromJson(Map<String, dynamic> json) => NotificationsPage(
        groups: (json['groups'] as List<dynamic>? ?? const [])
            .map((e) => NotificationGroup.fromJson(e as Map<String, dynamic>))
            .toList(),
        nextCursor: json['next_cursor'] as String?,
        hasMore: json['has_more'] as bool? ?? false,
      );

  final List<NotificationGroup> groups;
  final String? nextCursor;
  final bool hasMore;
}

/// Mirrors `notification.dto.NotificationPreferencesResponse`.
class NotificationPreferences {
  const NotificationPreferences({
    required this.pushEnabled,
    required this.inAppEnabled,
    required this.emailEnabled,
    required this.marketingEnabled,
    required this.remindersEnabled,
  });

  factory NotificationPreferences.fromJson(Map<String, dynamic> json) => NotificationPreferences(
        pushEnabled: json['push_enabled'] as bool,
        inAppEnabled: json['in_app_enabled'] as bool,
        emailEnabled: json['email_enabled'] as bool,
        marketingEnabled: json['marketing_enabled'] as bool,
        remindersEnabled: json['reminders_enabled'] as bool,
      );

  final bool pushEnabled;
  final bool inAppEnabled;
  final bool emailEnabled;
  final bool marketingEnabled;
  final bool remindersEnabled;
}
