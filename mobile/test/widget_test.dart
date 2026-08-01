// Smoke test: the app boots without throwing. This replaces the
// `flutter create .` boilerplate counter-app test, which referenced a
// `MyApp` class and counter UI that never existed in this codebase — the
// root widget has always been `GoTogetherApp` (see `lib/app.dart`).
//
// Kept intentionally minimal: `GoTogetherApp` immediately redirects through
// `Splash -> Welcome/Home` based on real network calls (auth check, etc.),
// which isn't something a real test should assert on without mocking the
// backend — out of scope for this smoke test.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gotogether/app.dart';

void main() {
  testWidgets('GoTogetherApp builds without throwing', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: GoTogetherApp()));
    await tester.pump();

    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
