import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;

/// Backend base URL for local development.
///
/// The backend runs on port 8081, not the Postgres/Spring Boot default 8080
/// — a separate, unrelated project on the dev machine this was set up on
/// already permanently owns 8080 (see `application.yml`'s `server.port`
/// comment). Android emulators cannot reach the host machine's `localhost`
/// directly — `10.0.2.2` is the documented Android emulator alias for the
/// host loopback — while iOS simulators share the host's network namespace
/// and can use `localhost` directly. A physical device needs the host
/// machine's real LAN IP, which can't be known ahead of time; override via
/// `--dart-define=API_BASE_URL=http://<lan-ip>:8081` when running against a
/// physical device.
class AppConfig {
  AppConfig._();

  static const _override = String.fromEnvironment('API_BASE_URL');

  static String get apiBaseUrl {
    if (_override.isNotEmpty) return _override;
    if (!kIsWeb && Platform.isAndroid) return 'http://10.0.2.2:8081';
    return 'http://localhost:8081';
  }
}
