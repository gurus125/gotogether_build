/// One Pexels search result — mirrors backend `storage.dto.PhotoSearchResultResponse`.
class PhotoSearchResult {
  const PhotoSearchResult({
    required this.id,
    this.thumbnailUrl,
    this.fullUrl,
    this.photographerName,
    this.photographerUrl,
  });

  factory PhotoSearchResult.fromJson(Map<String, dynamic> json) => PhotoSearchResult(
        id: json['id'] as String,
        // Backend maps these from Pexels' own `src.medium`/`src.large2x` via
        // Jackson's `asText(null)` — null on the rare malformed/missing-field
        // upstream response rather than an empty string, so `usable` below
        // can tell "no image" apart from "broken image" cleanly.
        thumbnailUrl: json['thumbnail_url'] as String?,
        fullUrl: json['full_url'] as String?,
        photographerName: json['photographer_name'] as String?,
        photographerUrl: json['photographer_url'] as String?,
      );

  final String id;
  final String? thumbnailUrl;
  final String? fullUrl;
  final String? photographerName;
  final String? photographerUrl;

  /// A result with no `fullUrl` can't actually be picked (nothing to
  /// download/upload) — filtered out before rendering rather than shown as
  /// a dead tile.
  bool get usable => fullUrl != null && fullUrl!.isNotEmpty;
}
