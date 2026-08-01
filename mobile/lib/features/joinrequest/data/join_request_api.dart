import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import 'join_request_models.dart';

/// Join Request APIs (API Spec Section 8). Real notifications ("new
/// request", "You're in!"), Chat unlock, and Trust Score effects are all
/// deferred on the backend (Phase 4/5/6 modules) — see
/// `JoinRequestService`'s class doc — so nothing here should be read as
/// triggering those side effects yet.
class JoinRequestApi {
  JoinRequestApi(this._apiClient);

  final ApiClient _apiClient;

  Future<JoinRequestResponse> create(String tripId, {String? requestMessage}) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/trips/$tripId/join-requests',
          data: {'request_message': requestMessage},
        ));
    return JoinRequestResponse.fromJson(response.data!);
  }

  Future<JoinRequestResponse> withdraw(String joinRequestId) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>('/join-requests/$joinRequestId/withdraw'));
    return JoinRequestResponse.fromJson(response.data!);
  }

  /// Returns the updated `join_request` only — the Organizer's queue UI only
  /// needs the new status (accepted, or waiting_list if a capacity race was
  /// lost); the admitted member's roster row is refreshed separately via
  /// `MembershipApi.roster`.
  Future<JoinRequestResponse> accept(String joinRequestId) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>('/join-requests/$joinRequestId/accept'));
    return JoinRequestResponse.fromJson(response.data!['join_request'] as Map<String, dynamic>);
  }

  Future<JoinRequestResponse> reject(String joinRequestId, {String? note}) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/join-requests/$joinRequestId/reject',
          data: {'note': note},
        ));
    return JoinRequestResponse.fromJson(response.data!);
  }

  /// Organizer's request queue for a trip, oldest-first.
  Future<List<JoinRequestResponse>> organizerQueue(String tripId, {String? status}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/trips/$tripId/join-requests',
          queryParameters: {if (status != null) 'status': status},
        ));
    return CursorPage.fromJson(response.data!, JoinRequestResponse.fromJson).items;
  }

  Future<JoinStatusResponse> joinStatus(String tripId) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/trips/$tripId/join-status'));
    return JoinStatusResponse.fromJson(response.data!);
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
