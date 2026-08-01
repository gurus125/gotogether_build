import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:image_picker/image_picker.dart';

import 'presigned_upload_url.dart';

/// Shared "get bytes from somewhere, then upload them to a presigned URL"
/// flow used by profile-photo (Edit Profile), trip-photo (Manage Trip
/// Photos) gallery upload, and — since [uploadBytes] was split out —
/// trip-photo stock-photo search too (`PhotoSearchScreen`, which downloads a
/// chosen Pexels result's bytes itself, then calls the exact same upload
/// path a gallery pick uses). The only thing that ever differed between call
/// sites was which backend endpoint hands back the [PresignedUploadUrl] in
/// the first place, so that part is injected rather than hardcoded here.
class ImageUploadService {
  ImageUploadService([ImagePicker? picker]) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;

  /// Returns the final public URL to persist (`photo_url`, `image_url`), or
  /// `null` if the user backed out of the picker.
  Future<String?> pickAndUpload({
    required Future<PresignedUploadUrl> Function(String contentType) requestUploadUrl,
    ImageSource source = ImageSource.gallery,
  }) async {
    final picked = await _picker.pickImage(source: source, maxWidth: 1600, imageQuality: 85);
    if (picked == null) return null;

    final contentType = _contentTypeFor(picked.path);
    final bytes = await picked.readAsBytes();
    return uploadBytes(bytes: bytes, contentType: contentType, requestUploadUrl: requestUploadUrl);
  }

  /// The upload half of [pickAndUpload], split out so a caller that already
  /// has bytes from somewhere other than the device picker — a downloaded
  /// stock-photo search result, for instance — can reuse the exact same
  /// presigned-upload mechanics instead of re-implementing them.
  Future<String> uploadBytes({
    required Uint8List bytes,
    required String contentType,
    required Future<PresignedUploadUrl> Function(String contentType) requestUploadUrl,
  }) async {
    final presigned = await requestUploadUrl(contentType);

    // A bare Dio, not the app's shared ApiClient — the presigned URL points
    // at the storage bucket (MinIO/S3), a different host entirely from the
    // GoTogether backend, and must NOT carry this app's Authorization/JWT
    // header. The presigned URL's own query-string signature is the only
    // auth this request needs or should have.
    await Dio().put<void>(
      presigned.uploadUrl,
      data: bytes,
      options: Options(headers: {'Content-Type': contentType}, contentType: contentType),
    );

    return presigned.publicUrl;
  }

  /// Matches `StorageService.ALLOWED_IMAGE_CONTENT_TYPES` on the backend
  /// (jpeg/png/webp only) — anything else is rejected server-side with a 422
  /// before a presigned URL is even issued, so this only needs to guess well
  /// enough for the common cases `image_picker` actually returns.
  String _contentTypeFor(String path) {
    final lower = path.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'image/jpeg';
  }
}
