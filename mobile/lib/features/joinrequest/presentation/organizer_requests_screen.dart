import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../data/join_request_models.dart';
import '../state/join_request_providers.dart';

/// The Organizer's "manage requests" queue for a trip (API Spec Section 8:
/// `GET /trips/{id}/join-requests`, `.../accept`, `.../reject`).
///
/// No screen for this exists in the approved design set (the kickoff report
/// flagged Chat and the Admin Panel as un-mocked; this is a third, smaller
/// gap in the same category — Join Request management has API coverage but
/// no mockup). Built here to match the established design system (same
/// tokens/components as every other screen) rather than invented business
/// logic — the actions themselves (Accept/Decline) and their outcomes are
/// exactly what Chapter 3 Section 3.3 and the API Specification define.
///
/// Each card now leads with the applicant's real name/photo (backend
/// `JoinRequestController.organizerQueue` overlays `ProfileService` onto the
/// queue — see `JoinRequestResponse#withApplicantProfile`'s doc), tappable
/// through to `TrustReviewsScreen` for the full trust/reviews picture —
/// mirroring `roster_screen.dart`'s identical pattern for trip members.
/// Before this, an organizer deciding Accept/Decline had nothing to go on
/// but a request message and a generic "Traveller request" label — a real
/// gap for a trust-first platform, not a cosmetic one.
class OrganizerRequestsScreen extends ConsumerWidget {
  const OrganizerRequestsScreen({super.key, required this.tripId});

  final String tripId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final queueAsync = ref.watch(organizerQueueProvider(tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Join requests')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(organizerQueueProvider(tripId)),
        child: queueAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(children: const [
            SizedBox(height: 100),
            Center(child: Text('Could not load requests.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
          ]),
          data: (requests) {
            if (requests.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 100),
                Center(child: Text('No join requests yet.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
              ]);
            }
            return ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: requests.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, i) => _RequestCard(tripId: tripId, request: requests[i]),
            );
          },
        ),
      ),
    );
  }
}

class _RequestCard extends ConsumerStatefulWidget {
  const _RequestCard({required this.tripId, required this.request});

  final String tripId;
  final JoinRequestResponse request;

  @override
  ConsumerState<_RequestCard> createState() => _RequestCardState();
}

class _RequestCardState extends ConsumerState<_RequestCard> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final request = widget.request;
    final (bg, fg, label) = switch (request.status) {
      'PENDING' => (AppColors.primaryLight, AppColors.primary, 'Pending'),
      'ACCEPTED' => (AppColors.successTint, AppColors.success, 'Accepted'),
      'WAITING_LIST' => (AppColors.accentTint, AppColors.accentTextOnTint, 'Waiting list${request.waitlistPosition != null ? ' · #${request.waitlistPosition}' : ''}'),
      'REJECTED' => (AppColors.errorTint, AppColors.error, 'Declined'),
      _ => (AppColors.border, AppColors.textSecondary, request.status),
    };

    final applicantName = request.applicantDisplayName;
    final hasProfile = applicantName != null && applicantName.isNotEmpty;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(16)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: hasProfile
                      ? () => context.push('/users/${request.applicantId}/trust-reviews', extra: applicantName)
                      : null,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircleAvatar(
                        radius: 15,
                        backgroundColor: AppColors.primaryLight,
                        backgroundImage: request.applicantPhotoUrl != null ? NetworkImage(request.applicantPhotoUrl!) : null,
                        child: request.applicantPhotoUrl == null
                            ? Text(
                                hasProfile ? applicantName[0].toUpperCase() : '?',
                                style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600, fontSize: 12),
                              )
                            : null,
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          hasProfile ? applicantName : 'Traveller request',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (hasProfile) ...[
                        const SizedBox(width: 2),
                        const Icon(Icons.chevron_right, size: 16, color: AppColors.textTertiary),
                      ],
                    ],
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
                child: Text(label, style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: fg)),
              ),
            ],
          ),
          if (request.requestMessage != null && request.requestMessage!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(request.requestMessage!, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.5)),
          ],
          if (request.status == 'PENDING') ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _busy ? null : () => _decide(reject: true),
                    child: const Text('Decline'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _busy ? null : () => _decide(reject: false),
                    child: const Text('Accept'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _decide({required bool reject}) async {
    setState(() => _busy = true);
    try {
      final api = ref.read(joinRequestApiProvider);
      if (reject) {
        await api.reject(widget.request.id);
      } else {
        await api.accept(widget.request.id);
      }
      ref.invalidate(organizerQueueProvider(widget.tripId));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
