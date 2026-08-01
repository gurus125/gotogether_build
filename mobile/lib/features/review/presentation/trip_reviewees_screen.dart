import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../membership/data/membership_models.dart';
import '../../membership/state/membership_providers.dart';
import '../../user/state/user_providers.dart';

/// "Who can I review on this trip" — reached from My Trips' Past tab
/// ("Leave a review"). No dedicated backend endpoint exists for this list
/// (API Specification Section 11 only names 3 Review endpoints, and Section
/// 19 calls that table "the complete contract"), so this is built from the
/// existing roster (`GET /trips/{id}/members`) minus the signed-in user.
/// There's no way to know ahead of time which members have already been
/// reviewed (no per-trip "my submitted reviews" endpoint either) — tapping
/// someone already reviewed will surface the backend's 409 duplicate-review
/// error on the next screen rather than being filtered out here.
class TripRevieweesScreen extends ConsumerWidget {
  const TripRevieweesScreen({super.key, required this.tripId, required this.tripTitle});

  final String tripId;
  final String tripTitle;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rosterAsync = ref.watch(rosterProvider(tripId));
    final userAsync = ref.watch(currentUserProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text('Review — $tripTitle')),
      body: rosterAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load trip members.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
        data: (members) {
          final selfId = userAsync.valueOrNull?.id;
          final reviewable = members.where((m) => m.userId != selfId).toList();
          if (reviewable.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text('No other travellers to review on this trip.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: reviewable.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (context, i) => _RevieweeTile(tripId: tripId, member: reviewable[i]),
          );
        },
      ),
    );
  }
}

class _RevieweeTile extends StatelessWidget {
  const _RevieweeTile({required this.tripId, required this.member});

  final String tripId;
  final RosterMember member;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: AppColors.primaryLight,
            backgroundImage: member.photoUrl != null ? NetworkImage(member.photoUrl!) : null,
            child: member.photoUrl == null
                ? Text(member.displayName.isNotEmpty ? member.displayName[0].toUpperCase() : '?',
                    style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600))
                : null,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(member.displayName, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                if (member.isOrganizer)
                  const Text('Organizer', style: TextStyle(fontSize: 10.5, color: AppColors.primary, fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          TextButton(
            onPressed: () => context.push('/trip/$tripId/reviews/${member.userId}', extra: member.displayName),
            child: const Text('Write a review'),
          ),
        ],
      ),
    );
  }
}
