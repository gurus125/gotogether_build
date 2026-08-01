/// Mirrors `com.gotogether.auth.dto.AuthResponse` (API Specification: Auth
/// module) — every sign-in path (Google, Phone OTP) and `/auth/refresh`
/// return this same shape.
class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.userId,
    required this.newUser,
  });

  // Backend serializes snake_case (spring.jackson.property-naming-strategy:
  // SNAKE_CASE, added during the Phase 2 docs review 2026-07-22 to match the
  // API Specification — this factory was updated in the same pass.
  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['access_token'] as String,
        refreshToken: json['refresh_token'] as String,
        userId: json['user_id'] as String,
        newUser: json['new_user'] as bool,
      );

  final String accessToken;
  final String refreshToken;
  final String userId;
  final bool newUser;
}

/// A backend `ApiErrorResponse` surfaced as a typed exception so screens can
/// show the server's own message instead of a generic "something went
/// wrong".
class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}
