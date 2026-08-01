import 'package:google_sign_in/google_sign_in.dart';

import '../../../core/network/token_storage.dart';
import 'auth_api.dart';
import 'auth_models.dart';

/// Orchestrates the two MVP sign-in paths (Business Rules Module 1: Google
/// Sign-In or Phone OTP, no passwords) — mirrors the backend's `AuthService`
/// split between raw HTTP (`AuthApi`) and app-level session concerns
/// (persisting tokens, driving `GoogleSignIn`) that don't belong on the wire.
class AuthRepository {
  AuthRepository({
    required AuthApi authApi,
    required TokenStorage tokenStorage,
    GoogleSignIn? googleSignIn,
  })  : _authApi = authApi,
        _tokenStorage = tokenStorage,
        // `serverClientId`/`clientId` come from the native Google Sign-In
        // setup (google-services.json on Android, GIDClientID in
        // Info.plist on iOS) — see mobile/README.md. Requesting only
        // `email` since that's all `GoogleTokenVerifier` needs server-side.
        _googleSignIn = googleSignIn ?? GoogleSignIn(scopes: const ['email']);

  final AuthApi _authApi;
  final TokenStorage _tokenStorage;
  final GoogleSignIn _googleSignIn;

  Future<bool> hasStoredSession() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    return refreshToken != null;
  }

  Future<AuthResponse> signInWithGoogle() async {
    final account = await _googleSignIn.signIn();
    if (account == null) {
      // User cancelled the native Google chooser — not an error state.
      throw const ApiException('Sign-in was cancelled.');
    }

    final authentication = await account.authentication;
    final idToken = authentication.idToken;
    if (idToken == null) {
      throw const ApiException('Google did not return an ID token. Please try again.');
    }

    final response = await _authApi.signInWithGoogle(idToken);
    await _persist(response);
    return response;
  }

  Future<void> requestPhoneOtp(String phoneNumber) {
    return _authApi.requestPhoneOtp(phoneNumber);
  }

  Future<AuthResponse> verifyPhoneOtp(String phoneNumber, String code) async {
    final response = await _authApi.verifyPhoneOtp(phoneNumber, code);
    await _persist(response);
    return response;
  }

  Future<void> logout() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    await _tokenStorage.clear();
    if (refreshToken != null) {
      // Best-effort — the user is logged out locally regardless of whether
      // the server-side revocation call succeeds.
      try {
        await _authApi.logout(refreshToken);
      } catch (_) {
        // Ignored: local session is already cleared above.
      }
    }
    try {
      await _googleSignIn.signOut();
    } catch (_) {
      // Not signed in via Google, or plugin not configured — fine.
    }
  }

  Future<void> _persist(AuthResponse response) {
    return _tokenStorage.saveTokens(accessToken: response.accessToken, refreshToken: response.refreshToken);
  }
}
