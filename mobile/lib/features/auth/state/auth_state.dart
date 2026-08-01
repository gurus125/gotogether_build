enum AuthStatus {
  /// Not yet determined whether a stored session exists — the splash screen
  /// stays up while in this state.
  unknown,
  unauthenticated,
  authenticating,
  authenticated,
}

class AuthState {
  const AuthState({required this.status, this.errorMessage});

  const AuthState.unknown() : this(status: AuthStatus.unknown);
  const AuthState.unauthenticated({String? errorMessage}) : this(status: AuthStatus.unauthenticated, errorMessage: errorMessage);
  const AuthState.authenticating() : this(status: AuthStatus.authenticating);
  const AuthState.authenticated() : this(status: AuthStatus.authenticated);

  final AuthStatus status;
  final String? errorMessage;
}
