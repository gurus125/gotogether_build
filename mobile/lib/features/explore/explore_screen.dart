import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_colors.dart';
import '../destination/data/destination_models.dart';
import '../destination/state/destination_providers.dart';
import '../trip/data/trip_models.dart';
import '../trip/presentation/widgets/trip_card.dart';
import '../trip/state/trip_providers.dart';

/// Carries the Home Screen search card's picks (destination + date range)
/// into Explore's initial results, via `GoRouterState.extra` (same pattern
/// `app_router.dart` already uses for `SubmitReviewScreen`/`TrustReviewsScreen`).
/// Kept as an in-memory object, not a query string, since both screens live
/// in the same isolate — no need to serialize a `DestinationSummary`.
class ExploreSearchArgs {
  const ExploreSearchArgs({this.destination, this.dateRange});

  final DestinationSummary? destination;
  final DateTimeRange? dateRange;
}

/// The approved Explore design: search/filter engine over trips, not a
/// second Home feed. Age range / Gender pref. / Minimum organizer trust score
/// sliders are shown (matching the approved filter sheet) but are
/// deliberately inert — `GET /explore` has no query parameters for them and
/// Chapter 4's declared-preference matching is still undefined (flagged
/// during the Phase 2 docs review, confirmed with the product owner
/// 2026-07-22 to build the UI now and wire it once that's specified).
class ExploreScreen extends ConsumerStatefulWidget {
  const ExploreScreen({super.key, this.initialArgs});

  final ExploreSearchArgs? initialArgs;

  @override
  ConsumerState<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends ConsumerState<ExploreScreen> {
  DestinationSummary? _destination;
  DateTimeRange? _dateRange;
  String _sort = 'best_match';
  Future<CursorPage<TripSummary>>? _resultsFuture;

  static const _sortLabels = {
    'best_match': 'Best Match',
    'newest': 'Newest',
    'leaving_soon': 'Leaving Soon',
    'lowest_budget': 'Lowest Budget',
  };

  @override
  void initState() {
    super.initState();
    _destination = widget.initialArgs?.destination;
    _dateRange = widget.initialArgs?.dateRange;
    _runSearch();
  }

  void _runSearch() {
    final dateFmt = DateFormat('yyyy-MM-dd');
    setState(() {
      _resultsFuture = ref.read(tripApiProvider).explore(
            destinationId: _destination?.id,
            dateFrom: _dateRange != null ? dateFmt.format(_dateRange!.start) : null,
            dateTo: _dateRange != null ? dateFmt.format(_dateRange!.end) : null,
            sort: _sort,
          );
    });
  }

  Future<void> _pickDateRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: now,
      lastDate: now.add(const Duration(days: 365)),
      initialDateRange: _dateRange,
    );
    if (picked != null) {
      setState(() => _dateRange = picked);
      _runSearch();
    }
  }

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
              const Text('Destination', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
              const SizedBox(height: 12),
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Any destination', style: TextStyle(fontSize: 13)),
                onTap: () => Navigator.pop(context, null),
              ),
              ...destinations.map((d) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(d.name, style: const TextStyle(fontSize: 13)),
                    onTap: () => Navigator.pop(context, d),
                  )),
            ],
          ),
        ),
      ),
    );
    setState(() => _destination = picked);
    _runSearch();
  }

  Future<void> _pickSort() async {
    final picked = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: _sortLabels.entries
              .map((e) => ListTile(title: Text(e.value, style: const TextStyle(fontSize: 13)), onTap: () => Navigator.pop(context, e.key)))
              .toList(),
        ),
      ),
    );
    if (picked != null) {
      setState(() => _sort = picked);
      _runSearch();
    }
  }

  void _openFilters() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => const _AdvancedFiltersSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  GestureDetector(
                    onTap: _pickDestination,
                    child: Container(
                      height: 44,
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        border: Border.all(color: AppColors.border),
                        borderRadius: BorderRadius.circular(100),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.search, size: 16, color: AppColors.textTertiary),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _destination?.name ?? 'Search destination (from Delhi NCR)',
                              style: TextStyle(
                                  fontSize: 12.5,
                                  color: _destination != null ? AppColors.textPrimary : AppColors.textTertiary),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        GestureDetector(
                          onTap: _openFilters,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(color: AppColors.textPrimary, borderRadius: BorderRadius.circular(100)),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.tune, size: 13, color: Colors.white),
                                SizedBox(width: 5),
                                Text('Filters', style: TextStyle(fontSize: 11.5, color: Colors.white, fontWeight: FontWeight.w500)),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        GestureDetector(
                          onTap: _pickDateRange,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: AppColors.surface,
                              border: Border.all(color: _dateRange != null ? AppColors.primary : AppColors.border),
                              borderRadius: BorderRadius.circular(100),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.calendar_today_outlined,
                                    size: 12, color: _dateRange != null ? AppColors.primary : AppColors.textSecondary),
                                const SizedBox(width: 5),
                                Text(
                                  _dateRange == null
                                      ? 'Dates'
                                      : '${DateFormat('MMM d').format(_dateRange!.start)} – ${DateFormat('MMM d').format(_dateRange!.end)}',
                                  style: TextStyle(
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w500,
                                      color: _dateRange != null ? AppColors.primary : AppColors.textSecondary),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  FutureBuilder<CursorPage<TripSummary>>(
                    future: _resultsFuture,
                    builder: (context, snapshot) => Text(
                      snapshot.hasData ? '${snapshot.data!.items.length} trips found' : ' ',
                      style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary),
                    ),
                  ),
                  GestureDetector(
                    onTap: _pickSort,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(_sortLabels[_sort]!, style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500)),
                        const Icon(Icons.expand_more, size: 16),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: FutureBuilder<CursorPage<TripSummary>>(
                future: _resultsFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (snapshot.hasError) {
                    return Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text('Could not load trips.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
                          const SizedBox(height: 8),
                          OutlinedButton(onPressed: _runSearch, child: const Text('Retry')),
                        ],
                      ),
                    );
                  }
                  final items = snapshot.data?.items ?? [];
                  if (items.isEmpty) {
                    return Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text('No trips match right now.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
                          const SizedBox(height: 8),
                          TextButton(
                            onPressed: () {
                              setState(() {
                                _destination = null;
                                _dateRange = null;
                                _sort = 'best_match';
                              });
                              _runSearch();
                            },
                            child: const Text('Clear filters'),
                          ),
                        ],
                      ),
                    );
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    itemCount: items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, i) => TripCard(trip: items[i], width: double.infinity),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdvancedFiltersSheet extends StatelessWidget {
  const _AdvancedFiltersSheet();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Filters', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
            const Text('Age range', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
            const SizedBox(height: 4),
            IgnorePointer(
              child: Opacity(
                opacity: 0.5,
                child: Slider(value: 0.5, onChanged: (_) {}),
              ),
            ),
            const Text('Gender preference', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
            const SizedBox(height: 4),
            IgnorePointer(
              child: Opacity(
                opacity: 0.5,
                child: DropdownButton<String>(
                  isExpanded: true,
                  value: 'any',
                  items: const [DropdownMenuItem(value: 'any', child: Text('Any (optional)'))],
                  onChanged: (_) {},
                ),
              ),
            ),
            const SizedBox(height: 12),
            const Text('Minimum organizer trust score', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
            IgnorePointer(
              child: Opacity(
                opacity: 0.5,
                child: Slider(value: 0.0, onChanged: (_) {}),
              ),
            ),
            Container(
              margin: const EdgeInsets.only(top: 8, bottom: 16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: AppColors.primaryTint, borderRadius: BorderRadius.circular(12)),
              child: const Text(
                'Age range, gender preference, and minimum trust score filtering are coming in a future update — not wired up yet.',
                style: TextStyle(fontSize: 11, color: AppColors.primary, height: 1.5),
              ),
            ),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
            ),
          ],
        ),
      ),
    );
  }
}
