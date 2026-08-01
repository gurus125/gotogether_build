import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';

/// A single Help & Support article — FAQ answer, safety tip, or community
/// guideline. All content here is static and ships with the app rather than
/// coming from a backend CMS: there's no design doc or backend module for a
/// help-content system, and the actual content (trip join flow, Trust Score
/// formula, cancellation behaviour, report reasons) is stable enough — and
/// tied closely enough to how this specific app works — that hand-writing it
/// against the real mechanics (see `TripService`/`TrustService`/`ReportReason`
/// for what this content is grounded in) is more accurate than a generic
/// placeholder would be. If this needs to be editable without a release,
/// that's a real future ask (a CMS-backed `content` module), not something to
/// half-build here.
class HelpArticle {
  const HelpArticle({required this.title, required this.body, required this.icon, required this.iconBg});

  final String title;
  final String body;
  final String icon;

  /// Background color of the circular icon badge. There's no separate
  /// foreground/tint color to go with it — the icon is an emoji glyph, which
  /// renders in its own native color regardless of any `TextStyle.color` set
  /// on it, so a second color field would just be dead data.
  final Color iconBg;
}

class HelpSection {
  const HelpSection({required this.label, required this.articles});

  final String label;
  final List<HelpArticle> articles;
}

const _faqIconBg = AppColors.primaryTint;
const _tipIconBg = AppColors.communityTint;
const _guidelineIconBg = AppColors.successTint;

final helpSections = <HelpSection>[
  const HelpSection(
    label: 'FAQs',
    articles: [
      HelpArticle(
        title: 'How does joining a trip work?',
        icon: '❓',
        iconBg: _faqIconBg,
        body: 'Browse trips on Explore or Home, then open one that interests you. Tap Request to Join and add a '
            "short message introducing yourself — organizers are more likely to accept a request that says who you "
            "are and why you want to join.\n\nYour request lands in the organizer's Manage Requests queue, where "
            "they can accept you, decline, or place you on a waiting list if the trip is already full. You'll get "
            'a notification the moment they decide.\n\nOnce accepted, you\'re added to the trip and its group chat '
            'unlocks so you can start coordinating with the organizer and other travellers.',
      ),
      HelpArticle(
        title: 'How is my Trust Score calculated?',
        icon: '❓',
        iconBg: _faqIconBg,
        body: "Your Trust Score is a composite of several factors, not just one: reviews from travellers you've "
            'actually been on trips with, how many trips you complete versus leave early or get removed from, your '
            'verification level (phone, email, government ID), how reliable you are as an organizer if you host '
            "trips, your account activity, and how complete your profile is.\n\nCompleting trips and being marked "
            'as attended (not a no-show) contributes positively; leaving early, getting removed, or no-shows drag '
            "the score down.\n\nThere's no way to buy or fake a higher score — it only moves based on your real "
            'trip history and reviews from people you actually travelled with.',
      ),
      HelpArticle(
        title: 'What happens if a trip is cancelled?',
        icon: '❓',
        iconBg: _faqIconBg,
        body: "If an organizer cancels a trip, every member gets notified immediately and the trip's group chat is "
            "archived — no new messages, but your chat history stays visible.\n\nCancelling a trip affects the "
            "organizer's own Trust Score, not the members' — travellers who joined in good faith aren't penalized "
            "for an organizer's decision.\n\nIf you were on the waiting list or your request hadn't been decided "
            "yet, it's automatically closed out; you won't need to withdraw it yourself.",
      ),
    ],
  ),
  const HelpSection(
    label: 'Safety tips',
    articles: [
      HelpArticle(
        title: 'Meeting travellers for the first time',
        icon: '🤝',
        iconBg: _tipIconBg,
        body: "Choose a public place for your first meetup with the group before departure if possible — a cafe, "
            "station, or the trip's designated meeting point all work well.\n\nCheck each traveller's Trust Score "
            "and reviews on their profile beforehand — a thin profile with no verification isn't a dealbreaker on "
            "its own, but combined with other red flags it's worth being cautious.\n\nTrust your instincts: if "
            "something feels off before you've even left, it's okay to raise it in the group chat or step back "
            'entirely.',
      ),
      HelpArticle(
        title: 'Sharing your live location on a trip',
        icon: '📍',
        iconBg: _tipIconBg,
        body: 'Share your live location with a trusted friend or family member for the full duration of any trip, '
            'not just with people in your travel group. Let them know your rough itinerary and check in at agreed '
            'points, especially on multi-day trips.\n\nWithin the group, only share your exact real-time location '
            "with people you've actually met and feel comfortable with — being in the same group chat isn't on its "
            'own a reason to broadcast your location to everyone in it.',
      ),
      HelpArticle(
        title: 'Recognizing red flags',
        icon: '🚩',
        iconBg: _tipIconBg,
        body: 'Be cautious of organizers or fellow travellers who pressure you to pay outside the app, refuse to '
            'verify their identity, get defensive when asked reasonable safety questions, or push last-minute plan '
            "changes that isolate you from the rest of the group.\n\nA verified badge and a high Trust Score are "
            "good signals, but they're not a guarantee — if someone's behaviour doesn't match their profile, "
            "report it. Trust how you feel over how someone presents themselves.",
      ),
    ],
  ),
  const HelpSection(
    label: 'Community guidelines',
    articles: [
      HelpArticle(
        title: 'Code of conduct',
        icon: '📜',
        iconBg: _guidelineIconBg,
        body: 'Treat every traveller with respect, regardless of background, gender, or where they\'re from. '
            'Harassment, discrimination, and unwanted contact are never acceptable and are grounds for removal '
            "from a trip and its chat.\n\nBe honest about who you are — using a fake profile or misrepresenting "
            'your identity breaks the trust the entire platform depends on.\n\nShow up when you say you will, '
            "communicate promptly if plans change, and give organizers and fellow travellers the same reliability "
            "you'd expect from them.",
      ),
      HelpArticle(
        title: 'What gets an account suspended',
        icon: '⛔',
        iconBg: _guidelineIconBg,
        body: 'Accounts can be suspended for harassment or unsafe behaviour toward other travellers, confirmed '
            'fraud (fake profiles, identity mismatch, or misrepresenting yourself), repeated no-shows after '
            'committing to trips, spamming trips or chats, or posting inappropriate content.\n\nReports are '
            "reviewed by our moderation team, not resolved automatically — a single report doesn't mean automatic "
            'suspension, but a pattern of verified reports will. Serious safety violations can lead to immediate '
            'suspension while under review.',
      ),
    ],
  ),
];

/// Flattened view of every article across every section — used by the
/// search bar, which searches across all categories at once rather than
/// being scoped to whichever section the user happens to be looking at.
List<HelpArticle> get allHelpArticles => helpSections.expand((s) => s.articles).toList();
