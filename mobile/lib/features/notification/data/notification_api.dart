import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import 'notification_models.dart';

/// Notification APIs (API Spec Section 13).
class NotificationApi {
  NotificationApi(this._apiClient);

  final ApiClient _apiClient;

  /// `filter` is one of `all` / `trips` / `chats` / `trust` (Notifications
  /// Screen mockup's chips — the `trips` chip absorbs join-request and
  /// reminder types too, matching backend `NotificationService.FILTER_TYPES`).
  Future<NotificationsPage> list({String filter = 'all', String? cursor, int limit = 20}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/notifications',
          queryParameters: {'filter': filter, if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return NotificationsPage.fromJson(response.data!);
  }

  Future<void> markRead(String notificationId) {
    return _run(() => _apiClient.dio.post<void>('/notifications/$notificationId/read'));
  }

  /// Swipe-to-archive (Notifications Screen mockup's "full left swipe archives").
  Future<void> archive(String notificationId) {
    return _run(() => _apiClient.dio.post<void>('/notifications/$notificationId/archive'));
  }

  Future<void> markAllRead() {
    return _run(() => _apiClient.dio.post<void>('/notifications/read-all'));
  }

  Future<NotificationPreferences> getPreferences() async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/users/me/notification-preferences'));
    return NotificationPreferences.fromJson(response.data!);
  }

  Future<NotificationPreferences> updatePreferences({
    bool? pushEnabled,
    bool? inAppEnabled,
    bool? emailEnabled,
    bool? marketingEnabled,
    bool? remindersEnabled,
  }) async {
    final response = await _run(() => _apiClient.dio.patch<Map<String, dynamic>>(
          '/users/me/notification-preferences',
          data: {
            'push_enabled': pushEnabled,
            'in_app_enabled': inAppEnabled,
            'email_enabled': emailEnabled,
            'marketing_enabled': marketingEnabled,
            'reminders_enabled': remindersEnabled,
          },
        ));
    return NotificationPreferences.fromJson(response.data!);
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
