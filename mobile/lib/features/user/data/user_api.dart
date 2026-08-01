import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import 'user_models.dart';

/// Raw HTTP calls for `GET /users/me` (API Specification: User module).
/// Verification submission/listing (`POST/GET /users/me/verifications`)
/// belongs to the Verification module (Phase 8), not wired here.
class UserApi {
  UserApi(this._apiClient);

  final ApiClient _apiClient;

  Future<UserResponse> getMe() async {
    try {
      final response = await _apiClient.dio.get<Map<String, dynamic>>('/users/me');
      return UserResponse.fromJson(response.data!);
    } on DioException catch (e) {
      final data = e.response?.data;
      final message = data is Map<String, dynamic> ? data['message'] as String? : null;
      throw ApiException(message ?? 'Could not load your account.', statusCode: e.response?.statusCode);
    }
  }
}
