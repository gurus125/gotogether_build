import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import 'review_models.dart';

/// Review APIs (API Specification Section 11) — `POST /reviews/{id}/report`
/// intentionally absent, see backend `ReviewService`'s class doc (needs the
/// `report`/`admin` modules, not built yet).
class ReviewApi {
  ReviewApi(this._apiClient);

  final ApiClient _apiClient;

  /// Throws [ApiException] with `statusCode: 409` if this pair has already
  /// been reviewed for this trip (backend's `ConflictException`, informally
  /// "DUPLICATE_REVIEW" — there's no dedicated machine-readable code for it,
  /// just the generic `CONFLICT` envelope, so callers key off the status
  /// code and show `e.message`).
  Future<ReviewResponse> submit(String tripId, SubmitReviewRequest request) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/trips/$tripId/reviews',
          data: request.toJson(),
        ));
    return ReviewResponse.fromJson(response.data!);
  }

  Future<CursorPage<ReviewResponse>> published(String userId, {String? cursor, int limit = 20}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/users/$userId/reviews',
          queryParameters: {if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, ReviewResponse.fromJson);
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
