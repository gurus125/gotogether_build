import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../trip/state/trip_providers.dart';
import '../data/photo_search_models.dart';
import '../state/photo_search_providers.dart';

/// "Search photos" — the second way to attach a trip cover/gallery photo,
/// alongside the existing gallery upload (`_AddPhotoTile` in
/// `trip_photos_screen.dart`). Searches Pexels via the backend's proxy
/// endpoint (`GET /photos/search` — the API key never reaches this app),
/// then downloads whichever result is picked and pushes it through the
/// exact same presigned-upload pipeline a gallery photo already uses
/// (`ImageUploadService.uploadBytes`) — nothing about how the photo is
/// stored differs from a gallery pick once one's chosen; only the source of
/// the bytes does. Reached via `/trip/:id/photos/search`, not a raw
/// `Navigator.push` — this app's own established convention (see
/// `app_router.dart`).
class PhotoSearchScreen extends ConsumerStatefulWidget {
  const PhotoSearchScreen({super.key, required this.tripId, required this.isFirstPhoto});

  final String tripId;

  /// Mirrors `_addPhoto`'s own `existing.isEmpty` check in
  /// `trip_photos_screen.dart` — the first photo added to an empty gallery
  /// is auto-marked cover (`is_primary`), so this needs to travel with the
  /// route rather than be re-derived here (this screen never loads the trip's
  /// existing image list itself).
  final bool isFirstPhoto;

  @override
  ConsumerState<PhotoSearchScreen> createState() => _PhotoSearchScreenState();
}

class _PhotoSearchScreenState extends ConsumerState<PhotoSearchScreen> {
  final _controller = TextEditingController();
  List<PhotoSearchResult>? _results;
  bool _searching = false;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) return;
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final results = await ref.read(photoSearchApiProvider).search(query);
      if (!mounted) return;
      setState(() => _results = results.where((r) => r.usable).toList());
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _pick(PhotoSearchResult result) async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      // A bare Dio, not the app's shared ApiClient — this is a request
      // straight to Pexels' own CDN, a different host entirely, and must
      // not carry this app's Authorization/JWT header (same reasoning as
      // the presigned upload PUT itself — see ImageUploadService).
      final response = await Dio().get<List<int>>(
        result.fullUrl!,
        options: Options(responseType: ResponseType.bytes),
      );
      final bytes = Uint8List.fromList(response.data!);
      final contentType = _contentTypeFrom(response.headers.value('content-type'));

      final tripApi = ref.read(tripApiProvider);
      final url = await ref.read(imageUploadServiceProvider).uploadBytes(
            bytes: bytes,
            contentType: contentType,
            requestUploadUrl: (ct) => tripApi.getImageUploadUrl(widget.tripId, ct),
          );
      await tripApi.addImage(widget.tripId, url, widget.isFirstPhoto);
      ref.invalidate(tripDetailsProvider(widget.tripId));
      if (!mounted) return;
      context.pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// Matches `StorageService.ALLOWED_IMAGE_CONTENT_TYPES` (jpeg/png/webp) —
  /// Pexels always serves jpeg in practice, but this reads the CDN's actual
  /// response header rather than assuming, falling back to jpeg only if the
  /// header is missing or outside the allowed set.
  String _contentTypeFrom(String? header) {
    const allowed = {'image/jpeg', 'image/png', 'image/webp'};
    final normalized = header?.split(';').first.trim().toLowerCase();
    return allowed.contains(normalized) ? normalized! : 'image/jpeg';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Search photos')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
            child: TextField(
              controller: _controller,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _search(),
              decoration: InputDecoration(
                hintText: 'Search a destination — "Andaman", "Manali"…',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searching
                    ? const Padding(
                        padding: EdgeInsets.all(14),
                        child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                      )
                    : IconButton(icon: const Icon(Icons.arrow_forward), onPressed: _search),
                filled: true,
                fillColor: AppColors.surface,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppColors.border)),
              ),
            ),
          ),
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 4, 16, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('Photos via Pexels', style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w500, color: AppColors.textTertiary)),
            ),
          ),
          Expanded(child: _buildBody()),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(_error!, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
        ),
      );
    }
    final results = _results;
    if (results == null) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Search for a destination to find a photo.',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
        ),
      );
    }
    if (results.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('No photos found — try a different search.',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
        ),
      );
    }
    return Stack(
      children: [
        GridView.builder(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 1,
          ),
          itemCount: results.length,
          itemBuilder: (context, i) => _ResultTile(result: results[i], onTap: () => _pick(results[i])),
        ),
        if (_saving)
          Container(
            color: Colors.black.withOpacity(0.25),
            child: const Center(child: CircularProgressIndicator(color: Colors.white)),
          ),
      ],
    );
  }
}

class _ResultTile extends StatelessWidget {
  const _ResultTile({required this.result, required this.onTap});

  final PhotoSearchResult result;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (result.thumbnailUrl != null) Image.network(result.thumbnailUrl!, fit: BoxFit.cover) else Container(color: AppColors.primaryLight),
            if (result.photographerName != null)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                  decoration: const BoxDecoration(color: Color(0x99000000)),
                  child: Text(
                    result.photographerName!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w500, color: Colors.white),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
