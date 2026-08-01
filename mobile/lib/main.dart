import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app.dart';

/// Firebase initialization (firebase_core + messaging) is intentionally not
/// wired here yet — it requires `flutterfire configure` to be run against a
/// real Firebase project first (see mobile/README.md). Added in the
/// Notifications module phase, not Phase 0.
void main() {
  runApp(const ProviderScope(child: GoTogetherApp()));
}
