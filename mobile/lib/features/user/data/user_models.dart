/// Mirrors `com.gotogether.user.dto.UserResponse` (backs `GET /users/me`) —
/// auth-identity/account-lifecycle fields only. Display data (name, photo,
/// bio, ...) lives in `ProfileResponse` instead, matching the backend's
/// user/profile module split.
class UserResponse {
  const UserResponse({
    required this.id,
    required this.phoneNumber,
    required this.email,
    required this.status,
    required this.verificationLevel,
    required this.role,
  });

  // Backend serializes snake_case (spring.jackson.property-naming-strategy:
  // SNAKE_CASE, added during the Phase 2 docs review 2026-07-22) — enum
  // *values* (status/verificationLevel/role) are unaffected and still
  // serialize as the Java constant name (e.g. "ID_APPROVED").
  factory UserResponse.fromJson(Map<String, dynamic> json) => UserResponse(
        id: json['id'] as String,
        phoneNumber: json['phone_number'] as String?,
        email: json['email'] as String?,
        status: json['status'] as String,
        verificationLevel: json['verification_level'] as String,
        role: json['role'] as String,
      );

  final String id;
  final String? phoneNumber;
  final String? email;

  /// One of `REGISTERED` / `VERIFIED` / `RESTRICTED` / `SUSPENDED` (see
  /// `UserStatus` — Jackson serializes the Java enum name as-is, uppercase;
  /// only the JPA column value is lowercased for the native Postgres enum).
  final String status;

  /// One of `NONE` / `PHONE` / `EMAIL` / `ID_APPROVED` (see `VerificationLevel`).
  final String verificationLevel;

  /// One of `INDIVIDUAL` / `MODERATOR` / `ADMIN` (see `AccountRole`).
  final String role;
}
