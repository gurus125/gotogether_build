import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../auth/state/auth_controller.dart';
import '../company/state/company_providers.dart';
import '../trip/data/trip_models.dart';
import '../trip/state/trip_providers.dart';
import '../trust/data/trust_models.dart';
import '../trust/state/trust_providers.dart';
import '../user/data/user_models.dart';
import '../user/state/user_providers.dart';
import 'state/profile_providers.dart';

/// My Profile (Self) — rebuilt for Phase 5 to match `profilev1.pdf` /
/// `GoTogether Profile Screen-print.dc.html`'s "MY PROFILE (SELF)" state
/// exactly: identity block + Edit profile, single-bar Trust Score card,
/// verification progress banner, Travel stats, My trips, Settings, Log out.
///
/// Two things the mockup shows that this build cannot back with real data
/// yet, both left as an honest "coming soon" tap rather than invented:
/// - "Verify now →" and "Manage verification" — there's no Verification
///   Flow screen built yet (`GoTogether Verification Flow.dc.html` is its
///   own undelivered phase); "X of 4 complete" itself IS real, computed
///   from `UserResponse.verificationLevel`.
/// - "Privacy settings", "Notifications" — no settings/notification-
///   preferences backend exists at all (out of Phase 5 scope). Same
///   disabled-with-snackbar pattern as My Trips' "Edit trip" (Phase 3).
///
/// "Trust Score" IS wired — opens `TrustTipsScreen`, a full breakdown of
/// every weighted component behind `GET /users/me/trust-score`, not just its
/// `improvementTips` (see that screen's class doc). "Help & support" now
/// opens `HelpSupportScreen` — see that screen's class doc.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileProvider);
    final userAsync = ref.watch(currentUserProvider);
    final trustAsync = ref.watch(myTrustScoreProvider);
    final statsAsync = ref.watch(travelStatsProvider);
    final upcomingAsync = ref.watch(myTripsProvider('upcoming'));
    final savedAsync = ref.watch(myTripsProvider('saved'));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Profile')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(profileProvider);
          ref.invalidate(currentUserProvider);
          ref.invalidate(myTrustScoreProvider);
          ref.invalidate(travelStatsProvider);
          ref.invalidate(myTripsProvider('upcoming'));
          ref.invalidate(myTripsProvider('saved'));
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Center(
              child: Column(
                children: [
                  Builder(builder: (context) {
                    // `.valueOrNull` rather than `.value` — the latter
                    // rethrows synchronously when the provider is in an
                    // error state (e.g. the backend unreachable), which
                    // would crash this whole screen's build.
                    final p = profileAsync.valueOrNull;
                    return CircleAvatar(
                      radius: 40,
                      backgroundColor: AppColors.primaryLight,
                      backgroundImage: p?.photoUrl != null ? NetworkImage(p!.photoUrl!) : null,
                      child: p?.photoUrl == null
                          ? Text(
                              (p != null && p.displayName.isNotEmpty) ? p.displayName[0].toUpperCase() : '?',
                              style: const TextStyle(fontSize: 28, color: AppColors.primary, fontWeight: FontWeight.w600),
                            )
                          : null,
                    );
                  }),
                  const SizedBox(height: 12),
                  profileAsync.when(
                    data: (p) => Text(
                      [p.displayName, if (_age(p.dateOfBirth) != null) '${_age(p.dateOfBirth)}'].join(', '),
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    loading: () => const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2)),
                    error: (e, _) => const Text('—'),
                  ),
                  const SizedBox(height: 4),
                  profileAsync.when(
                    data: (p) => Text(p.city ?? '', style: const TextStyle(color: AppColors.textSecondary, fontSize: 12.5)),
                    loading: () => const SizedBox.shrink(),
                    error: (e, _) => const SizedBox.shrink(),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(onPressed: () => context.push('/profile/edit'), child: const Text('Edit profile')),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _TrustScoreCard(trustAsync: trustAsync),
            const SizedBox(height: 12),
            _VerificationBanner(userAsync: userAsync),
            const SizedBox(height: 12),
            _TravelStatsCard(statsAsync: statsAsync),
            const SizedBox(height: 12),
            _MyTripsCard(upcomingAsync: upcomingAsync, savedAsync: savedAsync),
            const SizedBox(height: 12),
            _SettingsCard(),
            const SizedBox(height: 12),
            const _CompanyCard(),
            const SizedBox(height: 12),
            _MenuTile(icon: Icons.logout, label: 'Log out', destructive: true, onTap: () => _confirmLogout(context, ref)),
          ],
        ),
      ),
    );
  }

  /// Whole-years-old from an ISO `date_of_birth` — the mockup's ", 29"
  /// beside the name isn't its own backend field, just derived here.
  static int? _age(String? dateOfBirth) {
    if (dateOfBirth == null) return null;
    final dob = DateTime.tryParse(dateOfBirth);
    if (dob == null) return null;
    final now = DateTime.now();
    var age = now.year - dob.year;
    if (now.month < dob.month || (now.month == dob.month && now.day < dob.day)) age--;
    return age;
  }

  Future<void> _confirmLogout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Log out?'),
        content: const Text('You can sign back in any time with Google or your phone number.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Log out')),
        ],
      ),
    );
    if (confirmed == true) {
      await ref.read(authControllerProvider.notifier).logout();
    }
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
      child: child,
    );
  }
}

class _TrustScoreCard extends StatelessWidget {
  const _TrustScoreCard({required this.trustAsync});

  final AsyncValue<TrustScoreResponse> trustAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: trustAsync.when(
        loading: () => const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: LinearProgressIndicator()),
        error: (e, _) => const Text('Could not load your trust score.', style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
        data: (trust) {
          final pct = (trust.currentScore / 10).clamp(0.0, 1.0);
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Icon(Icons.star_rounded, color: AppColors.accent, size: 20),
                  const SizedBox(width: 6),
                  Text(trust.currentScore.toStringAsFixed(1), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
                  const Padding(
                    padding: EdgeInsets.only(bottom: 3, left: 3),
                    child: Text('/ 10 Trust Score', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(value: pct, minHeight: 7, backgroundColor: AppColors.border, color: AppColors.primary),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// "Verification progress · X of 4 complete" (Phone, Email, Government ID,
/// Selfie-match-bundled-with-ID — see `GoTogether Verification Flow.dc.html`).
/// Only these 3 backend-tracked states exist (`VerificationLevel`
/// NONE/PHONE/EMAIL/ID_APPROVED), so PHONE→1/4, EMAIL→2/4, ID_APPROVED→4/4.
class _VerificationBanner extends ConsumerWidget {
  const _VerificationBanner({required this.userAsync});

  final AsyncValue<UserResponse> userAsync;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return userAsync.when(
      loading: () => const SizedBox.shrink(),
      error: (e, _) => const SizedBox.shrink(),
      data: (user) {
        final level = user.verificationLevel;
        if (level == 'ID_APPROVED') return const SizedBox.shrink();
        final complete = switch (level) { 'EMAIL' => 2, 'PHONE' => 1, _ => 0 };
        return _Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Verification progress · $complete of 4 complete', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              const Text(
                'Complete Government ID verification to boost your trust score.',
                style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.5),
              ),
              const SizedBox(height: 8),
              GestureDetector(
                onTap: () => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Coming soon.'))),
                child: const Text('Verify now →', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.primary)),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _TravelStatsCard extends StatelessWidget {
  const _TravelStatsCard({required this.statsAsync});

  final AsyncValue<TravelStats> statsAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Travel stats', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          statsAsync.when(
            loading: () => const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2))),
            error: (e, _) => const Text('Could not load stats.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
            data: (stats) => Row(
              children: [
                _StatColumn(value: '${stats.joined}', label: 'JOINED'),
                _StatColumn(value: '${stats.completed}', label: 'COMPLETED'),
                _StatColumn(value: '${stats.organized}', label: 'ORGANIZED'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatColumn extends StatelessWidget {
  const _StatColumn({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
          const SizedBox(height: 2),
          Text(label, style: const TextStyle(fontSize: 9.5, color: AppColors.textSecondary, letterSpacing: 0.4)),
        ],
      ),
    );
  }
}

class _MyTripsCard extends StatelessWidget {
  const _MyTripsCard({required this.upcomingAsync, required this.savedAsync});

  final AsyncValue<List<dynamic>> upcomingAsync;
  final AsyncValue<List<dynamic>> savedAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('My trips', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          _MyTripsRow(label: 'Upcoming', countAsync: upcomingAsync, onTap: () => context.push('/my-trips')),
          const SizedBox(height: 8),
          _MyTripsRow(label: 'Saved', countAsync: savedAsync, onTap: () => context.push('/my-trips')),
        ],
      ),
    );
  }
}

class _MyTripsRow extends StatelessWidget {
  const _MyTripsRow({required this.label, required this.countAsync, required this.onTap});

  final String label;
  final AsyncValue<List<dynamic>> countAsync;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final count = countAsync.valueOrNull?.length;
    return GestureDetector(
      onTap: onTap,
      child: Row(
        children: [
          Expanded(child: Text(label, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textPrimary))),
          Text(count != null ? '$count trip${count == 1 ? '' : 's'}' : '—', style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right, size: 16, color: AppColors.textTertiary),
        ],
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    void comingSoon() => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Coming soon.')));

    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Settings', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _SettingsRow(label: 'Manage verification', onTap: comingSoon),
          _SettingsRow(label: 'Privacy settings', onTap: comingSoon),
          _SettingsRow(label: 'Notifications', onTap: comingSoon),
          _SettingsRow(label: 'Trust Score', onTap: () => context.push('/profile/trust-tips')),
          _SettingsRow(label: 'Help & support', onTap: () => context.push('/help'), isLast: true),
        ],
      ),
    );
  }
}

/// Entry point into Phase 7's Travel Company flows — no design mockup shows
/// this anywhere (same flagged gap as the Notifications bell icon), so this
/// is a plain settings-style row rather than an invented visual treatment.
/// `myCompanyStatusProvider` returning `null` means "not staff of any
/// company" (the common case), so most users see the "Become a Partner" CTA;
/// existing staff see "My Company" instead.
class _CompanyCard extends ConsumerWidget {
  const _CompanyCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync = ref.watch(myCompanyStatusProvider);
    return _Card(
      child: statusAsync.when(
        loading: () => const Padding(padding: EdgeInsets.symmetric(vertical: 4), child: LinearProgressIndicator()),
        error: (e, _) => const SizedBox.shrink(),
        data: (status) => _SettingsRow(
          label: status == null ? 'Become a Travel Partner' : 'My Company',
          isLast: true,
          onTap: () => context.push(status == null ? '/companies/apply' : '/companies/me'),
        ),
      ),
    );
  }
}

class _SettingsRow extends StatelessWidget {
  const _SettingsRow({required this.label, required this.onTap, this.isLast = false});

  final String label;
  final VoidCallback onTap;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: isLast ? null : const BoxDecoration(border: Border(bottom: BorderSide(color: AppColors.border))),
        child: Row(
          children: [
            Expanded(child: Text(label, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textPrimary))),
            const Icon(Icons.chevron_right, size: 16, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }
}

class _MenuTile extends StatelessWidget {
  const _MenuTile({required this.icon, required this.label, required this.onTap, this.destructive = false});

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final color = destructive ? AppColors.error : AppColors.textPrimary;
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
          child: Row(
            children: [
              Icon(icon, size: 18, color: color),
              const SizedBox(width: 12),
              Text(label, style: TextStyle(fontSize: 13, color: color)),
              const Spacer(),
              const Icon(Icons.chevron_right, size: 18, color: AppColors.textTertiary),
            ],
          ),
        ),
      ),
    );
  }
}
