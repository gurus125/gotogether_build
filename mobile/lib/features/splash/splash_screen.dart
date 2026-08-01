import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// Branding-only splash, matching the approved Auth Flow design's "Splash"
/// state. Purely presentational — the router's redirect (`app_router.dart`)
/// watches `authControllerProvider` and moves away from this route as soon
/// as the stored-session check resolves (to `/home` if a session exists,
/// `/welcome` otherwise), so there's no navigation logic to write here.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.primary,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20)),
              child: Center(
                child: Container(
                  width: 26,
                  height: 26,
                  decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(8)),
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text('GoTogether', style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text('Travel with people you trust', style: TextStyle(color: Colors.white.withOpacity(0.85), fontSize: 11.5)),
          ],
        ),
      ),
    );
  }
}
