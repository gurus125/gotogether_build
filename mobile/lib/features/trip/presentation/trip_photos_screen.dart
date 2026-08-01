import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../data/trip_models.dart';
import '../state/trip_providers.dart';

/// "Manage Trip Photos" — a new screen with no approved design mockup
/// backing it (the 7-step "Quick Publish" Create Trip wizard has no photo
/// step). Kept separate from `EditTripScreen` ("Manage Trip" proper — group
/// size/meeting point/approval settings, see that screen's class doc) rather
/// than folded in, since photo upload is a fundamentally different flow
/// (presigned-URL + picker) than a text/toggle form. Scoped deliberately narrow: add and
/// delete photos only. The first photo uploaded to an empty gallery is
/// auto-marked as the cover (`is_primary`); there's no way to change the
/// cover of an existing gallery afterwards, because the backend only
/// supports `POST` (add) and `DELETE` — no `PATCH /trips/{id}/images/{id}`
/// to re-designate an existing image as primary. Flagged here rather than
/// silently building a "Set as cover" button that would have nothing to call.
///
/// "Add photo" now has a sibling, "Search photos" (`PhotoSearchScreen`,
/// `/trip/:id/photos/search`) — an organizer without their own photo of a
/// destination can search Pexels' stock library and attach a result
/// instead of only ever picking from their camera roll. Both tiles feed
/// the exact same `is_primary`/first-photo logic below (`images.isEmpty`),
/// so which one is used doesn't change that behaviour.
class TripPhotosScreen extends ConsumerStatefulWidget {
  const TripPhotosScreen({super.key, required this.tripId});

  final String tripId;

  @override
  ConsumerState<TripPhotosScreen> createState() => _TripPhotosScreenState();
}

class _TripPhotosScreenState extends ConsumerState<TripPhotosScreen> {
  bool _uploading = false;
  String? _deletingImageId;

  Future<void> _addPhoto(List<TripImage> existing) async {
    setState(() => _uploading = true);
    try {
      final tripApi = ref.read(tripApiProvider);
      final url = await ref.read(imageUploadServiceProvider).pickAndUpload(
            requestUploadUrl: (contentType) => tripApi.getImageUploadUrl(widget.tripId, contentType),
          );
      if (url == null) return; // user backed out of the picker
      await tripApi.addImage(widget.tripId, url, existing.isEmpty);
      ref.invalidate(tripDetailsProvider(widget.tripId));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _deletePhoto(TripImage image) async {
    setState(() => _deletingImageId = image.id);
    try {
      await ref.read(tripApiProvider).deleteImage(widget.tripId, image.id);
      ref.invalidate(tripDetailsProvider(widget.tripId));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _deletingImageId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detailsAsync = ref.watch(tripDetailsProvider(widget.tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Trip photos')),
      body: detailsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Could not load this trip.\n$e', textAlign: TextAlign.center)),
        data: (details) {
          final images = details.trip.images;
          return GridView.builder(
            padding: const EdgeInsets.all(16),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: 10,
              crossAxisSpacing: 10,
              childAspectRatio: 1,
            ),
            itemCount: images.length + 2,
            itemBuilder: (context, index) {
              if (index == images.length) {
                return _AddPhotoTile(uploading: _uploading, onTap: _uploading ? null : () => _addPhoto(images));
              }
              if (index == images.length + 1) {
                return _SearchPhotoTile(
                  onTap: () => context.push('/trip/${widget.tripId}/photos/search', extra: images.isEmpty),
                );
              }
              final image = images[index];
              return _PhotoTile(
                image: image,
                deleting: _deletingImageId == image.id,
                onDelete: () => _deletePhoto(image),
              );
            },
          );
        },
      ),
    );
  }
}

class _PhotoTile extends StatelessWidget {
  const _PhotoTile({required this.image, required this.deleting, required this.onDelete});

  final TripImage image;
  final bool deleting;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: Stack(
        fit: StackFit.expand,
        children: [
          CachedNetworkImage(imageUrl: image.imageUrl, fit: BoxFit.cover),
          if (image.primary)
            Positioned(
              left: 6,
              top: 6,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(100)),
                child: const Text('Cover', style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: Colors.white)),
              ),
            ),
          Positioned(
            right: 6,
            top: 6,
            child: GestureDetector(
              onTap: deleting ? null : onDelete,
              child: Container(
                width: 26,
                height: 26,
                decoration: const BoxDecoration(color: Color(0x99000000), shape: BoxShape.circle),
                child: deleting
                    ? const Padding(
                        padding: EdgeInsets.all(5),
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.close, size: 15, color: Colors.white),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SearchPhotoTile extends StatelessWidget {
  const _SearchPhotoTile({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(14),
        ),
        child: const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.travel_explore_outlined, color: AppColors.textSecondary),
              SizedBox(height: 6),
              Text('Search photos', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
            ],
          ),
        ),
      ),
    );
  }
}

class _AddPhotoTile extends StatelessWidget {
  const _AddPhotoTile({required this.uploading, required this.onTap});

  final bool uploading;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Center(
          child: uploading
              ? const CircularProgressIndicator(strokeWidth: 2)
              : const Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.add_photo_alternate_outlined, color: AppColors.textSecondary),
                    SizedBox(height: 6),
                    Text('Add photo', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
                  ],
                ),
        ),
      ),
    );
  }
}
