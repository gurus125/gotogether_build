import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import 'photo_search_models.dart';

/// `GET /photos/search` — stock destination photos (Pexels, proxied server-
/// side so the API key never ships in this app) for the trip photo picker's
/// "Search photos" option, alongside the existing gallery upload.
class PhotoSearchApi {
  PhotoSearchApi(this._apiClient);

  final ApiClient _apiClient;

  Future<List<PhotoSearchResult>> search(String query, {int page = 1}) async {
    // Backend returns a bare JSON array (`List<PhotoSearchResultResponse>`),
    // not an envelope object — same pattern as MembershipApi.roster.
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>(
          '/photos/search',
          queryParameters: {'query': query, 'page': page},
        ));
    return (response.data ?? const []).map((e) => PhotoSearchResult.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<T> _run<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      final errorData = e.response?.data;
      final message = errorData is Map<String, dynamic> ? errorData['message'] as String? : null;
      throw ApiException(message ?? 'Something went wrong. Please try again.', statusCode: e.response?.statusCode);
    }
  }
}
