import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../../core/upload/presigned_upload_url.dart';
import '../../auth/data/auth_models.dart';
import 'trip_models.dart';

/// Trip + Explore APIs (API Specification Sections 6, 7). Image upload
/// (`getImageUploadUrl`/`addImage`/`deleteImage` below) is now wired against
/// `storage.StorageService` on the backend — previously unimplemented, see
/// `TripController`'s class doc. Endpoints needing `trip_members`/
/// `join_requests` data (roster, leave, My Trips tabs) still aren't listed
/// here; those live in `MembershipApi`/`JoinRequestApi`.
class TripApi {
  TripApi(this._apiClient);

  final ApiClient _apiClient;

  Future<TripDetails> createDraft(CreateTripRequest request) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>('/trips', data: request.toJson()));
    return TripDetails.fromJson(response.data!);
  }

  Future<TripDetails> publish(String tripId) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>('/trips/$tripId/publish'));
    return TripDetails.fromJson(response.data!);
  }

  Future<TripDetails> cancel(String tripId, String reason) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/trips/$tripId/cancel',
          data: {'reason': reason},
        ));
    return TripDetails.fromJson(response.data!);
  }

  Future<void> delete(String tripId) {
    return _run(() => _apiClient.dio.delete<void>('/trips/$tripId'));
  }

  /// "Manage Trip" — see `UpdateTripRequest`'s class doc.
  Future<TripDetails> update(String tripId, UpdateTripRequest request) async {
    final response = await _run(() => _apiClient.dio.patch<Map<String, dynamic>>('/trips/$tripId', data: request.toJson()));
    return TripDetails.fromJson(response.data!);
  }

  Future<TripDetailsResponse> getDetails(String tripId) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/trips/$tripId'));
    return TripDetailsResponse.fromJson(response.data!);
  }

  Future<CursorPage<TripSummary>> recommended({String? cursor, int limit = 20}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/trips/recommended',
          queryParameters: {if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, TripSummary.fromJson);
  }

  Future<CursorPage<TripSummary>> list({
    String? destinationId,
    String? kind,
    String? status,
    String? cursor,
    int limit = 20,
  }) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/trips',
          queryParameters: {
            if (destinationId != null) 'destinationId': destinationId,
            if (kind != null) 'kind': kind,
            if (status != null) 'status': status,
            if (cursor != null) 'cursor': cursor,
            'limit': limit,
          },
        ));
    return CursorPage.fromJson(response.data!, TripSummary.fromJson);
  }

  Future<CursorPage<TripSummary>> explore({
    String? destinationId,
    int? budgetMin,
    int? budgetMax,
    String? dateFrom,
    String? dateTo,
    int? durationMinDays,
    int? durationMaxDays,
    String? tripType,
    String? kind,
    bool verifiedOnly = false,
    String? sort,
    String? cursor,
    int limit = 20,
  }) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/explore',
          queryParameters: {
            if (destinationId != null) 'destinationId': destinationId,
            if (budgetMin != null) 'budgetMin': budgetMin,
            if (budgetMax != null) 'budgetMax': budgetMax,
            if (dateFrom != null) 'dateFrom': dateFrom,
            if (dateTo != null) 'dateTo': dateTo,
            if (durationMinDays != null) 'durationMinDays': durationMinDays,
            if (durationMaxDays != null) 'durationMaxDays': durationMaxDays,
            if (tripType != null) 'tripType': tripType,
            if (kind != null) 'kind': kind,
            'verifiedOnly': verifiedOnly,
            if (sort != null) 'sort': sort,
            if (cursor != null) 'cursor': cursor,
            'limit': limit,
          },
        ));
    return CursorPage.fromJson(response.data!, TripSummary.fromJson);
  }

  /// My Trips (API Spec Section 6) — returns a plain list, not a cursor page
  /// (the backend's `GET /users/me/trips` returns `List<TripSummary>`
  /// directly; each tab's dataset is small enough at MVP scale that pagination
  /// wasn't worth the complexity — see backend `TripController.myTrips`).
  Future<List<TripSummary>> myTrips(String tab) async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>('/users/me/trips', queryParameters: {'tab': tab}));
    return (response.data ?? const []).map((e) => TripSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// "Travel stats" (JOINED/COMPLETED/ORGANIZED) for the Profile screen —
  /// see backend `TravelStatsResponse`'s class doc (added for Phase 5, not
  /// in the original API Specification table).
  Future<TravelStats> travelStats() async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/users/me/travel-stats'));
    return TravelStats.fromJson(response.data!);
  }

  Future<void> save(String tripId) {
    return _run(() => _apiClient.dio.post<void>('/trips/$tripId/save'));
  }

  Future<void> unsave(String tripId) {
    return _run(() => _apiClient.dio.delete<void>('/trips/$tripId/save'));
  }

  /// Step 1 of trip-photo upload — organizer-only, enforced server-side
  /// (`TripService.assertOrganizer`) before a presigned URL is even issued.
  Future<PresignedUploadUrl> getImageUploadUrl(String tripId, String contentType) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/trips/$tripId/images/upload-url',
          queryParameters: {'content_type': contentType},
        ));
    return PresignedUploadUrl.fromJson(response.data!);
  }

  /// Step 2 — persists the uploaded image (its `public_url` from step 1) as a `trip_images` row.
  Future<TripImage> addImage(String tripId, String imageUrl, bool isPrimary) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/trips/$tripId/images',
          data: {'image_url': imageUrl, 'is_primary': isPrimary},
        ));
    return TripImage.fromJson(response.data!);
  }

  Future<void> deleteImage(String tripId, String imageId) {
    return _run(() => _apiClient.dio.delete<void>('/trips/$tripId/images/$imageId'));
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
