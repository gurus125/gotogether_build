import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../data/trip_api.dart';
import '../data/trip_models.dart';
import '../state/trip_providers.dart';
import 'widgets/trip_card.dart';

/// The approved My Trips design: four tabs sharing one card component, each
/// adding only the context that matters for that tab (a "members confirmed"
/// progress bar on Upcoming, nothing extra on Created/Saved). Past now has
/// a real "Leave a review" action (Phase 5's `review` module) opening the
/// roster-based reviewee list — the mockup's own "you rated this trip"
/// per-review-status line still isn't shown, since there's no per-trip
/// "have I already reviewed this person" endpoint (see
/// `TripRevieweesScreen`'s doc). "Edit trip" (Created) now opens
/// `EditTripScreen` — "Manage Trip" (group size/meeting point/approval
/// settings) is real, see that screen's class doc — except when the trip has
/// left the editable window (`IN_PROGRESS` or terminal), where it stays a
/// disabled tap response instead of pushing a route the backend would just
/// reject. Trip *photo* management is separate (`TripPhotosScreen`), reachable
/// from Trip Details' organizer action bar. "Open chat" is now wired for real
/// (Phase 4).
class MyTripsScreen extends ConsumerStatefulWidget {
  const MyTripsScreen({super.key});

  @override
  ConsumerState<MyTripsScreen> createState() => _MyTripsScreenState();
}

class _MyTripsScreenState extends ConsumerState<MyTripsScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  static const _tabs = ['upcoming', 'past', 'created', 'saved'];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('My Trips'),
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.textPrimary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: const [
            Tab(text: 'Upcoming'),
            Tab(text: 'Past'),
            Tab(text: 'Created'),
            Tab(text: 'Saved'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: _tabs.map((tab) => _TripTab(tab: tab)).toList(),
      ),
    );
  }
}

class _TripTab extends ConsumerWidget {
  const _TripTab({required this.tab});

  final String tab;

  static const _empty = {
    'upcoming': (title: 'No upcoming trips', body: 'Trips you join or create will show up here.', cta: 'Explore trips'),
    'past': (title: 'No past trips yet', body: 'Completed trips will appear here.', cta: 'Explore trips'),
    'created': (title: "You haven't created a trip yet", body: 'Start one and invite travellers to join.', cta: '+ Create a trip'),
    'saved': (title: 'No saved trips', body: 'Tap the bookmark on any trip to save it for later.', cta: 'Explore trips'),
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tripsAsync = ref.watch(myTripsProvider(tab));

    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(myTripsProvider(tab)),
      child: tripsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ListView(children: const [
          SizedBox(height: 120),
          Center(child: Text('Could not load trips.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
        ]),
        data: (trips) {
          if (trips.isEmpty) {
            final empty = _empty[tab]!;
            return ListView(
              padding: const EdgeInsets.fromLTRB(24, 80, 24, 24),
              children: [
                Column(
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: AppColors.communityText.withOpacity(0.4), width: 2)),
                    ),
                    const SizedBox(height: 16),
                    Text(empty.title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                    const SizedBox(height: 6),
                    Text(empty.body, textAlign: TextAlign.center, style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.6)),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.accent),
                      onPressed: () => tab == 'created' ? context.go('/create') : context.go('/explore'),
                      child: Text(empty.cta),
                    ),
                  ],
                ),
              ],
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: trips.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (context, i) => _MyTripCard(trip: trips[i], tab: tab),
          );
        },
      ),
    );
  }
}

/// Cover photo is now 200px, matching Explore's full-width `TripCard`
/// exactly (both render one card per row at device width), and the no-photo
/// fallback is the shared `TripCoverImage` (solid brand-blue + destination
/// name) — previously this card used its own 96px height and a bare
/// placeholder icon, so a trip looked meaningfully different depending on
/// which tab you found it in.
class _MyTripCard extends ConsumerWidget {
  const _MyTripCard({required this.trip, required this.tab});

  final TripSummary trip;
  final String tab;

  /// Mirrors backend `TripService.requireEditable` — kept in sync with the
  /// identical set in `trip_details_screen.dart`'s `_OrganizerActionBar`.
  static const _nonEditableTripStatuses = {'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'ARCHIVED'};

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dateFormat = DateFormat('MMM d');
    final start = DateTime.tryParse(trip.startDate);
    final end = DateTime.tryParse(trip.endDate);
    final dateLabel = start != null && end != null ? '${dateFormat.format(start)}–${dateFormat.format(end)}' : '';
    final progressPct = trip.maxGroupSize > 0 ? (trip.joinedCount / trip.maxGroupSize).clamp(0.0, 1.0) : 0.0;

    return Container(
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(16)),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              TripCoverImage(imageUrl: trip.coverImageUrl, destinationName: trip.destination.name, height: 200),
              Positioned(
                top: 8,
                left: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                  decoration: BoxDecoration(color: AppColors.communityTint, borderRadius: BorderRadius.circular(5)),
                  child: Text(trip.status.replaceAll('_', ' '),
                      style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: AppColors.communityText)),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(trip.title, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)),
                const SizedBox(height: 3),
                Text('$dateLabel · ${trip.destination.name}', style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
                if (tab == 'upcoming') ...[
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('${trip.joinedCount} of ${trip.maxGroupSize} members', style: const TextStyle(fontSize: 9.5, color: AppColors.textSecondary)),
                      Text('${(progressPct * 100).round()}%', style: const TextStyle(fontSize: 9.5, color: AppColors.textSecondary)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(3),
                    child: LinearProgressIndicator(value: progressPct, minHeight: 5, backgroundColor: AppColors.border, color: AppColors.primary),
                  ),
                ],
                const SizedBox(height: 10),
                Row(children: _actionsFor(context, ref, tab)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _actionsFor(BuildContext context, WidgetRef ref, String tab) {
    Widget action({required String label, required VoidCallback? onTap, bool primary = false}) => Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 6),
            child: GestureDetector(
              onTap: onTap,
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 8),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: primary ? AppColors.primaryLight : AppColors.background,
                  borderRadius: BorderRadius.circular(100),
                ),
                child: Text(label,
                    style: TextStyle(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w500,
                        color: onTap == null ? AppColors.textTertiary : (primary ? AppColors.primary : AppColors.textSecondary))),
              ),
            ),
          ),
        );

    void notEditable() => ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('This trip can no longer be edited.')));
    void viewTrip() => context.push('/trip/${trip.id}');
    void openChat() => context.push('/trip/${trip.id}/chat');
    void editTrip() => context.push('/trip/${trip.id}/edit');

    switch (tab) {
      case 'upcoming':
        return [action(label: 'Open chat', onTap: openChat), action(label: 'View trip', onTap: viewTrip, primary: true)];
      case 'created':
        final isEditable = !_nonEditableTripStatuses.contains(trip.status);
        return [
          action(label: 'Edit trip', onTap: isEditable ? editTrip : notEditable),
          action(label: 'View trip', onTap: viewTrip, primary: true),
        ];
      case 'saved':
        return [
          action(label: 'View trip', onTap: viewTrip, primary: true),
          action(
            label: 'Remove',
            onTap: () async {
              await ref.read(tripApiProvider).unsave(trip.id);
              ref.invalidate(myTripsProvider('saved'));
            },
          ),
        ];
      case 'past':
        return [
          action(label: 'View trip', onTap: viewTrip),
          action(
            label: 'Leave a review',
            onTap: () => context.push('/trip/${trip.id}/reviewees', extra: trip.title),
            primary: true,
          ),
        ];
      default:
        return [action(label: 'View trip', onTap: viewTrip, primary: true)];
    }
  }
}
