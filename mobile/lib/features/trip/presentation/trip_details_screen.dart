import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/reason_picker_sheet.dart';
import '../../joinrequest/data/join_request_models.dart';
import '../../joinrequest/state/join_request_providers.dart';
import '../../membership/state/membership_providers.dart';
import '../../user/state/user_providers.dart';
import '../data/trip_models.dart';
import '../state/trip_providers.dart';

/// The approved "Trip Details" design. Itinerary, Budget breakdown,
/// Included/Not included, Group preferences, Reviews, Safety information, and
/// Similar trips still depend on modules that don't exist yet (Reviews are
/// Phase 5; trip-management fields are post-publish-only and never collected
/// by the Quick Publish wizard) — see `TripDetailsResponse`'s doc. Hiding them
/// beats faking empty states. The members preview and the sticky join/leave
/// bar are real as of Phase 3 (`joinrequest`/`membership` modules).
class TripDetailsScreen extends ConsumerWidget {
  const TripDetailsScreen({super.key, required this.tripId});

  final String tripId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detailsAsync = ref.watch(tripDetailsProvider(tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      body: detailsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorView(onRetry: () => ref.invalidate(tripDetailsProvider(tripId))),
        data: (details) => _TripDetailsBody(tripId: tripId, details: details),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            GestureDetector(
              onTap: () => context.pop(),
              child: Align(
                alignment: Alignment.topLeft,
                child: Container(
                  width: 32,
                  height: 32,
                  decoration: const BoxDecoration(color: AppColors.surface, shape: BoxShape.circle),
                  child: const Icon(Icons.arrow_back, size: 16),
                ),
              ),
            ),
            const SizedBox(height: 40),
            const Text('This trip could not be loaded.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}

class _TripDetailsBody extends ConsumerWidget {
  const _TripDetailsBody({required this.tripId, required this.details});

  final String tripId;
  final TripDetailsResponse details;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trip = details.trip;
    final organizer = details.organizer;
    final currentUserAsync = ref.watch(currentUserProvider);
    final isOrganizer = currentUserAsync.maybeWhen(data: (u) => u.id == organizer.id, orElse: () => false);

    return Stack(
      children: [
        CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 240,
              pinned: true,
              backgroundColor: AppColors.surface,
              leading: Padding(
                padding: const EdgeInsets.all(8),
                child: GestureDetector(
                  onTap: () => context.pop(),
                  child: Container(
                    decoration: const BoxDecoration(color: Colors.white70, shape: BoxShape.circle),
                    child: const Icon(Icons.arrow_back, size: 16, color: Colors.black87),
                  ),
                ),
              ),
              flexibleSpace: FlexibleSpaceBar(
                background: trip.images.isNotEmpty
                    ? Image.network(trip.images.first.imageUrl, fit: BoxFit.cover)
                    : Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [AppColors.primaryLight, AppColors.communityTint],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                        ),
                        child: const Center(child: Icon(Icons.landscape_outlined, size: 48, color: Colors.white70)),
                      ),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(trip.title, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 20)),
                    const SizedBox(height: 6),
                    _TripSummaryRow(trip: trip),
                    const SizedBox(height: 18),
                    _OrganizerCard(organizer: organizer, companyId: trip.companyId),
                    const SizedBox(height: 18),
                    _TrustIndicators(organizer: organizer, isCompany: trip.companyId != null),
                    if (details.membersPreview.isNotEmpty) ...[
                      const SizedBox(height: 18),
                      _MembersPreviewSection(tripId: tripId, membersPreview: details.membersPreview),
                    ],
                    if (trip.description != null && trip.description!.isNotEmpty) ...[
                      const SizedBox(height: 18),
                      const Text('About this trip', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 6),
                      Text(trip.description!, style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary, height: 1.6)),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
        Align(
          alignment: Alignment.bottomCenter,
          child: isOrganizer
              ? _OrganizerActionBar(tripId: tripId, tripStatus: trip.status, tripTitle: trip.title)
              : _StickyJoinBar(tripId: tripId, trip: trip),
        ),
      ],
    );
  }
}

class _MembersPreviewSection extends StatelessWidget {
  const _MembersPreviewSection({required this.tripId, required this.membersPreview});

  final String tripId;
  final List<MemberPreview> membersPreview;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(
          child: SizedBox(
            height: 36,
            child: Stack(
              children: [
                for (var i = 0; i < membersPreview.length && i < 5; i++)
                  Positioned(
                    left: i * 24.0,
                    child: CircleAvatar(
                      radius: 16,
                      backgroundColor: AppColors.primaryLight,
                      backgroundImage: membersPreview[i].photoUrl != null ? NetworkImage(membersPreview[i].photoUrl!) : null,
                      child: membersPreview[i].photoUrl == null
                          ? Text(
                              membersPreview[i].displayName.isNotEmpty ? membersPreview[i].displayName[0].toUpperCase() : '?',
                              style: const TextStyle(fontSize: 11, color: AppColors.primary, fontWeight: FontWeight.w600),
                            )
                          : null,
                    ),
                  ),
              ],
            ),
          ),
        ),
        TextButton(
          onPressed: () => context.push('/trip/$tripId/members'),
          child: const Text('View all', style: TextStyle(fontSize: 12)),
        ),
      ],
    );
  }
}

/// The organizer's own view of their trip's sticky bar — replaces the
/// applicant Join/Withdraw flow with a shortcut into the un-mocked "manage
/// requests" queue (see `organizer_requests_screen.dart`'s class doc).
///
/// Once the trip is Completed, "Manage requests" (moot — nobody can join a
/// finished trip) is replaced with "Mark attendance", the entry point into
/// `AttendanceScreen`. That screen only has anything to show once
/// `TripLifecycleScheduler` (or the manual complete path) has actually run —
/// see that class's doc for why trip completion itself was unreachable
/// before this was built.
///
/// The edit (pencil) icon opens `EditTripScreen` — "Manage Trip" (group
/// size/meeting point/approval settings) now actually exists, see that
/// screen's class doc. Hidden once the trip is no longer editable
/// (`IN_PROGRESS` or a terminal status), mirroring backend
/// `TripService.requireEditable`'s own rule, so this bar never offers an
/// action the backend would just reject.
///
/// The cancel (X-circle) icon opens `TripApi.cancel` — previously wired all
/// the way through the backend (mandatory reason, member notification fan-
/// out) and even present in the Flutter data layer (`TripApi.cancel`), but
/// never actually reachable from any screen. Shown for any non-terminal
/// status (mirrors backend `Trip.isTerminal`/`TripService.cancel`'s own
/// check) — deliberately a wider set than the edit pencil's, since a trip
/// `IN_PROGRESS` can still be cancelled, just not edited. Uses the shared
/// `showReasonPicker` sheet so the mandatory reason is fast to fill instead
/// of a bare text box.
class _OrganizerActionBar extends ConsumerStatefulWidget {
  const _OrganizerActionBar({required this.tripId, required this.tripStatus, required this.tripTitle});

  final String tripId;
  final String tripStatus;
  final String tripTitle;

  @override
  ConsumerState<_OrganizerActionBar> createState() => _OrganizerActionBarState();
}

class _OrganizerActionBarState extends ConsumerState<_OrganizerActionBar> {
  static const _nonEditableStatuses = {'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'ARCHIVED'};
  static const _nonCancellableStatuses = {'COMPLETED', 'CANCELLED', 'ARCHIVED'};

  static const _cancelReasons = [
    'Not enough travellers joined',
    'Change of plans',
    'Weather or safety concern',
    'Destination no longer available',
    'Personal emergency',
  ];

  bool _cancelling = false;

  @override
  Widget build(BuildContext context) {
    final isCompleted = widget.tripStatus == 'COMPLETED';
    final isEditable = !_nonEditableStatuses.contains(widget.tripStatus);
    final isCancellable = !_nonCancellableStatuses.contains(widget.tripStatus);
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 20),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: Row(
        children: [
          if (isEditable) ...[
            IconButton(
              onPressed: () => context.push('/trip/${widget.tripId}/edit'),
              icon: const Icon(Icons.edit_outlined),
              style: IconButton.styleFrom(
                backgroundColor: AppColors.background,
                side: const BorderSide(color: AppColors.border),
              ),
            ),
            const SizedBox(width: 8),
          ],
          IconButton(
            onPressed: () => context.push('/trip/${widget.tripId}/photos'),
            icon: const Icon(Icons.photo_library_outlined),
            style: IconButton.styleFrom(
              backgroundColor: AppColors.background,
              side: const BorderSide(color: AppColors.border),
            ),
          ),
          if (isCancellable) ...[
            const SizedBox(width: 8),
            IconButton(
              onPressed: _cancelling ? null : _cancelTrip,
              icon: _cancelling
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.error))
                  : const Icon(Icons.cancel_outlined, color: AppColors.error),
              style: IconButton.styleFrom(
                backgroundColor: AppColors.errorTint,
                side: const BorderSide(color: AppColors.error),
              ),
            ),
          ],
          const SizedBox(width: 8),
          Expanded(
            child: OutlinedButton(
              onPressed: () => context.push('/trip/${widget.tripId}/chat'),
              child: const Text('Open chat'),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: ElevatedButton(
              onPressed: () => context.push(isCompleted ? '/trip/${widget.tripId}/attendance' : '/trip/${widget.tripId}/requests'),
              child: Text(isCompleted ? 'Mark attendance' : 'Manage requests'),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _cancelTrip() async {
    final reason = await showReasonPicker(
      context,
      title: 'Cancel this trip?',
      subtitle: 'Every traveller on "${widget.tripTitle}" will be notified this trip has been cancelled, with your name attached. This can\'t be undone.',
      presetReasons: _cancelReasons,
      confirmLabel: 'Cancel trip',
    );
    if (reason == null || !mounted) return;
    setState(() => _cancelling = true);
    try {
      await ref.read(tripApiProvider).cancel(widget.tripId, reason);
      ref.invalidate(tripDetailsProvider(widget.tripId));
      ref.invalidate(myTripsProvider('created'));
      ref.invalidate(myTripsProvider('upcoming'));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Trip cancelled — travellers have been notified.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }
}

class _TripSummaryRow extends StatelessWidget {
  const _TripSummaryRow({required this.trip});

  final TripDetails trip;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('MMM d');
    final start = DateTime.tryParse(trip.startDate);
    final end = DateTime.tryParse(trip.endDate);
    final dateLabel = start != null && end != null ? '${dateFormat.format(start)}–${dateFormat.format(end)}' : '';
    final priceLabel = trip.fixedPrice != null
        ? '₹${trip.fixedPrice}'
        : (trip.budgetMin != null ? '~₹${trip.budgetMin}${trip.budgetMax != null ? '–${trip.budgetMax}' : '+'}' : '');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('$dateLabel · ${trip.destination.name}', style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
        const SizedBox(height: 6),
        Row(
          children: [
            _StatusChip(status: trip.status),
            if (priceLabel.isNotEmpty) ...[
              const SizedBox(width: 8),
              Text(priceLabel, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500)),
              const Text(' est. per person', style: TextStyle(fontSize: 10.5, color: AppColors.textTertiary)),
            ],
          ],
        ),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final (bg, fg, label) = switch (status) {
      'PUBLISHED' => (AppColors.primaryLight, AppColors.primary, 'OPEN'),
      'ACCEPTING_REQUESTS' => (AppColors.primaryLight, AppColors.primary, 'OPEN'),
      'CONFIRMED' => (AppColors.successTint, AppColors.success, 'CONFIRMED'),
      'FULL' => (AppColors.accentTint, AppColors.accentTextOnTint, 'FULL'),
      'CANCELLED' => (AppColors.errorTint, AppColors.error, 'CANCELLED'),
      'COMPLETED' => (AppColors.communityTint, AppColors.communityText, 'COMPLETED'),
      _ => (AppColors.border, AppColors.textSecondary, status),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
      child: Text(label, style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: fg)),
    );
  }
}

class _OrganizerCard extends StatelessWidget {
  const _OrganizerCard({required this.organizer, this.companyId});

  final OrganizerSummary organizer;

  /// Phase 7: non-null exactly when this is a Verified Partner Trip —
  /// `organizer` is then already the Company's own branding (see
  /// `TripSummary.companyId`'s doc), so tapping opens the Company Profile
  /// screen instead of an individual's Trust+Reviews view. Getting this
  /// wrong would show a traveller a named employee's personal trust
  /// history, exactly what Operations Module A's "Organizer assignment"
  /// rule says must never happen.
  final String? companyId;

  @override
  Widget build(BuildContext context) {
    final isCompany = companyId != null;
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      // Individual organizer -> Trust+Reviews view (Phase 5). Company
      // organizer -> the public Company Profile (Phase 7) — see
      // `TrustReviewsScreen`'s class doc for what the individual view
      // deliberately doesn't show.
      onTap: () => isCompany
          ? context.push('/companies/$companyId', extra: organizer.displayName)
          : context.push('/users/${organizer.id}/trust-reviews', extra: organizer.displayName),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          children: [
            CircleAvatar(
              radius: 20,
              backgroundColor: AppColors.primaryLight,
              backgroundImage: organizer.photoUrl != null ? NetworkImage(organizer.photoUrl!) : null,
              child: organizer.photoUrl == null
                  ? Text(organizer.displayName.isNotEmpty ? organizer.displayName[0].toUpperCase() : '?',
                      style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600))
                  : null,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    isCompany ? organizer.displayName : 'Hosted by ${organizer.displayName}',
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 2),
                  if (isCompany)
                    const Text('Verified Travel Partner', style: TextStyle(fontSize: 10.5, color: AppColors.success))
                  else if (organizer.idVerified)
                    const Text('ID Verified', style: TextStyle(fontSize: 10.5, color: AppColors.success)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, size: 18, color: AppColors.textTertiary),
            // "Message" is intentionally absent — Chat is a Phase 4 module and
            // doesn't exist yet (Chapter 1 Section 21's Chat lifecycle: chat
            // only unlocks after Join Request Accepted).
          ],
        ),
      ),
    );
  }
}

class _TrustIndicators extends StatelessWidget {
  const _TrustIndicators({required this.organizer, this.isCompany = false});

  final OrganizerSummary organizer;
  final bool isCompany;

  @override
  Widget build(BuildContext context) {
    if (!organizer.idVerified) return const SizedBox.shrink();
    final label = isCompany ? 'Verified Partner' : 'ID Verified';
    return Wrap(
      spacing: 8,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(color: AppColors.successTint, borderRadius: BorderRadius.circular(100)),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.verified, size: 13, color: AppColors.success),
              const SizedBox(width: 4),
              Text(label, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w500, color: AppColors.success)),
            ],
          ),
        ),
      ],
    );
  }
}

/// The applicant-facing sticky CTA — real as of Phase 3. Reads
/// `joinStatusProvider` (not `TripDetailsResponse.joinStatus`, which is a
/// plain status string) because the withdraw action needs the request's own
/// id, only present on the dedicated `GET /trips/{id}/join-status` response.
class _StickyJoinBar extends ConsumerStatefulWidget {
  const _StickyJoinBar({required this.tripId, required this.trip});

  final String tripId;
  final TripDetails trip;

  @override
  ConsumerState<_StickyJoinBar> createState() => _StickyJoinBarState();
}

class _StickyJoinBarState extends ConsumerState<_StickyJoinBar> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final statusAsync = ref.watch(joinStatusProvider(widget.tripId));

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 20),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: statusAsync.when(
        loading: () => const SizedBox(
          width: double.infinity,
          child: ElevatedButton(onPressed: null, child: SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))),
        ),
        error: (e, _) => SizedBox(
          width: double.infinity,
          child: OutlinedButton(
            onPressed: () => ref.invalidate(joinStatusProvider(widget.tripId)),
            child: const Text('Could not load — tap to retry'),
          ),
        ),
        data: (status) => _buildForStatus(context, status),
      ),
    );
  }

  Widget _buildForStatus(BuildContext context, JoinStatusResponse status) {
    final joinLabel = widget.trip.kind == 'VERIFIED_PARTNER' ? 'Book your spot' : 'Request to join';
    final closed = const ['FULL', 'CANCELLED', 'COMPLETED', 'ARCHIVED'].contains(widget.trip.status);

    switch (status.status) {
      case JoinStatusResponse.notRequested:
      case 'WITHDRAWN':
      case 'EXPIRED':
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: (_busy || closed) ? null : _requestToJoin,
            child: Text(closed ? '$joinLabel — trip closed' : joinLabel),
          ),
        );
      case 'PENDING':
        return Row(
          children: [
            const Expanded(
              child: Text('Request pending', textAlign: TextAlign.center, style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
            ),
            TextButton(onPressed: _busy ? null : () => _withdraw(status), child: const Text('Withdraw')),
          ],
        );
      case 'WAITING_LIST':
        return Row(
          children: [
            Expanded(
              child: Text(
                'On waiting list${status.waitlistPosition != null ? ' · #${status.waitlistPosition}' : ''}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary),
              ),
            ),
            TextButton(onPressed: _busy ? null : () => _withdraw(status), child: const Text('Withdraw')),
          ],
        );
      case 'ACCEPTED':
        return Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () => context.push('/trip/${widget.tripId}/chat'),
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_circle, size: 15, color: AppColors.success),
                    SizedBox(width: 6),
                    Text("You're in! Open chat", style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                  ],
                ),
              ),
            ),
            TextButton(onPressed: _busy ? null : _leave, child: const Text('Leave trip')),
          ],
        );
      case 'REJECTED':
        final onCooldown = status.canReapplyAt != null && DateTime.tryParse(status.canReapplyAt!)?.isAfter(DateTime.now()) == true;
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: (_busy || onCooldown || closed) ? null : _requestToJoin,
            child: Text(onCooldown ? 'Declined — can reapply later' : joinLabel),
          ),
        );
      default:
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(onPressed: null, child: Text(status.status)),
        );
    }
  }

  Future<void> _requestToJoin() async {
    setState(() => _busy = true);
    try {
      await ref.read(joinRequestApiProvider).create(widget.tripId);
      ref.invalidate(joinStatusProvider(widget.tripId));
      ref.invalidate(tripDetailsProvider(widget.tripId));
    } catch (e) {
      _showError(e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _withdraw(JoinStatusResponse status) async {
    final joinRequestId = status.joinRequestId;
    if (joinRequestId == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(joinRequestApiProvider).withdraw(joinRequestId);
      ref.invalidate(joinStatusProvider(widget.tripId));
      ref.invalidate(tripDetailsProvider(widget.tripId));
    } catch (e) {
      _showError(e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _leave() async {
    setState(() => _busy = true);
    try {
      await ref.read(membershipApiProvider).leave(widget.tripId);
      ref.invalidate(joinStatusProvider(widget.tripId));
      ref.invalidate(tripDetailsProvider(widget.tripId));
    } catch (e) {
      _showError(e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showError(Object e) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
  }
}
