import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import 'membership_models.dart';

/// Membership APIs (API Spec Section 9) plus the roster read (Section 6's
/// `GET /trips/{id}/members`).
class MembershipApi {
  MembershipApi(this._apiClient);

  final ApiClient _apiClient;

  Future<List<RosterMember>> roster(String tripId) async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>('/trips/$tripId/members'));
    return (response.data ?? const []).map((e) => RosterMember.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> leave(String tripId) {
    return _run(() => _apiClient.dio.post<void>('/trips/$tripId/leave'));
  }

  Future<void> removeMember(String tripId, String userId, String reason) {
    return _run(() => _apiClient.dio.post<void>('/trips/$tripId/members/$userId/remove', data: {'reason': reason}));
  }

  /// [attendanceStatus] must be `'ATTENDED'` or `'NO_SHOW'` — the backend
  /// deserializes it as a Java enum by exact constant name (case-sensitive),
  /// not the app's usual snake_case convention, since it's an enum value,
  /// not a field name. Organizer-only, and only once the trip is Completed
  /// (both enforced server-side in `MembershipService.markAttendance`).
  Future<void> markAttendance(String tripId, String userId, String attendanceStatus) {
    return _run(() => _apiClient.dio.patch<void>(
          '/trips/$tripId/members/$userId/attendance',
          data: {'attendance_status': attendanceStatus},
        ));
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
