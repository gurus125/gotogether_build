/// Mirrors the backend's `storage.dto.PresignedUploadResponse` — returned by
/// every "step 1" upload-url endpoint (`POST /profile/me/photo/upload-url`,
/// `POST /trips/{id}/images/upload-url`).
class PresignedUploadUrl {
  const PresignedUploadUrl({required this.uploadUrl, required this.publicUrl, required this.key});

  /// Presigned S3 PUT URL — upload the raw image bytes here directly.
  final String uploadUrl;

  /// The URL to persist afterwards (`photo_url`, `image_url`) once the
  /// upload succeeds.
  final String publicUrl;

  final String key;

  factory PresignedUploadUrl.fromJson(Map<String, dynamic> json) => PresignedUploadUrl(
        uploadUrl: json['upload_url'] as String,
        publicUrl: json['public_url'] as String,
        key: json['key'] as String,
      );
}
