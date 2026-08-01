import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';

/// Report API (API Spec Section 15) — the backend `report` module has full
/// coverage (`POST /reports`, `/reports/emergency`, `/reports/{id}/evidence`)
/// but was never wired to any Flutter screen until the trip card's "Report
/// trip" action needed it. Only `create` (the plain, non-emergency path) is
/// implemented here — emergency reporting and evidence upload aren't reached
/// from any UI yet, so building their clients now would be speculative.
///
/// `entityType`/`reason` are sent upper-case, matching backend
/// `ReportEntityType`/`ReportReason`'s exact enum constant names (parsed
/// case-insensitively server-side via `.toUpperCase()`, but sent upper-case
/// here for clarity — same convention as `MembershipApi.markAttendance`'s
/// `attendance_status`).
class ReportApi {
  ReportApi(this._apiClient);

  final ApiClient _apiClient;

  Future<void> create({
    required String entityType,
    required String entityId,
    required String reason,
    String? details,
  }) {
    return _run(() => _apiClient.dio.post<void>('/reports', data: {
          'entity_type': entityType,
          'entity_id': entityId,
          'reason': reason,
          if (details != null) 'details': details,
        }));
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
