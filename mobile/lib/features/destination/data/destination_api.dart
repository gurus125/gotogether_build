import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../auth/data/auth_models.dart';
import 'destination_models.dart';

/// Destination APIs (API Specification Section 5) — all read-only.
class DestinationApi {
  DestinationApi(this._apiClient);

  final ApiClient _apiClient;

  Future<List<DestinationSummary>> list({String? category}) async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>(
          '/destinations',
          queryParameters: {if (category != null) 'category': category},
        ));
    return _parseList(response.data);
  }

  Future<List<DestinationSummary>> search(String query) async {
    if (query.trim().isEmpty) return [];
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>(
          '/destinations/search',
          queryParameters: {'q': query},
        ));
    return _parseList(response.data);
  }

  Future<List<DestinationSummary>> popular({int limit = 10}) async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>(
          '/destinations/popular',
          queryParameters: {'limit': limit},
        ));
    return _parseList(response.data);
  }

  Future<List<DestinationSummary>> featured() async {
    final response = await _run(() => _apiClient.dio.get<List<dynamic>>('/destinations/featured'));
    return _parseList(response.data);
  }

  List<DestinationSummary> _parseList(List<dynamic>? data) =>
      (data ?? const []).map((e) => DestinationSummary.fromJson(e as Map<String, dynamic>)).toList();

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
