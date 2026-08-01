import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

/// Shared placeholder body for tabs whose real feature module hasn't been
/// built yet, so Phase 0 produces a runnable, navigable app instead of a
/// blank screen — replaced screen-by-screen as each module's Phase lands.
class PhasePlaceholder extends StatelessWidget {
  const PhasePlaceholder({super.key, required this.title, required this.phaseNote});

  final String title;
  final String phaseNote;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.construction_outlined, size: 40, color: AppColors.textTertiary),
              const SizedBox(height: 12),
              Text(
                phaseNote,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
