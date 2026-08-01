import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../data/trust_models.dart';
import '../state/trust_providers.dart';

/// Trust Score breakdown — full detail on exactly how the score is built,
/// not just a flat tip list (that's what this screen used to be; see git
/// history / earlier session notes). Every number and threshold shown here
/// mirrors backend `TrustService`'s actual formula 1:1 — the six weighted
/// components and their real weights (Reviews 40%, Trip completion 20%,
/// Verification 15%, Organizer reliability 10%, Account age & activity 10%,
/// Profile completeness 5%), the Reports & safety penalty (a direct
/// subtraction, not a seventh weighted slice — see `TrustService`'s
/// `WEIGHT_*` constants and `recalculate`'s doc), and the five Trust Level
/// bands (`TrustLevel.forScore`: Excellent 9.0+, Good 7.5+, Building 6.0+,
/// Caution 4.0+, else Restricted-triggering). Nothing here is invented —
/// where the copy explains *why* a component moved, it's describing the real
/// mechanic (e.g. completion counts a graceful leave at 90% credit, zero
/// credit for removed/late-left/no-show — see `TrustService.completionComponent`).
///
/// "Recent activity" surfaces `GET /users/me/trust-score/history` — built in
/// Phase 5 but never wired to any screen until now (see
/// `trustHistoryProvider`'s doc).
///
/// "Trust Improvement Tips" is a static, always-shown, one-tip-per-component
/// section (see `_improvementTips`) — separate from "Personalized for you"
/// (backend `improvementTips`, only rendered when non-empty). The backend
/// list only ever covers 4 of the 6 weighted components (reviews/completion/
/// verification/profileCompleteness — see `TrustService.buildImprovementTips`)
/// and is empty outright for a brand-new account with no computed components
/// yet, so it can't be the only source of advice on this screen. The Reviews
/// tip explicitly names all six rating dimensions a reviewer actually scores
/// (`ReviewResponse.ratingBehaviour`/`ratingPunctuality`/`ratingCommunication`/
/// `ratingCooperation`/`ratingSafety`/`ratingReliability` — see that DTO) so
/// the advice is concrete, not generic.
/// True for a brand-new account whose `trust_scores` row was only ever
/// lazily seeded (`TrustService.ensureRow`) and never actually recalculated
/// — every component is genuinely `null`, not zero (see
/// `TrustScoreComponents`' class doc). Drives `_UnscoredBanner` so this
/// reads as "nothing has happened yet" rather than looking broken.
bool _allUnscored(TrustScoreComponents c) =>
    c.reviews == null &&
    c.completion == null &&
    c.verification == null &&
    c.organizer == null &&
    c.accountActivity == null &&
    c.profileCompleteness == null;

class TrustTipsScreen extends ConsumerWidget {
  const TrustTipsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trustAsync = ref.watch(myTrustScoreProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Trust Score')),
      body: trustAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Could not load your Trust Score.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: () => ref.invalidate(myTrustScoreProvider), child: const Text('Retry')),
              ],
            ),
          ),
        ),
        data: (trust) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _ScoreHeader(trust: trust),
            if (_allUnscored(trust.components)) ...[
              const SizedBox(height: 10),
              const _UnscoredBanner(),
            ],
            const SizedBox(height: 20),
            const _SectionLabel('HOW YOUR SCORE IS CALCULATED'),
            const SizedBox(height: 8),
            _ComponentsCard(components: trust.components),
            const SizedBox(height: 16),
            const _SectionLabel('REPORTS & SAFETY'),
            const SizedBox(height: 8),
            _ReportsPenaltyCard(penalty: trust.components.reportsPenalty),
            const SizedBox(height: 20),
            const _SectionLabel('TRUST IMPROVEMENT TIPS'),
            const SizedBox(height: 8),
            const _ImprovementTipsCard(),
            if (trust.improvementTips.isNotEmpty) ...[
              const SizedBox(height: 20),
              const _SectionLabel('PERSONALIZED FOR YOU'),
              const SizedBox(height: 8),
              _TipsCard(tips: trust.improvementTips),
            ],
            const SizedBox(height: 20),
            const _SectionLabel('RECENT ACTIVITY'),
            const SizedBox(height: 8),
            const _HistoryCard(),
          ],
        ),
      ),
    );
  }
}

// --- score header --------------------------------------------------------

class _ScoreHeader extends StatelessWidget {
  const _ScoreHeader({required this.trust});

  final TrustScoreResponse trust;

  @override
  Widget build(BuildContext context) {
    final (label, fg, bg, blurb) = _levelInfo(trust.level);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(18)),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const Icon(Icons.star_rounded, color: AppColors.accent, size: 26),
              const SizedBox(width: 6),
              Text(trust.currentScore.toStringAsFixed(1), style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w800, height: 1)),
              const Padding(
                padding: EdgeInsets.only(bottom: 5, left: 4),
                child: Text('/ 10', style: TextStyle(fontSize: 14, color: AppColors.textSecondary)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
            decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(100)),
            child: Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: fg)),
          ),
          const SizedBox(height: 10),
          Text(
            blurb,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textPrimary, height: 1.6),
          ),
        ],
      ),
    );
  }

  /// Mirrors backend `TrustLevel.forScore` exactly — same five bands, same
  /// cutoffs (9.0 / 7.5 / 6.0 / 4.0).
  static (String, Color, Color, String) _levelInfo(String level) => switch (level) {
        'EXCELLENT' => ('Excellent · 9.0+', AppColors.successTextOnTint, AppColors.successTint,
            "You're among the most trusted travellers on GoTogether. Keep it up."),
        'GOOD' => ('Good · 7.5 – 8.9', AppColors.successTextOnTint, AppColors.successTint,
            'A solid, trustworthy track record. A few more completed trips or reviews could push you into Excellent.'),
        'BUILDING' => ('Building · 6.0 – 7.4', AppColors.accentTextOnTint, AppColors.accentTint,
            "You're still building trust on the platform — normal for newer accounts. See the tips below to grow faster."),
        'CAUTION' => ('Caution · 4.0 – 5.9', AppColors.error, AppColors.errorTint,
            'Your score is below where most travellers sit. Organizers may see this before accepting your join requests.'),
        _ => ('Restricted-triggering · Below 4.0', AppColors.error, AppColors.errorTint,
            'Your score is low enough to restrict some platform actions. Focus on the tips below to recover it.'),
      };
}

class _UnscoredBanner extends StatelessWidget {
  const _UnscoredBanner();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.primaryTint, borderRadius: BorderRadius.circular(14)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.info_outline, size: 16, color: AppColors.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              "Your account is new, so every component below still reads Not yet scored — that's expected, not an error. "
              'Your first completed trip, review, verification step, or profile update will trigger your first real '
              'calculation.',
              style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.primary, height: 1.6),
            ),
          ),
        ],
      ),
    );
  }
}

// --- component breakdown --------------------------------------------------

class _Component {
  const _Component({required this.title, required this.weightPercent, required this.value, required this.description});

  final String title;
  final int weightPercent;
  final double? value;
  final String description;
}

/// Order matches backend `TrustService`'s `WEIGHT_*` constants exactly.
List<_Component> _componentsFor(TrustScoreComponents c) => [
      _Component(
        title: 'Reviews from travellers',
        weightPercent: 40,
        value: c.reviews,
        description: 'Ratings from people you\'ve actually completed trips with, across six dimensions — behaviour, '
            'punctuality, communication, cooperation, safety, and reliability. The single biggest factor in your score.',
      ),
      _Component(
        title: 'Trip completion behaviour',
        weightPercent: 20,
        value: c.completion,
        description: 'How reliably you finish trips you join. Completing a trip counts fully; leaving gracefully with '
            "notice counts at 90%. Getting removed, leaving late, or being marked a no-show earns no credit at all — "
            "even though the trip itself still shows as completed.",
      ),
      _Component(
        title: 'Verification level',
        weightPercent: 15,
        value: c.verification,
        description: 'Your identity verification — phone, email, and government ID. Each step up moves this component '
            'closer to full marks.',
      ),
      _Component(
        title: 'Organizer reliability',
        weightPercent: 10,
        value: c.organizer,
        description: "If you organize trips: how quickly and consistently you respond to join requests. Sits at a "
            "neutral score until you've organized at least one trip — it's not a penalty for never organizing.",
      ),
      _Component(
        title: 'Account age & activity',
        weightPercent: 10,
        value: c.accountActivity,
        description: "How long you've been part of the GoTogether community, up to a two-year cap. This grows on its "
            'own over time — nothing to actively do here.',
      ),
      _Component(
        title: 'Profile completeness',
        weightPercent: 5,
        value: c.profileCompleteness,
        description: 'Whether your bio, photo, and travel preferences are filled in. The smallest weight, but also the '
            'easiest to max out in a couple of minutes.',
      ),
    ];

class _ComponentsCard extends StatelessWidget {
  const _ComponentsCard({required this.components});

  final TrustScoreComponents components;

  @override
  Widget build(BuildContext context) {
    final list = _componentsFor(components);
    return _Card(
      child: Column(
        children: [
          for (var i = 0; i < list.length; i++) ...[
            _ComponentRow(component: list[i]),
            if (i < list.length - 1)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(height: 1, color: AppColors.background),
              ),
          ],
        ],
      ),
    );
  }
}

class _ComponentRow extends StatelessWidget {
  const _ComponentRow({required this.component});

  final _Component component;

  @override
  Widget build(BuildContext context) {
    final value = component.value;
    final (statusLabel, statusColor, statusBg) = _band(value);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Row(
                children: [
                  Flexible(
                    child: Text(component.title, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600), overflow: TextOverflow.ellipsis),
                  ),
                  const SizedBox(width: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(color: AppColors.border, borderRadius: BorderRadius.circular(5)),
                    child: Text('${component.weightPercent}%', style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              value == null ? '—' : value.toStringAsFixed(1),
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(3),
          child: LinearProgressIndicator(
            value: value == null ? 0 : (value / 10).clamp(0.0, 1.0),
            minHeight: 6,
            backgroundColor: AppColors.background,
            color: statusColor,
          ),
        ),
        const SizedBox(height: 6),
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
              decoration: BoxDecoration(color: statusBg, borderRadius: BorderRadius.circular(100)),
              child: Text(statusLabel, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: statusColor)),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(component.description, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textPrimary, height: 1.6)),
      ],
    );
  }

  /// Same 0-10 scale and cutoffs as the overall Trust Score bands
  /// (`TrustLevel.forScore`), applied per-component so the vocabulary stays
  /// consistent throughout the app rather than inventing a second one.
  static (String, Color, Color) _band(double? value) {
    if (value == null) return ('Not yet scored', AppColors.textSecondary, AppColors.border);
    if (value >= 9.0) return ('Excellent', AppColors.successTextOnTint, AppColors.successTint);
    if (value >= 7.5) return ('Good', AppColors.successTextOnTint, AppColors.successTint);
    if (value >= 6.0) return ('Building', AppColors.accentTextOnTint, AppColors.accentTint);
    if (value >= 4.0) return ('Caution', AppColors.error, AppColors.errorTint);
    return ('Needs attention', AppColors.error, AppColors.errorTint);
  }
}

// --- reports & safety ------------------------------------------------------

class _ReportsPenaltyCard extends StatelessWidget {
  const _ReportsPenaltyCard({required this.penalty});

  final double? penalty;

  @override
  Widget build(BuildContext context) {
    final hasPenalty = penalty != null && penalty! < 0;
    return _Card(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(color: hasPenalty ? AppColors.errorTint : AppColors.successTint, shape: BoxShape.circle),
            alignment: Alignment.center,
            child: Icon(hasPenalty ? Icons.report_gmailerrorred : Icons.verified_user_outlined,
                size: 17, color: hasPenalty ? AppColors.error : AppColors.success),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  hasPenalty ? '${penalty!.toStringAsFixed(1)} points from confirmed reports' : 'No penalties — clean record',
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: hasPenalty ? AppColors.error : AppColors.textPrimary),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Unlike the components above, this is a direct subtraction, not a weighted slice — it only moves when a '
                  "report against you resolves to real enforcement action. An unsubstantiated report never touches your score.",
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textPrimary, height: 1.6),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// --- personalized tips ------------------------------------------------------

/// Backend-computed, dynamic, and scoped to your actual weak spots right now
/// — only ever rendered when non-empty (see the call site in
/// `TrustTipsScreen.build`), which is also why there's no empty-state branch
/// here: `_ImprovementTipsCard` below is the always-shown, comprehensive
/// counterpart that covers this screen when the backend has nothing
/// personalized to say yet (e.g. a brand-new account).
class _TipsCard extends StatelessWidget {
  const _TipsCard({required this.tips});

  final List<String> tips;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: tips
          .map((tip) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: _Card(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.lightbulb_outline, size: 18, color: AppColors.accent),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(tip, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textPrimary, height: 1.5)),
                      ),
                    ],
                  ),
                ),
              ))
          .toList(),
    );
  }
}

// --- static, always-shown improvement tips ----------------------------------

class _ImprovementTip {
  const _ImprovementTip({required this.title, required this.body, required this.icon, required this.iconBg, required this.iconColor});

  final String title;
  final String body;
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
}

/// One tip per weighted component (all six — the backend's dynamic list only
/// ever covers four, see this screen's class doc), plus the Reviews tip
/// explicitly naming all six rating dimensions a reviewer scores.
const _improvementTips = [
  _ImprovementTip(
    title: 'Reviews from travellers · 40%',
    icon: Icons.star_outline,
    iconBg: AppColors.primaryTint,
    iconColor: AppColors.primary,
    body: 'This is more than every other component combined, so it matters most. Every completed trip gives your '
        'fellow travellers a chance to rate you across six dimensions — Behaviour, Punctuality, Communication, '
        'Cooperation, Safety, and Reliability. Be considerate, show up on time, communicate clearly in the group '
        'chat, and follow through on plans.',
  ),
  _ImprovementTip(
    title: 'Trip completion behaviour · 20%',
    icon: Icons.task_alt,
    iconBg: AppColors.successTint,
    iconColor: AppColors.success,
    body: 'Finish the trips you join. A graceful, early-notice leave still keeps 90% of the credit for that trip — '
        'but getting removed, leaving late without notice, or being marked a no-show earns nothing, even though the '
        "trip still counts as one you joined.",
  ),
  _ImprovementTip(
    title: 'Verification level · 15%',
    icon: Icons.verified_outlined,
    iconBg: AppColors.communityTint,
    iconColor: AppColors.communityText,
    body: 'Complete phone, email, and government ID verification. Each step is a one-time action that permanently '
        "raises this component — there's no ongoing effort required once it's done.",
  ),
  _ImprovementTip(
    title: 'Organizer reliability · 10%',
    icon: Icons.forum_outlined,
    iconBg: AppColors.accentTint,
    iconColor: AppColors.accentTextOnTint,
    body: "If you organize trips, respond to join requests quickly and consistently — fast, decisive accept/decline "
        "decisions raise this component. It sits at a neutral score if you've never organized, so it's not a "
        'penalty for not hosting.',
  ),
  _ImprovementTip(
    title: 'Account age & activity · 10%',
    icon: Icons.hourglass_bottom,
    iconBg: AppColors.primaryTint,
    iconColor: AppColors.primary,
    body: 'This grows automatically the longer you stay an active member of GoTogether, up to a two-year cap. '
        'Nothing to actively do here besides keep using the app.',
  ),
  _ImprovementTip(
    title: 'Profile completeness · 5%',
    icon: Icons.badge_outlined,
    iconBg: AppColors.successTint,
    iconColor: AppColors.success,
    body: 'Add a profile photo, write a short bio, and fill in your travel preferences. The smallest weight in the '
        "formula, but also the fastest to max out — a couple of minutes of effort for full marks.",
  ),
];

class _ImprovementTipsCard extends StatelessWidget {
  const _ImprovementTipsCard();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: _improvementTips
          .map((tip) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: _Card(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(color: tip.iconBg, shape: BoxShape.circle),
                        alignment: Alignment.center,
                        child: Icon(tip.icon, size: 16, color: tip.iconColor),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(tip.title, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
                            const SizedBox(height: 4),
                            Text(tip.body, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textPrimary, height: 1.6)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ))
          .toList(),
    );
  }
}

// --- recent activity ------------------------------------------------------

class _HistoryCard extends ConsumerWidget {
  const _HistoryCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final historyAsync = ref.watch(trustHistoryProvider);
    return _Card(
      child: historyAsync.when(
        loading: () => const Padding(padding: EdgeInsets.symmetric(vertical: 4), child: LinearProgressIndicator()),
        // Non-critical section — a failed history fetch shouldn't block the
        // rest of the breakdown, which is why this renders inline rather
        // than an error state with its own retry button.
        error: (e, _) => const Text('Could not load recent activity.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
        data: (page) {
          if (page.items.isEmpty) {
            return const Text(
              "No changes yet — your score updates as you complete trips, receive reviews, and build your profile.",
              style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.5),
            );
          }
          final entries = page.items.take(10).toList();
          return Column(
            children: entries.asMap().entries.map((e) {
              final entry = e.value;
              final isLast = e.key == entries.length - 1;
              return Padding(
                padding: EdgeInsets.only(bottom: isLast ? 0 : 10),
                child: _HistoryRow(entry: entry),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

class _HistoryRow extends StatelessWidget {
  const _HistoryRow({required this.entry});

  final TrustScoreHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    final delta = entry.newScore - entry.oldScore;
    final rounded = double.parse(delta.toStringAsFixed(1));
    final isUp = rounded > 0;
    final isFlat = rounded == 0;
    final color = isFlat ? AppColors.textTertiary : (isUp ? AppColors.success : AppColors.error);
    final deltaLabel = isFlat ? '±0.0' : '${isUp ? '+' : ''}${rounded.toStringAsFixed(1)}';

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
          decoration: BoxDecoration(color: color.withOpacity(0.12), borderRadius: BorderRadius.circular(6)),
          child: Text(deltaLabel, style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: color)),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(entry.reason, style: const TextStyle(fontSize: 11.5, color: AppColors.textPrimary, height: 1.4)),
              const SizedBox(height: 2),
              Text(_relativeTime(entry.createdAt), style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
            ],
          ),
        ),
      ],
    );
  }

  static String _relativeTime(String createdAt) {
    final parsed = DateTime.tryParse(createdAt);
    if (parsed == null) return '';
    final diff = DateTime.now().toUtc().difference(parsed.toUtc());
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return '${(diff.inDays / 7).floor()}w ago';
  }
}

// --- shared bits ------------------------------------------------------

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(text, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.5, color: AppColors.textSecondary));
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
