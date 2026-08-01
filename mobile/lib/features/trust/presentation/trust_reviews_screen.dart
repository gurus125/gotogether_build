import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../review/data/review_models.dart';
import '../../review/state/review_providers.dart';
import '../data/trust_models.dart';
import '../state/trust_providers.dart';

/// Other traveller's Trust + Reviews view — matches `profilev1.pdf` /
/// `GoTogether Profile Screen-print.dc.html`'s "OTHER TRAVELLER'S PROFILE"
/// state's Trust Score and Reviews cards only. Per the user's explicit scope
/// decision, everything else that card shows (verification badges, the
/// tagline bio, "Travel stats", "Trip history", "About", "Badges & Active
/// trips") is deliberately NOT built here — it would need a `GET
/// /users/{id}` aggregating endpoint (badges, per-trip history, another
/// user's profile fields) that doesn't exist, plus a badge-awarding system
/// that doesn't exist either. Building a fake version of those would
/// undermine the trust-first premise, so they're skipped rather than faked.
///
/// The mockup's six sub-bars (Behaviour/Punctuality/Communication/
/// Cooperation/Safety/Reliability) are NOT `TrustScoreComponents` — that
/// record holds the seven weighted Trust Score *inputs* (reviews/completion/
/// verification/organizer/reportsPenalty/accountActivity/
/// profileCompleteness), a completely different breakdown. The six bars are
/// per-dimension averages across this user's own published reviews, which
/// only `ReviewResponse` exposes (each review's six raw 1-5 ratings) — no
/// backend endpoint computes these per-dimension averages, so they're
/// computed here from the fetched review page. That means they reflect only
/// the most recent page of reviews (default 20), not a true lifetime
/// average — acceptable at MVP data volumes, flagged here rather than
/// silently presented as exact.
class TrustReviewsScreen extends ConsumerWidget {
  const TrustReviewsScreen({super.key, required this.userId, required this.displayName});

  final String userId;
  final String displayName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trustAsync = ref.watch(trustBreakdownProvider(userId));
    final reviewsAsync = ref.watch(publishedReviewsProvider(userId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text(displayName)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _TrustScoreCard(trustAsync: trustAsync, reviewsAsync: reviewsAsync),
          const SizedBox(height: 12),
          _ReviewsCard(reviewsAsync: reviewsAsync),
        ],
      ),
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
      child: child,
    );
  }
}

const _subDimensions = [
  ('Behaviour', _SubDim.behaviour),
  ('Punctuality', _SubDim.punctuality),
  ('Communication', _SubDim.communication),
  ('Cooperation', _SubDim.cooperation),
  ('Safety', _SubDim.safety),
  ('Reliability', _SubDim.reliability),
];

enum _SubDim { behaviour, punctuality, communication, cooperation, safety, reliability }

class _TrustScoreCard extends StatelessWidget {
  const _TrustScoreCard({required this.trustAsync, required this.reviewsAsync});

  final AsyncValue<TrustScoreResponse> trustAsync;
  final AsyncValue<List<ReviewResponse>> reviewsAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: trustAsync.when(
        loading: () => const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: LinearProgressIndicator()),
        error: (e, _) => const Text('Could not load trust score.', style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
        data: (trust) {
          final reviews = reviewsAsync.valueOrNull ?? const <ReviewResponse>[];
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Icon(Icons.star_rounded, color: AppColors.accent, size: 20),
                  const SizedBox(width: 6),
                  Text(trust.currentScore.toStringAsFixed(1), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
                  const Padding(
                    padding: EdgeInsets.only(bottom: 3, left: 3),
                    child: Text('/ 10 Trust Score', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              if (reviews.isEmpty)
                const Text(
                  "No reviews yet — they'll appear after this traveller's first completed trip.",
                  style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary),
                )
              else ...[
                for (final dim in _subDimensions) ...[
                  _SubScoreBar(label: dim.$1, score: _averageFor(reviews, dim.$2)),
                  const SizedBox(height: 8),
                ],
                const SizedBox(height: 2),
                Text(
                  'Calculated from verified feedback across ${_distinctTripCount(reviews)} completed trip${_distinctTripCount(reviews) == 1 ? '' : 's'}.',
                  style: const TextStyle(fontSize: 10.5, color: AppColors.textTertiary),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  static double _averageFor(List<ReviewResponse> reviews, _SubDim dim) {
    final values = reviews.map((r) => switch (dim) {
          _SubDim.behaviour => r.ratingBehaviour,
          _SubDim.punctuality => r.ratingPunctuality,
          _SubDim.communication => r.ratingCommunication,
          _SubDim.cooperation => r.ratingCooperation,
          _SubDim.safety => r.ratingSafety,
          _SubDim.reliability => r.ratingReliability,
        });
    final avg = values.reduce((a, b) => a + b) / values.length;
    return avg * 2; // rescale 1-5 -> 0-10, matching the mockup's "9.x"-style values.
  }

  static int _distinctTripCount(List<ReviewResponse> reviews) => reviews.map((r) => r.tripId).toSet().length;
}

class _SubScoreBar extends StatelessWidget {
  const _SubScoreBar({required this.label, required this.score});

  final String label;
  final double score;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(width: 108, child: Text(label, style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary))),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: (score / 10).clamp(0.0, 1.0),
              minHeight: 6,
              backgroundColor: AppColors.border,
              color: AppColors.primary,
            ),
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(width: 28, child: Text(score.toStringAsFixed(1), style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600))),
      ],
    );
  }
}

class _ReviewsCard extends StatelessWidget {
  const _ReviewsCard({required this.reviewsAsync});

  final AsyncValue<List<ReviewResponse>> reviewsAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Reviews', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          reviewsAsync.when(
            loading: () => const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2))),
            error: (e, _) => const Text('Could not load reviews.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
            data: (reviews) {
              if (reviews.isEmpty) {
                return const Text(
                  "No reviews yet — they'll appear after this traveller's first completed trip.",
                  style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary),
                );
              }
              final traits = reviews.first.highlightedTraits;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (traits.isNotEmpty) ...[
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: traits
                          .map((t) => Container(
                                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                                decoration: BoxDecoration(color: AppColors.successTint, borderRadius: BorderRadius.circular(100)),
                                child: Text(t, style: const TextStyle(fontSize: 10.5, color: AppColors.successTextOnTint, fontWeight: FontWeight.w500)),
                              ))
                          .toList(),
                    ),
                    const SizedBox(height: 12),
                  ],
                  for (final review in reviews.take(10)) ...[
                    _ReviewSnippet(review: review),
                    const SizedBox(height: 10),
                  ],
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ReviewSnippet extends StatelessWidget {
  const _ReviewSnippet({required this.review});

  final ReviewResponse review;

  @override
  Widget build(BuildContext context) {
    if (review.comment == null || review.comment!.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('"${review.comment}"', style: const TextStyle(fontSize: 11.5, height: 1.5, color: AppColors.textPrimary)),
        const SizedBox(height: 3),
        Text('— ${review.reviewerDisplayName ?? 'A fellow traveller'}', style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
      ],
    );
  }
}
