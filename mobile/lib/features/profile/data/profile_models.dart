/// Mirrors `com.gotogether.profile.dto.ProfileResponse` (backs
/// `GET /profile/me`) — display/preference data, kept separate from
/// `UserResponse` per the backend's user/profile module split.
class ProfileResponse {
  const ProfileResponse({
    required this.userId,
    required this.displayName,
    this.photoUrl,
    this.bio,
    this.city,
    this.dateOfBirth,
    required this.languages,
    this.travelStyle,
    this.foodPreference,
    this.smokingPreference,
    this.drinkingPreference,
    this.preferredBudgetStyle,
    this.adventureLevel,
    this.emergencyContactName,
    this.emergencyContactPhone,
  });

  // Backend serializes snake_case (spring.jackson.property-naming-strategy:
  // SNAKE_CASE, added during the Phase 2 docs review 2026-07-22) — this
  // factory and UpdateProfileRequest.toJson() below were updated together.
  factory ProfileResponse.fromJson(Map<String, dynamic> json) => ProfileResponse(
        userId: json['user_id'] as String,
        displayName: json['display_name'] as String,
        photoUrl: json['photo_url'] as String?,
        bio: json['bio'] as String?,
        city: json['city'] as String?,
        dateOfBirth: json['date_of_birth'] as String?,
        languages: (json['languages'] as List<dynamic>? ?? const []).cast<String>(),
        travelStyle: json['travel_style'] as String?,
        foodPreference: json['food_preference'] as String?,
        smokingPreference: json['smoking_preference'] as String?,
        drinkingPreference: json['drinking_preference'] as String?,
        preferredBudgetStyle: json['preferred_budget_style'] as String?,
        adventureLevel: json['adventure_level'] as int?,
        emergencyContactName: json['emergency_contact_name'] as String?,
        emergencyContactPhone: json['emergency_contact_phone'] as String?,
      );

  final String userId;
  final String displayName;
  final String? photoUrl;
  final String? bio;
  final String? city;
  final String? dateOfBirth; // ISO-8601 date (LocalDate), kept as a string — no date picker in this screen yet.
  final List<String> languages;
  final String? travelStyle;
  final String? foodPreference;
  final String? smokingPreference;
  final String? drinkingPreference;
  final String? preferredBudgetStyle;
  final int? adventureLevel;
  final String? emergencyContactName;
  final String? emergencyContactPhone;
}

/// Mirrors `com.gotogether.profile.dto.UpdateProfileRequest` — PATCH
/// semantics, only non-null fields are sent/applied. Every field the
/// approved "Edit Profile" screen exposes is included here; `photoUrl` was
/// added once photo upload was actually wired (see `ImageUploadService`) —
/// previously excluded here since nothing produced a value for it. `city`/
/// `dateOfBirth` still aren't surfaced by this screen, left untouched.
class UpdateProfileRequest {
  const UpdateProfileRequest({
    this.displayName,
    this.photoUrl,
    this.bio,
    this.languages,
    this.travelStyle,
    this.foodPreference,
    this.smokingPreference,
    this.drinkingPreference,
    this.preferredBudgetStyle,
    this.emergencyContactName,
    this.emergencyContactPhone,
  });

  final String? displayName;
  final String? photoUrl;
  final String? bio;
  final List<String>? languages;
  final String? travelStyle;
  final String? foodPreference;
  final String? smokingPreference;
  final String? drinkingPreference;
  final String? preferredBudgetStyle;
  final String? emergencyContactName;
  final String? emergencyContactPhone;

  Map<String, dynamic> toJson() => {
        if (displayName != null) 'display_name': displayName,
        if (photoUrl != null) 'photo_url': photoUrl,
        if (bio != null) 'bio': bio,
        if (languages != null) 'languages': languages,
        if (travelStyle != null) 'travel_style': travelStyle,
        if (foodPreference != null) 'food_preference': foodPreference,
        if (smokingPreference != null) 'smoking_preference': smokingPreference,
        if (drinkingPreference != null) 'drinking_preference': drinkingPreference,
        if (preferredBudgetStyle != null) 'preferred_budget_style': preferredBudgetStyle,
        if (emergencyContactName != null) 'emergency_contact_name': emergencyContactName,
        if (emergencyContactPhone != null) 'emergency_contact_phone': emergencyContactPhone,
      };
}
