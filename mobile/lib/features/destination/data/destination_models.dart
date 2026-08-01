/// Mirrors `com.gotogether.destination.dto.DestinationSummary` (Destination
/// APIs, API Specification Section 5) — read-only, no create/update/delete
/// endpoint exists (destinations are platform-curated seed data).
class DestinationSummary {
  const DestinationSummary({
    required this.id,
    required this.name,
    required this.category,
    this.coverImageUrl,
  });

  factory DestinationSummary.fromJson(Map<String, dynamic> json) => DestinationSummary(
        id: json['id'] as String,
        name: json['name'] as String,
        category: json['category'] as String,
        coverImageUrl: json['cover_image_url'] as String?,
      );

  final String id;
  final String name;

  /// One of `MOUNTAINS` / `BEACHES` / `WEEKEND_ESCAPES` / `ADVENTURE` (see
  /// backend `DestinationCategory` — enum *values* serialize as the Java
  /// constant name, uppercase, even though JSON *field names* are snake_case).
  final String category;
  final String? coverImageUrl;
}
