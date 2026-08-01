import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/providers.dart';
import '../data/auth_api.dart';
import '../data/auth_models.dart';
import '../data/auth_repository.dart';
import 'auth_state.dart';

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(apiClientProvider)));

final authRepositoryProvider = Provider<AuthRepository>((ref) => AuthRepository(
      authApi: ref.watch(authApiProvider),
      tokenStorage: ref.watch(tokenStorageProvider),
    ));

final authControllerProvider = StateNotifierProvider<AuthController, AuthState>((ref) {
  return AuthController(ref.watch(authRepositoryProvider), ref.watch(apiClientProvider));
});

/// Drives the sign-in/sign-out lifecycle for the whole app. The router
/// (`app_router.dart`) watches this to decide between the auth stack and the
/// authenticated tab shell — see its `redirect` callback.
class AuthController extends StateNotifier<AuthState> {
  AuthController(this._repository, ApiClient apiClient) : super(const AuthState.unknown()) {
    // A 401 that survives a refresh attempt means the refresh token itself
    // is no longer valid (revoked/expired) — fall back to signed-out rather
    // than leaving the app stuck against a dead session.
    apiClient.onSessionExpired = () {
      if (mounted) state = const AuthState.unauthenticated();
    };
    _bootstrap();
  }

  final AuthRepository _repository;

  Future<void> _bootstrap() async {
    final hasSession = await _repository.hasStoredSession();
    state = hasSession ? const AuthState.authenticated() : const AuthState.unauthenticated();
  }

  Future<bool> signInWithGoogle() async {
    state = const AuthState.authenticating();
    try {
      await _repository.signInWithGoogle();
      state = const AuthState.authenticated();
      return true;
    } on ApiException catch (e) {
      state = AuthState.unauthenticated(errorMessage: e.message);
      return false;
    }
  }

  Future<bool> requestPhoneOtp(String phoneNumber) async {
    try {
      await _repository.requestPhoneOtp(phoneNumber);
      return true;
    } on ApiException catch (e) {
      state = AuthState.unauthenticated(errorMessage: e.message);
      return false;
    }
  }

  Future<bool> verifyPhoneOtp(String phoneNumber, String code) async {
    state = const AuthState.authenticating();
    try {
      await _repository.verifyPhoneOtp(phoneNumber, code);
      state = const AuthState.authenticated();
      return true;
    } on ApiException catch (e) {
      state = AuthState.unauthenticated(errorMessage: e.message);
      return false;
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    state = const AuthState.unauthenticated();
  }
}
