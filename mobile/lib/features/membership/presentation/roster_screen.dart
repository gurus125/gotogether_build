import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/reason_picker_sheet.dart';
import '../../trip/state/trip_providers.dart';
import '../../user/state/user_providers.dart';
import '../data/membership_models.dart';
import '../state/membership_providers.dart';

/// Full roster (API Spec Section 6: `GET /trips/{id}/members`) — reached from
/// Trip Details' "View all" on the members preview. `trustScore` is never
/// shown (always null — see `RosterMember`'s doc).
///
/// Also watches `tripDetailsProvider` (previously unused here) purely to
/// learn two things a roster row needs but its own endpoint doesn't return:
/// whether the *viewer* is the organizer, and the trip's current status —
/// both drive whether the "Remove" action below appears at all. Mirrors
/// `TripApi.cancel`'s own destination: `MembershipApi.removeMember` was
/// already fully wired backend-to-Flutter-data-layer but never reachable
/// from any screen until now.
class RosterScreen extends ConsumerWidget {
  const RosterScreen({super.key, required this.tripId});

  final String tripId;

  static const _nonRemovableStatuses = {'COMPLETED', 'CANCELLED', 'ARCHIVED'};

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rosterAsync = ref.watch(rosterProvider(tripId));
    final selfId = ref.watch(currentUserProvider).valueOrNull?.id;
    final tripDetails = ref.watch(tripDetailsProvider(tripId)).valueOrNull;
    final isOrganizer = tripDetails != null && tripDetails.organizer.id == selfId;
    final canRemove = isOrganizer && !_nonRemovableStatuses.contains(tripDetails?.trip.status);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Members')),
      body: rosterAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load members.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
        data: (members) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: members.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, i) => _MemberTile(
            tripId: tripId,
            member: members[i],
            isSelf: members[i].userId == selfId,
            canRemove: canRemove && !members[i].isOrganizer && members[i].userId != selfId,
          ),
        ),
      ),
    );
  }
}

class _MemberTile extends ConsumerStatefulWidget {
  const _MemberTile({required this.tripId, required this.member, required this.isSelf, required this.canRemove});

  final String tripId;
  final RosterMember member;
  final bool isSelf;
  final bool canRemove;

  @override
  ConsumerState<_MemberTile> createState() => _MemberTileState();
}

class _MemberTileState extends ConsumerState<_MemberTile> {
  static const _removeReasons = [
    'Not responding / inactive',
    'Broke group rules or guidelines',
    'Inappropriate behaviour',
    'Safety concern',
    'No longer able to join (personal reasons)',
  ];

  bool _removing = false;

  @override
  Widget build(BuildContext context) {
    final member = widget.member;
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      // Self isn't tappable — there's no "view your own Trust+Reviews from
      // someone else's roster" concept in the mockup; My Profile already
      // covers that. Opens `TrustReviewsScreen` (Phase 5) for everyone else.
      onTap: widget.isSelf ? null : () => context.push('/users/${member.userId}/trust-reviews', extra: member.displayName),
      child: Container(
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
            if (widget.canRemove)
              _removing
                  ? const Padding(
                      padding: EdgeInsets.all(8),
                      child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.error)),
                    )
                  : IconButton(
                      tooltip: 'Remove from trip',
                      onPressed: _removeMember,
                      icon: const Icon(Icons.person_remove_outlined, size: 19, color: AppColors.error),
                    )
            else if (!widget.isSelf)
              const Icon(Icons.chevron_right, size: 18, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }

  Future<void> _removeMember() async {
    final reason = await showReasonPicker(
      context,
      title: 'Remove ${widget.member.displayName}?',
      subtitle: 'They\'ll be notified they were removed, along with the reason. This can\'t be undone — they would need to request to join again.',
      presetReasons: _removeReasons,
      confirmLabel: 'Remove traveller',
    );
    if (reason == null || !mounted) return;
    setState(() => _removing = true);
    try {
      await ref.read(membershipApiProvider).removeMember(widget.tripId, widget.member.userId, reason);
      ref.invalidate(rosterProvider(widget.tripId));
      ref.invalidate(tripDetailsProvider(widget.tripId));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${widget.member.displayName} was removed from the trip.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _removing = false);
    }
  }
}
