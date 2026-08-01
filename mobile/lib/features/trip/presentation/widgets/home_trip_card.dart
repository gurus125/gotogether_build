import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/reason_picker_sheet.dart';
import '../../../report/state/report_providers.dart';
import '../../../trust/state/trust_providers.dart';
import '../../data/trip_models.dart';
import '../../state/trip_providers.dart';
import 'trip_card.dart' show TripCoverImage;

/// Home's "Trips for you" / "Verified partner trips" carousel card, built to
/// a supplied visual spec (publisher header, gradient-overlaid destination
/// image, floating verified badge + wishlist heart, date/location rows,
/// joined-count + trust-score footer). Deliberately a *separate* widget from
/// `TripCard` (`trip_card.dart`) rather than a redesign of it in place — that
/// widget is also used full-width by Explore and at width:180 by the Company
/// dashboard, neither of which asked for this richer treatment, and forcing
/// them onto this layout wasn't part of the request. `_TripCardRow` in
/// `home_screen.dart` is the only call site.
///
/// A few things the spec assumed exist but don't, resolved here rather than
/// faked:
/// - **Organizer trust score**: not present on `TripSummary` at all (no
///   trip-list response anywhere joins `trust_scores`). Fetched per-card via
///   `trustBreakdownProvider(organizerId)` — an extra network call per
///   unique organizer, deduped by Riverpod's family cache across cards
///   sharing one organizer. If it fails or hasn't loaded, the trust text is
///   simply omitted rather than shown as a placeholder/zero.
/// - **Wishlist heart**: the backend's `save`/`unsave` endpoints are real and
///   already used by My Trips' Saved tab, but no response anywhere returns
///   an `isSaved` flag for a given trip — there's nothing to initialize the
///   heart's filled/unfilled state from. It renders as optimistic local
///   state only (fills on tap, calls the real endpoint, reverts on
///   failure) — it will NOT reflect a trip already saved from a previous
///   session until a real `isSaved` field exists on `TripSummary`.
/// - **Destination "subtitle"**: the spec's "Crystal Clear Beaches"-style
///   line isn't a real field — `DestinationSummary` has no tagline, only a
///   `category` enum (e.g. `BEACHES`). Title-cased and shown in its place
///   rather than invented copy.
/// - **"Delhi NCR → destination"**: Delhi NCR is the same hardcoded MVP
///   departure-city string already used on Home's own search card and
///   Explore's search bar (Chapter 1 Section 14: it's the only departure
///   city at MVP, not a missing feature) — not a new fabrication.
/// - **Report trip**: the backend `report` module (`POST /reports`) existed
///   but was wired to zero Flutter screens before this. The three-dot menu's
///   "Report trip" is the first real caller — reasons are mapped to the
///   backend's fixed `report_reason` enum (`FRAUD`/`INAPPROPRIATE_CONTENT`/
///   `SPAM`/`OTHER`), reusing the shared `showReasonPicker` sheet.
class HomeTripCard extends ConsumerStatefulWidget {
  const HomeTripCard({super.key, required this.trip});

  final TripSummary trip;

  @override
  ConsumerState<HomeTripCard> createState() => _HomeTripCardState();
}

class _HomeTripCardState extends ConsumerState<HomeTripCard> {
  bool _saved = false;

  @override
  Widget build(BuildContext context) {
    final trip = widget.trip;
    final trustAsync = ref.watch(trustBreakdownProvider(trip.organizerId));
    final trustLabel = trustAsync.valueOrNull?.currentScore.toStringAsFixed(1);

    final start = DateTime.tryParse(trip.startDate);
    final end = DateTime.tryParse(trip.endDate);
    final dateLabel = start != null && end != null
        ? '${DateFormat('MMM d').format(start)} – ${DateFormat('MMM d').format(end)} · ${end.difference(start).inDays + 1} Days'
        : '';

    return GestureDetector(
      onTap: () => context.push('/trip/${trip.id}'),
      child: Container(
        width: 216, // 180 * 1.2 — widened 20% per request
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.10), blurRadius: 14, offset: const Offset(0, 5))],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _PublisherHeader(trip: trip, trustLabel: trustLabel),
            _CoverSection(trip: trip, saved: _saved, onToggleSave: _toggleSave),
            _InfoSection(trip: trip, dateLabel: dateLabel, trustLabel: trustLabel),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleSave() async {
    final next = !_saved;
    setState(() => _saved = next);
    try {
      if (next) {
        await ref.read(tripApiProvider).save(widget.trip.id);
      } else {
        await ref.read(tripApiProvider).unsave(widget.trip.id);
      }
    } catch (_) {
      if (mounted) setState(() => _saved = !next);
    }
  }
}

class _PublisherHeader extends ConsumerWidget {
  const _PublisherHeader({required this.trip, required this.trustLabel});

  final TripSummary trip;
  final String? trustLabel;

  static const _reportPresets = {
    'Misleading or fake trip details': 'FRAUD',
    'Inappropriate content': 'INAPPROPRIATE_CONTENT',
    'Spam': 'SPAM',
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      height: 44,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: AppColors.primaryLight,
              backgroundImage: trip.organizerPhotoUrl != null ? NetworkImage(trip.organizerPhotoUrl!) : null,
              child: trip.organizerPhotoUrl == null
                  ? Text(trip.organizerDisplayName.isNotEmpty ? trip.organizerDisplayName[0].toUpperCase() : '?',
                      style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700, fontSize: 13))
                  : null,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(trip.organizerDisplayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
                  Text(
                    trustLabel != null ? 'Organizer · Trust $trustLabel' : 'Organizer',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w500, color: AppColors.textSecondary),
                  ),
                ],
              ),
            ),
            IconButton(
              onPressed: () => _showMenu(context, ref),
              icon: const Icon(Icons.more_vert, size: 18, color: AppColors.textTertiary),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showMenu(BuildContext context, WidgetRef ref) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => _CardMenuSheet(organizerName: trip.organizerDisplayName),
    );
    if (action == null || !context.mounted) return;
    if (action == 'profile') {
      context.push('/users/${trip.organizerId}/trust-reviews', extra: trip.organizerDisplayName);
    } else if (action == 'report') {
      await _reportTrip(context, ref);
    }
  }

  Future<void> _reportTrip(BuildContext context, WidgetRef ref) async {
    final result = await showReasonPicker(
      context,
      title: 'Report this trip?',
      subtitle: 'Our Trust & Safety team reviews every report filed against "${trip.title}".',
      presetReasons: _reportPresets.keys.toList(),
      confirmLabel: 'Submit report',
    );
    if (result == null || !context.mounted) return;
    final reason = _reportPresets[result] ?? 'OTHER';
    final details = _reportPresets.containsKey(result) ? null : result;
    try {
      await ref.read(reportApiProvider).create(entityType: 'TRIP', entityId: trip.id, reason: reason, details: details);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Report submitted — thanks for flagging this.')));
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }
}

class _CardMenuSheet extends StatelessWidget {
  const _CardMenuSheet({required this.organizerName});

  final String organizerName;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Container(
        decoration: const BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.person_outline, color: AppColors.textPrimary),
              title: Text("View $organizerName's profile", style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
              onTap: () => Navigator.of(context).pop('profile'),
            ),
            ListTile(
              leading: const Icon(Icons.flag_outlined, color: AppColors.error),
              title: const Text('Report trip', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: AppColors.error)),
              onTap: () => Navigator.of(context).pop('report'),
            ),
          ],
        ),
      ),
    );
  }
}

class _CoverSection extends StatelessWidget {
  const _CoverSection({required this.trip, required this.saved, required this.onToggleSave});

  final TripSummary trip;
  final bool saved;
  final VoidCallback onToggleSave;

  static const double _height = 110;

  static String _titleCase(String s) =>
      s.split('_').where((w) => w.isNotEmpty).map((w) => '${w[0].toUpperCase()}${w.substring(1).toLowerCase()}').join(' ');

  @override
  Widget build(BuildContext context) {
    final hasPhoto = trip.coverImageUrl != null;
    return SizedBox(
      height: _height,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (hasPhoto)
            Image.network(trip.coverImageUrl!, fit: BoxFit.cover)
          else
            // No photo: same solid-brand-blue-plus-destination-name treatment
            // as everywhere else in the app (`TripCoverImage`, shared with
            // `TripCard` and My Trips) — the spec's gradient/overlaid-text
            // treatment below only applies once there's an actual photo to
            // lay it over.
            TripCoverImage(imageUrl: null, destinationName: trip.destination.name, height: _height),
          if (hasPhoto) ...[
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: [Color(0x59000000), Colors.transparent], // ~35% opacity black, fading up
                ),
              ),
            ),
            Positioned(
              left: 10,
              right: 10,
              bottom: 8,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(trip.destination.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white)),
                  Text(_titleCase(trip.destination.category),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: Colors.white.withOpacity(0.9))),
                ],
              ),
            ),
          ],
          if (trip.kind == 'VERIFIED_PARTNER')
            Positioned(
              top: 6,
              left: 6,
              child: Container(
                height: 20,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(color: AppColors.success, borderRadius: BorderRadius.circular(100)),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.check, size: 11, color: Colors.white),
                    SizedBox(width: 3),
                    Text('VERIFIED', style: TextStyle(fontSize: 9, fontWeight: FontWeight.w700, color: Colors.white)),
                  ],
                ),
              ),
            ),
          Positioned(
            top: 2,
            right: 2,
            child: IconButton(
              onPressed: onToggleSave,
              icon: Icon(saved ? Icons.favorite : Icons.favorite_border, size: 19, color: Colors.white),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 30, minHeight: 30),
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoSection extends StatelessWidget {
  const _InfoSection({required this.trip, required this.dateLabel, required this.trustLabel});

  final TripSummary trip;
  final String dateLabel;
  final String? trustLabel;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              const Icon(Icons.calendar_today_outlined, size: 13, color: AppColors.textSecondary),
              const SizedBox(width: 6),
              Expanded(
                child: Text(dateLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.location_on_outlined, size: 13, color: AppColors.textPrimary),
              const SizedBox(width: 6),
              Expanded(
                child: Text('Delhi NCR → ${trip.destination.name}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          const Divider(height: 1, thickness: 1, color: AppColors.border),
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.people_outline, size: 13, color: AppColors.textSecondary),
              const SizedBox(width: 4),
              Flexible(
                child: Text('${trip.joinedCount}/${trip.maxGroupSize} Joined',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
              ),
              const Spacer(),
              if (trustLabel != null) ...[
                const Icon(Icons.verified_user_outlined, size: 13, color: AppColors.success),
                const SizedBox(width: 4),
                Text('Trust $trustLabel', style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: AppColors.success)),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
