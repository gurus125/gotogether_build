import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import 'company_models.dart';

/// Travel Company APIs (API Spec Section 14).
class CompanyApi {
  CompanyApi(this._apiClient);

  final ApiClient _apiClient;

  Future<Company> apply(ApplyCompanyRequest request) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>('/companies/apply', data: request.toJson()));
    return Company.fromJson(response.data!);
  }

  Future<CompanyProfile> getProfile(String companyId) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/companies/$companyId'));
    return CompanyProfile.fromJson(response.data!);
  }

  /// Throws a 403 `ApiException` if the caller isn't staff of any company —
  /// callers use that to distinguish "not a Travel Partner yet" from a real
  /// error (see `myCompanyStatusProvider`'s doc).
  Future<CompanyVerificationStatus> getMyVerificationStatus() async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>('/companies/me/verification-status'));
    return CompanyVerificationStatus.fromJson(response.data!);
  }

  Future<CursorPage<TripSummary>> getMyTrips({String? status, String? cursor, int limit = 20}) async {
    final response = await _run(() => _apiClient.dio.get<Map<String, dynamic>>(
          '/companies/me/trips',
          queryParameters: {if (status != null) 'status': status, if (cursor != null) 'cursor': cursor, 'limit': limit},
        ));
    return CursorPage.fromJson(response.data!, TripSummary.fromJson);
  }

  Future<List<CompanyStaff>> listStaff() async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>('/companies/me/staff'));
    return (response.data ?? const []).map((e) => CompanyStaff.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// `role` is `manager` or `support` — requesting `owner` always fails with
  /// `409 MULTI_ADMIN_NOT_ENABLED` (Operations Module A's single-active-owner MVP cap).
  Future<CompanyStaff> inviteStaff(String userId, String role) async {
    final response = await _run(() => _apiClient.dio.post<Map<String, dynamic>>(
          '/companies/me/staff',
          data: {'user_id': userId, 'role': role},
        ));
    return CompanyStaff.fromJson(response.data!);
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
