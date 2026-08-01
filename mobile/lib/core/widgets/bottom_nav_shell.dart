import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../theme/app_colors.dart';

/// Bottom navigation bar matching the approved design (Home / Explore /
/// raised accent-colored Create button / Chats / Profile — see the
/// "Home Screen" design doc's footer nav).
class BottomNavShell extends StatelessWidget {
  const BottomNavShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  static const _tabs = [
    (icon: Icons.home_rounded, label: 'Home'),
    (icon: Icons.explore_outlined, label: 'Explore'),
    (icon: Icons.add, label: ''), // Create — rendered as the raised accent button below.
    (icon: Icons.chat_bubble_outline, label: 'Chats'),
    (icon: Icons.person_outline, label: 'Profile'),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: SafeArea(
        child: Container(
          height: 64,
          decoration: const BoxDecoration(
            color: AppColors.surface,
            border: Border(top: BorderSide(color: AppColors.border)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: List.generate(_tabs.length, (index) {
              final isCreate = index == 2;
              final isActive = navigationShell.currentIndex == index;

              if (isCreate) {
                return GestureDetector(
                  onTap: () => navigationShell.goBranch(index),
                  child: Container(
                    width: 48,
                    height: 48,
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: AppColors.accent,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(Icons.add, color: Colors.white),
                  ),
                );
              }

              final tab = _tabs[index];
              final color = isActive ? AppColors.primary : AppColors.textTertiary;
              return InkWell(
                onTap: () => navigationShell.goBranch(index, initialLocation: index == navigationShell.currentIndex),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(tab.icon, color: color, size: 22),
                    const SizedBox(height: 2),
                    Text(tab.label, style: TextStyle(color: color, fontSize: 10.5)),
                  ],
                ),
              );
            }),
          ),
        ),
      ),
    );
  }
}
