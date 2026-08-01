import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_colors.dart';
import '../destination/data/destination_models.dart';
import '../destination/state/destination_providers.dart';
import '../explore/explore_screen.dart';
import '../notification/state/notification_providers.dart';
import '../profile/state/profile_providers.dart';
import '../trip/data/trip_models.dart';
import '../trip/presentation/widgets/home_trip_card.dart';
import '../trip/state/trip_providers.dart';

/// The approved Home Screen design (v3, "trip-discovery-first"): search card
/// → Trips for you → Verified partner trips → Popular destinations → Create
/// CTA. Trust score / reputation tier isn't shown in the greeting (unlike the
/// design's "Trust score 92 · Community Leader" line) — the `trust` module
/// doesn't exist until Phase 5, so there's no real score to display yet;
/// showing a fabricated number would undermine the exact "trust-first"
/// premise the product is built on. The "Continue planning" draft-resume
/// card is also omitted — nothing persists an abandoned wizard draft
/// server-side or locally yet (Chapter 3: drafts auto-expire after 14 days,
/// but there's no "list my drafts" endpoint to resume from on Home).
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileProvider);
    final recommendedAsync = ref.watch(recommendedTripsProvider);
    final partnerAsync = ref.watch(verifiedPartnerTripsProvider);
    final destinationsAsync = ref.watch(featuredDestinationsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(recommendedTripsProvider);
            ref.invalidate(verifiedPartnerTripsProvider);
            ref.invalidate(featuredDestinationsProvider);
          },
          child: ListView(
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('GoTogether', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // No mockup shows a notification entry point anywhere
                        // in the approved design set (checked Home Screen
                        // specifically). Placing a bell icon here is a
                        // pragmatic, undocumented UI decision for Phase 6 —
                        // flagged to the user, not silently invented.
                        Stack(
                          clipBehavior: Clip.none,
                          children: [
                            IconButton(
                              onPressed: () => context.push('/notifications'),
                              icon: const Icon(Icons.notifications_none_rounded, size: 24, color: AppColors.textPrimary),
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(minWidth: 34, minHeight: 34),
                              visualDensity: VisualDensity.compact,
                            ),
                            if (ref.watch(hasUnreadNotificationsProvider).valueOrNull == true)
                              Positioned(
                                right: 4,
                                top: 4,
                                child: Container(
                                  width: 8,
                                  height: 8,
                                  decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(width: 4),
                        profileAsync.maybeWhen(
                          data: (profile) => CircleAvatar(
                            radius: 17,
                            backgroundColor: AppColors.primaryLight,
                            backgroundImage: profile.photoUrl != null ? NetworkImage(profile.photoUrl!) : null,
                            child: profile.photoUrl == null
                                ? Text(profile.displayName.isNotEmpty ? profile.displayName[0].toUpperCase() : '?',
                                    style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600, fontSize: 13))
                                : null,
                          ),
                          orElse: () => const SizedBox(width: 34, height: 34),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
                child: profileAsync.maybeWhen(
                  data: (profile) => Text(
                    _greeting(profile.displayName),
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontSize: 22, letterSpacing: -0.2),
                  ),
                  orElse: () => const SizedBox(height: 28),
                ),
              ),
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 14, 16, 0),
                child: _SearchCard(),
              ),
              _SectionHeader(title: 'Trips for you', onSeeAll: () => context.go('/explore')),
              _TripCardRow(tripsAsync: recommendedAsync),
              _SectionHeader(title: 'Verified partner trips', onSeeAll: () => context.go('/explore')),
              _TripCardRow(tripsAsync: partnerAsync),
              _SectionHeader(title: 'Popular destinations', onSeeAll: () => context.go('/explore')),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: destinationsAsync.when(
                  loading: () => const SizedBox(height: 84, child: Center(child: CircularProgressIndicator())),
                  error: (e, _) => const SizedBox.shrink(),
                  data: (destinations) => GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                    childAspectRatio: 2.2,
                    children: destinations.map((d) => _DestinationTile(destination: d)).toList(),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 0),
                child: Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(color: AppColors.accentTint, borderRadius: BorderRadius.circular(16)),
                  child: Column(
                    children: [
                      const Text("Can't find the right trip?",
                          style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: AppColors.accentTextOnTint)),
                      const SizedBox(height: 4),
                      const Text('Create your own and invite travellers to join.',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 11, color: AppColors.accentTextOnTint)),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: AppColors.accent),
                        onPressed: () => context.go('/create'),
                        child: const Text('+ Create your own trip'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _greeting(String name) {
    final hour = DateTime.now().hour;
    final firstName = name.split(' ').first;
    if (hour < 12) return 'Good morning, $firstName';
    if (hour < 17) return 'Good afternoon, $firstName';
    return 'Good evening, $firstName';
  }
}

/// The Home Screen's search card (approved design's "03 · Home" screen).
/// Previously all three fields — including DATES — were static, non-tappable
/// text baked into one card-wide `GestureDetector` that just opened Explore
/// regardless of which field was tapped; DATES always showed the hardcoded
/// string "Anytime" and never reflected anything the traveller picked. Fixed:
/// TO opens a destination picker and DATES opens a real
/// `showDateRangePicker`, each updating its own field's displayed value
/// (matching the design's "Sep 12 – Sep 22" style date range), and "Search
/// trips" forwards the picked destination/dates into Explore's initial
/// results rather than discarding them. FROM stays locked/non-interactive —
/// Delhi NCR is the only departure city at MVP (Chapter 1 Section 14), not a
/// missing feature.
class _SearchCard extends ConsumerStatefulWidget {
  const _SearchCard();

  @override
  ConsumerState<_SearchCard> createState() => _SearchCardState();
}

class _SearchCardState extends ConsumerState<_SearchCard> {
  DestinationSummary? _destination;
  DateTimeRange? _dateRange;

  Future<void> _pickDestination() async {
    final destinations = await ref.read(destinationApiProvider).list();
    if (!mounted) return;
    final picked = await showModalBottomSheet<DestinationSummary?>(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Where to?', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Anywhere', style: TextStyle(fontSize: 13, color: AppColors.textPrimary)),
                onTap: () => Navigator.pop(context, null),
              ),
              ...destinations.map((d) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(d.name, style: const TextStyle(fontSize: 13, color: AppColors.textPrimary)),
                    onTap: () => Navigator.pop(context, d),
                  )),
            ],
          ),
        ),
      ),
    );
    if (!mounted) return;
    setState(() => _destination = picked);
  }

  Future<void> _pickDates() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: now,
      lastDate: now.add(const Duration(days: 365)),
      initialDateRange: _dateRange,
    );
    if (picked != null) setState(() => _dateRange = picked);
  }

  void _search() {
    // `go`, not `push` — `/explore` is a bottom-nav branch inside the
    // `StatefulShellRoute`, and every other Home entry point that switches to
    // it (`_SectionHeader.onSeeAll`, `_DestinationTile.onTap`) already uses
    // `context.go` for that reason. `go_router` still threads `extra` through
    // to `GoRouterState.extra` on a `go`, not just a `push`.
    context.go(
      '/explore',
      extra: ExploreSearchArgs(destination: _destination, dateRange: _dateRange),
    );
  }

  String get _datesLabel {
    final range = _dateRange;
    if (range == null) return 'Anytime';
    final fmt = DateFormat('MMM d');
    return '${fmt.format(range.start)} – ${fmt.format(range.end)}';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        border: Border.all(color: AppColors.border),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        children: [
          Row(
            children: [
              const Expanded(child: _SearchField(label: 'FROM', value: 'Delhi NCR', locked: true)),
              const SizedBox(width: 8),
              Expanded(
                child: _SearchField(
                  label: 'TO',
                  value: _destination?.name ?? 'Anywhere',
                  onTap: _pickDestination,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _SearchField(label: 'DATES', value: _datesLabel, onTap: _pickDates),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(onPressed: _search, child: const Text('Search trips')),
          ),
        ],
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  const _SearchField({required this.label, required this.value, this.onTap, this.locked = false});

  final String label;
  final String value;
  final VoidCallback? onTap;
  final bool locked;

  @override
  Widget build(BuildContext context) {
    final field = Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(10)),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: AppColors.textSecondary)),
                const SizedBox(height: 2),
                Text(
                  value,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
                ),
              ],
            ),
          ),
          if (locked) const Icon(Icons.lock_outline, size: 13, color: AppColors.textTertiary),
        ],
      ),
    );
    if (onTap == null) return field;
    return GestureDetector(onTap: onTap, behavior: HitTestBehavior.opaque, child: field);
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title, required this.onSeeAll});

  final String title;
  final VoidCallback onSeeAll;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 22, 16, 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
          GestureDetector(
            onTap: onSeeAll,
            child: const Text('See all', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.primary)),
          ),
        ],
      ),
    );
  }
}

class _TripCardRow extends StatelessWidget {
  const _TripCardRow({required this.tripsAsync});

  final AsyncValue<CursorPage<TripSummary>> tripsAsync;

  @override
  Widget build(BuildContext context) {
    // HomeTripCard's own sections sum to roughly 250px (44 header + 110
    // image + ~95 info area) — 260 leaves a small safety margin rather than
    // matching that exactly, since text line-height can't be measured
    // without actually running Flutter in this environment (see that
    // widget's own doc). Any slack just becomes a few px of blank space at
    // the bottom of a card, not an overflow.
    return SizedBox(
      height: 260,
      child: tripsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: Text('Could not load trips.', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
        ),
        data: (page) {
          if (page.items.isEmpty) {
            return const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Text('No matches yet — try widening your dates.', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            );
          }
          return ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: page.items.length,
            separatorBuilder: (_, __) => const SizedBox(width: 10),
            itemBuilder: (context, i) => HomeTripCard(trip: page.items[i]),
          );
        },
      ),
    );
  }
}

class _DestinationTile extends StatelessWidget {
  const _DestinationTile({required this.destination});

  final DestinationSummary destination;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => context.go('/explore', extra: ExploreSearchArgs(destination: destination)),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.primaryLight,
          borderRadius: BorderRadius.circular(12),
          image: destination.coverImageUrl != null
              ? DecorationImage(image: NetworkImage(destination.coverImageUrl!), fit: BoxFit.cover)
              : null,
        ),
        padding: const EdgeInsets.all(10),
        alignment: Alignment.bottomLeft,
        child: Text(destination.name,
            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
      ),
    );
  }
}
