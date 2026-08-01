import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';

class GoTogetherApp extends ConsumerWidget {
  const GoTogetherApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      // Temporary short display name ("Go") for the app icon/title bar while
      // the app is still in development — swap back to the full
      // "GoTogether" name whenever that's ready to be final.
      title: 'Go',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      routerConfig: router,
    );
  }
}
