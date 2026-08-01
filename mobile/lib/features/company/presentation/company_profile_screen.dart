import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../data/company_models.dart';
import '../state/company_providers.dart';

/// Public Company Profile (`GET /companies/{id}`, Operations Module A:
/// "shows business name/logo, Verified Partner badge, an aggregate
/// traveller-satisfaction rating..., past trips run, a published
/// cancellation policy, and business contact information"). No design
/// mockup exists for this anywhere in the approved design set — functional
/// only, built from the design system's existing tokens.
class CompanyProfileScreen extends ConsumerWidget {
  const CompanyProfileScreen({super.key, required this.companyId, this.fallbackName});

  final String companyId;
  final String? fallbackName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(companyProfileProvider(companyId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text(fallbackName ?? 'Travel Partner')),
      body: profileAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Could not load this company.\n$e', textAlign: TextAlign.center, style: const TextStyle(fontSize: 12.5)),
          ),
        ),
        data: (profile) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Center(
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 36,
                    backgroundColor: AppColors.primaryLight,
                    backgroundImage: profile.logoUrl != null ? NetworkImage(profile.logoUrl!) : null,
                    child: profile.logoUrl == null
                        ? Text(profile.displayName.isNotEmpty ? profile.displayName[0].toUpperCase() : '?',
                            style: const TextStyle(fontSize: 26, color: AppColors.primary, fontWeight: FontWeight.w600))
                        : null,
                  ),
                  const SizedBox(height: 10),
                  Text(profile.displayName, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 6),
                  if (profile.status == 'VERIFIED')
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(color: AppColors.successTint, borderRadius: BorderRadius.circular(100)),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.verified, size: 13, color: AppColors.success),
                          SizedBox(width: 4),
                          Text('Verified Partner', style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.success)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _StatsRow(profile: profile),
            const SizedBox(height: 12),
            if (profile.description != null && profile.description!.isNotEmpty) ...[
              _Card(child: Text(profile.description!, style: const TextStyle(fontSize: 12.5, height: 1.6))),
              const SizedBox(height: 12),
            ],
            _Card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Cancellation policy', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  Text(profile.cancellationPolicy, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.6)),
                ],
              ),
            ),
            const SizedBox(height: 12),
            _Card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Contact', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  Text(profile.supportEmail, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                  const SizedBox(height: 4),
                  Text(profile.supportPhone, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                  if (profile.websiteUrl != null) ...[
                    const SizedBox(height: 4),
                    Text(profile.websiteUrl!, style: const TextStyle(fontSize: 12, color: AppColors.primary)),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
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

class _StatsRow extends StatelessWidget {
  const _StatsRow({required this.profile});

  final CompanyProfile profile;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: Row(
        children: [
          Expanded(
            child: Column(
              children: [
                Text(
                  profile.aggregateRating != null ? profile.aggregateRating!.toStringAsFixed(1) : '—',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 2),
                const Text('RATING', style: TextStyle(fontSize: 9.5, color: AppColors.textTertiary, letterSpacing: 0.4)),
              ],
            ),
          ),
          Expanded(
            child: Column(
              children: [
                Text('${profile.tripsCompletedCount}', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                const SizedBox(height: 2),
                const Text('TRIPS COMPLETED', style: TextStyle(fontSize: 9.5, color: AppColors.textTertiary, letterSpacing: 0.4)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
