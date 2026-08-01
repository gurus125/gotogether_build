import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../upload/image_upload_service.dart';
import 'api_client.dart';
import 'token_storage.dart';

final tokenStorageProvider = Provider<TokenStorage>((ref) => TokenStorage());

/// One [ApiClient] (and therefore one underlying [Dio]) for the whole app —
/// every feature's data layer depends on this rather than constructing its
/// own Dio instance, so token attachment/refresh (see `ApiClient`) is
/// consistent everywhere.
final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(tokenStorage: ref.watch(tokenStorageProvider));
});

/// Shared pick-and-upload flow for profile photos and trip photos — see
/// [ImageUploadService]'s class doc.
final imageUploadServiceProvider = Provider<ImageUploadService>((ref) => ImageUploadService());
