import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import 'auth_models.dart';

/// Raw HTTP calls for the Auth module (API Specification: `POST /auth/google`,
/// `POST /auth/phone/otp/request`, `POST /auth/phone/otp/verify`,
/// `POST /auth/refresh`, `POST /auth/logout`). Deliberately thin — no token
/// persistence or app state here, that's `AuthRepository`'s job.
class AuthApi {
  AuthApi(this._apiClient);

  final ApiClient _apiClient;

  // Request bodies use snake_case keys (spring.jackson.property-naming-strategy:
  // SNAKE_CASE, added during the Phase 2 docs review 2026-07-22) — matches
  // each backend request DTO's field name (e.g. GoogleSignInRequest.idToken
  // -> id_token).
  Future<AuthResponse> signInWithGoogle(String idToken) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/auth/google',
          data: {'id_token': idToken},
        ));
    return AuthResponse.fromJson(response.data!);
  }

  Future<void> requestPhoneOtp(String phoneNumber) {
    return _run(() => _apiClient.dio.post<void>(
          '/auth/phone/otp/request',
          data: {'phone_number': phoneNumber},
        ));
  }

  Future<AuthResponse> verifyPhoneOtp(String phoneNumber, String code) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/auth/phone/otp/verify',
          data: {'phone_number': phoneNumber, 'code': code},
        ));
    return AuthResponse.fromJson(response.data!);
  }

  Future<void> logout(String refreshToken) {
    return _run(() => _apiClient.dio.post<void>(
          '/auth/logout',
          data: {'refresh_token': refreshToken},
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
