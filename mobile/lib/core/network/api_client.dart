import 'dart:async';

import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'token_storage.dart';

/// Thin wrapper around a single shared [Dio] instance.
///
/// Handles the two cross-cutting concerns every authenticated call needs
/// (Backend Architecture: stateless bearer-token API):
/// 1. Attaching the current access token to every request.
/// 2. Transparently refreshing once on a 401 and retrying the original
///    request, so feature code never has to think about token expiry.
///
/// [onSessionExpired] is invoked when a 401 survives a refresh attempt (i.e.
/// the refresh token itself is no longer valid) — the auth layer wires this
/// up to force the user back to signed-out state, without this class needing
/// to know anything about `AuthController`.
class ApiClient {
  ApiClient({required TokenStorage tokenStorage, this.onSessionExpired})
      : _tokenStorage = tokenStorage,
        dio = Dio(BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
        )),
        // A bare client with no interceptors, used only for the refresh call
        // itself — routing it through `dio` would recurse into the 401
        // handler below.
        _refreshDio = Dio(BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
        )) {
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        if (!_isAuthEndpoint(options.path)) {
          final token = await _tokenStorage.readAccessToken();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        final isUnauthorized = error.response?.statusCode == 401;
        final requestOptions = error.requestOptions;

        if (isUnauthorized && !_isAuthEndpoint(requestOptions.path)) {
          final refreshed = await _refreshTokens();
          if (refreshed) {
            try {
              final retryResponse = await _retry(requestOptions);
              handler.resolve(retryResponse);
              return;
            } catch (_) {
              // Fall through to the original error below.
            }
          } else {
            await _tokenStorage.clear();
            onSessionExpired?.call();
          }
        }

        handler.next(error);
      },
    ));
  }

  final Dio dio;
  final Dio _refreshDio;
  final TokenStorage _tokenStorage;

  /// Called once a refresh attempt itself fails (refresh token expired or
  /// revoked) — see class doc.
  void Function()? onSessionExpired;

  Completer<bool>? _refreshInFlight;

  bool _isAuthEndpoint(String path) => path.startsWith('/auth/');

  Future<Response<dynamic>> _retry(RequestOptions requestOptions) {
    final options = Options(method: requestOptions.method, headers: requestOptions.headers);
    return dio.request<dynamic>(
      requestOptions.path,
      data: requestOptions.data,
      queryParameters: requestOptions.queryParameters,
      options: options,
    );
  }

  /// Ensures concurrent 401s only trigger a single `/auth/refresh` call —
  /// several requests failing at once (e.g. a screen firing off a few calls
  /// in parallel) must not race to rotate the refresh token against each
  /// other, since rotation is single-use server-side.
  Future<bool> _refreshTokens() {
    final inFlight = _refreshInFlight;
    if (inFlight != null) return inFlight.future;

    final completer = Completer<bool>();
    _refreshInFlight = completer;

    () async {
      try {
        final refreshToken = await _tokenStorage.readRefreshToken();
        if (refreshToken == null) {
          completer.complete(false);
          return;
        }

        final response = await _refreshDio.post<Map<String, dynamic>>(
          '/auth/refresh',
          data: {'refresh_token': refreshToken},
        );

        // Backend serializes snake_case (spring.jackson.property-naming-strategy:
        // SNAKE_CASE, added during the Phase 2 docs review 2026-07-22).
        final data = response.data!;
        await _tokenStorage.saveTokens(
          accessToken: data['access_token'] as String,
          refreshToken: data['refresh_token'] as String,
        );
        completer.complete(true);
      } catch (_) {
        completer.complete(false);
      } finally {
        _refreshInFlight = null;
      }
    }();

    return completer.future;
  }
}
