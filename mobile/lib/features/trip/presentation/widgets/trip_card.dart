import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../core/theme/app_colors.dart';
import '../../data/trip_models.dart';

/// Cover image for a trip card — shared by [TripCard] and My Trips' own
/// card (`my_trips_screen.dart`'s `_MyTripCard`) so a trip with no uploaded
/// `coverImageUrl` renders identically everywhere: a solid GoTogether-blue
/// panel with the destination name on it, instead of a bare placeholder
/// icon that gave no information and read as broken/unfinished. Raised from
/// this file (rather than a new `core/widgets` file) since `TripCard` is
/// already the trip-card home and `_MyTripCard` already imports from this
/// `widgets/` folder's sibling — see that class's own doc for why it isn't
/// just reusing `TripCard` wholesale (it needs a status badge + progress bar
/// + action row `TripCard` doesn't have).
class TripCoverImage extends StatelessWidget {
  const TripCoverImage({super.key, required this.imageUrl, required this.destinationName, required this.height});

  final String? imageUrl;
  final String destinationName;
  final double height;

  @override
  Widget build(BuildContext context) {
    if (imageUrl != null) {
      return Image.network(imageUrl!, height: height, width: double.infinity, fit: BoxFit.cover);
    }
    return Container(
      height: height,
      width: double.infinity,
      color: AppColors.primary,
      alignment: Alignment.center,
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Text(
        destinationName,
        textAlign: TextAlign.center,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: Colors.white, letterSpacing: 0.2),
      ),
    );
  }
}

/// The Trip Card component reused across Home ("Trips for you" / "Verified
/// partner trips") and Explore results, per the Design System's own
/// component-reuse note (Home Screen design Section 4).
class TripCard extends StatelessWidget {
  const TripCard({super.key, required this.trip, this.partner = false, this.width = 210});

  final TripSummary trip;
  final bool partner;
  final double width;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('MMM d');
    final start = DateTime.tryParse(trip.startDate);
    final end = DateTime.tryParse(trip.endDate);
    final dateLabel = start != null && end != null ? '${dateFormat.format(start)}–${dateFormat.format(end)}' : '';

    // Explore's vertical list passes width: double.infinity (a near-full-
    // device-width card); Home's horizontal row and the Company dashboard
    // pass a fixed ~180-210. A single 88px image height read fine at the
    // narrow width (roughly 2:1) but badly letterboxed/cropped the photo at
    // full device width (nearly 4.5:1) — that's what was reported here.
    // Only bumping it for the wide case, not globally, since the narrow
    // cards' total height is already tightly tuned against a fixed
    // SizedBox(height: 190) elsewhere (home_screen.dart) — see that file's
    // comment on the RenderFlex overflow this same card caused earlier.
    final bool isFullWidth = !width.isFinite;
    final double imageHeight = isFullWidth ? 200 : 88;

    return GestureDetector(
      onTap: () => context.push('/trip/${trip.id}'),
      child: Container(
        width: width,
        decoration: BoxDecoration(
          color: partner ? const Color(0xFFFAFAFA) : AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(16),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            TripCoverImage(imageUrl: trip.coverImageUrl, destinationName: trip.destination.name, height: imageHeight),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (partner)
                    const Text('VERIFIED PARTNER',
                        style: TextStyle(fontSize: 8.5, fontWeight: FontWeight.w600, letterSpacing: 0.4, color: AppColors.textPrimary))
                  else
                    Text(trip.destination.name.toUpperCase(),
                        style: const TextStyle(fontSize: 8.5, fontWeight: FontWeight.w600, color: AppColors.communityText)),
                  const SizedBox(height: 4),
                  Text(trip.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
                  const SizedBox(height: 3),
                  // This meta line (dates + capacity/price) is core decision
                  // info, not decorative caption text — was textTertiary
                  // (previously 3.32:1 contrast, below WCAG AA) which read as
                  // "too light" per the design-fidelity report. textSecondary
                  // matches the design's clearly-legible medium-grey meta text.
                  Text(
                    partner
                        ? dateLabel + (trip.fixedPrice != null ? ' · ₹${trip.fixedPrice}' : '')
                        : '$dateLabel · ${trip.joinedCount} of ${trip.maxGroupSize} joined',
                    style: const TextStyle(fontSize: 9.5, color: AppColors.textSecondary),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 7,
                        backgroundColor: AppColors.primaryLight,
                        backgroundImage: trip.organizerPhotoUrl != null ? NetworkImage(trip.organizerPhotoUrl!) : null,
                      ),
                      const SizedBox(width: 5),
                      Expanded(
                        child: Text(
                          partner
                              ? trip.organizerDisplayName
                              : '${trip.organizerDisplayName}${trip.organizerVerified ? ' · Verified' : ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 9.5, color: AppColors.textSecondary),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
