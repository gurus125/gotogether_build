import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../../core/upload/presigned_upload_url.dart';
import '../../auth/data/auth_models.dart';
import 'profile_models.dart';

/// Raw HTTP calls for the Profile module (API Specification:
/// `GET/PATCH /profile/me`, plus the newer photo-upload-url endpoint).
class ProfileApi {
  ProfileApi(this._apiClient);

  final ApiClient _apiClient;

  Future<ProfileResponse> getMyProfile() async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/profile/me'));
    return ProfileResponse.fromJson(response.data!);
  }

  Future<ProfileResponse> updateProfile(UpdateProfileRequest request) async {
    final response = await _run(() => _apiClient.dio.patch<Map<String, dynamic>>('/profile/me', data: request.toJson()));
    return ProfileResponse.fromJson(response.data!);
  }

  /// Step 1 of profile-photo upload — see `ImageUploadService`'s class doc
  /// for the full flow. Step 2 is just calling [updateProfile] with
  /// `photoUrl` set to the returned `public_url`.
  Future<PresignedUploadUrl> getPhotoUploadUrl(String contentType) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/profile/me/photo/upload-url',
          queryParameters: {'content_type': contentType},
        ));
    return PresignedUploadUrl.fromJson(response.data!);
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
