import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import 'trust_models.dart';

/// Trust APIs (API Specification Section 12) plus the self-view variant
/// from Section 4 (`GET /users/me/trust-score`).
class TrustApi {
  TrustApi(this._apiClient);

  final ApiClient _apiClient;

  Future<TrustScoreResponse> mine() async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/users/me/trust-score'));
    return TrustScoreResponse.fromJson(response.data!);
  }

  Future<TrustScoreResponse> breakdown(String userId) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/users/$userId/trust-score'));
    return TrustScoreResponse.fromJson(response.data!);
  }

  Future<CursorPage<TrustScoreHistoryEntry>> history({String? cursor, int limit = 20}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/users/me/trust-score/history',
          queryParameters: {if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, TrustScoreHistoryEntry.fromJson);
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
