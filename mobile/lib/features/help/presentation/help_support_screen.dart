import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/theme/app_colors.dart';
import '../data/help_content.dart';

const _supportEmail = 'gotogethertech@gmail.com';

/// Help & Support — search, Emergency help, FAQs, Safety tips, Community
/// guidelines, Contact support, Report & feedback. Matches the approved
/// mockup (`Help & Support.png`, two screens).
///
/// Emergency calls `tel:100` (India police) directly — the one action here
/// that can't be a "coming soon" stub, since it's exactly the row someone in
/// real danger would reach for. "Chat with support" and the Report/Feedback
/// rows all resolve to `mailto:$_supportEmail` with a distinguishing subject
/// line — there's no live-chat/ticketing backend to wire this to (a `report`
/// module exists, Phase 8, but it's built for reporting another user/trip's
/// misconduct with a required `entityId`, not free-floating app feedback or
/// bug reports — a different shape of problem), so email is the one real
/// channel available. Flagged here rather than faking a chat widget with
/// nothing behind it.
///
/// FAQ/tip/guideline content is static (see `HelpArticle`'s doc for why) and
/// searchable — the search bar matches against title and body across every
/// section at once.
class HelpSupportScreen extends StatefulWidget {
  const HelpSupportScreen({super.key});

  @override
  State<HelpSupportScreen> createState() => _HelpSupportScreenState();
}

class _HelpSupportScreenState extends State<HelpSupportScreen> {
  final _searchController = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _launch(Uri uri, String failureMessage) async {
    bool launched = false;
    try {
      launched = await launchUrl(uri);
    } catch (_) {
      launched = false;
    }
    if (!launched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(failureMessage)));
    }
  }

  void _call(String number) => _launch(Uri(scheme: 'tel', path: number), "Couldn't open the dialer — call $number directly.");

  void _email(String subject) => _launch(
        Uri(scheme: 'mailto', path: _supportEmail, query: 'subject=${Uri.encodeComponent(subject)}'),
        "Couldn't open an email app — reach us directly at $_supportEmail.",
      );

  void _openArticle(HelpArticle article) => context.push('/help/article', extra: article);

  @override
  Widget build(BuildContext context) {
    final query = _query.trim().toLowerCase();
    final searching = query.isNotEmpty;
    final results = searching
        ? allHelpArticles.where((a) => a.title.toLowerCase().contains(query) || a.body.toLowerCase().contains(query)).toList()
        : const <HelpArticle>[];

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Help & Support')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          _SearchField(controller: _searchController, onChanged: (v) => setState(() => _query = v)),
          const SizedBox(height: 14),
          if (searching) ...[
            if (results.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Text('No help articles match "$_query".',
                    textAlign: TextAlign.center, style: const TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
              )
            else
              ...results.map((a) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: _HelpRow(icon: a.icon, iconBg: a.iconBg, title: a.title, onTap: () => _openArticle(a)),
                  )),
          ] else ...[
            _EmergencyBanner(onCall: () => _call('100')),
            const SizedBox(height: 18),
            for (final section in helpSections) ...[
              _SectionHeader(label: section.label),
              const SizedBox(height: 8),
              ...section.articles.map((a) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: _HelpRow(icon: a.icon, iconBg: a.iconBg, title: a.title, onTap: () => _openArticle(a)),
                  )),
              const SizedBox(height: 10),
            ],
            _SectionHeader(label: 'Contact support'),
            const SizedBox(height: 8),
            _HelpRow(
              icon: '💬',
              iconBg: AppColors.primaryTint,
              title: 'Chat with support',
              subtitle: 'Usually replies in under 1 hour',
              onTap: () => _email('Support request from GoTogether app'),
            ),
            const SizedBox(height: 8),
            _HelpRow(
              icon: '✉️',
              iconBg: AppColors.primaryTint,
              title: 'Email us',
              subtitle: _supportEmail,
              onTap: () => _email('Support request from GoTogether app'),
            ),
            const SizedBox(height: 18),
            _SectionHeader(label: 'Report & feedback'),
            const SizedBox(height: 8),
            _HelpRow(
              icon: '🚨',
              iconBg: AppColors.errorTint,
              title: 'Report a problem',
              onTap: () => _email('Problem report — GoTogether app'),
            ),
            const SizedBox(height: 8),
            _HelpRow(
              icon: '⭐',
              iconBg: AppColors.accentTint,
              title: 'Send feedback',
              onTap: () => _email('Feedback — GoTogether app'),
            ),
            const SizedBox(height: 24),
            const Center(
              child: Text('GoTogether v1.0.0', style: TextStyle(fontSize: 10.5, color: AppColors.textTertiary)),
            ),
          ],
        ],
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  const _SearchField({required this.controller, required this.onChanged});

  final TextEditingController controller;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
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
            child: TextField(
              controller: controller,
              onChanged: onChanged,
              decoration: const InputDecoration(
                hintText: 'Search help articles',
                border: InputBorder.none,
                filled: false,
                contentPadding: EdgeInsets.zero,
              ),
              style: const TextStyle(fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmergencyBanner extends StatelessWidget {
  const _EmergencyBanner({required this.onCall});

  final VoidCallback onCall;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.errorTint, borderRadius: BorderRadius.circular(16)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: const BoxDecoration(color: AppColors.error, shape: BoxShape.circle),
            alignment: Alignment.center,
            child: const Icon(Icons.phone_in_talk, size: 17, color: Colors.white),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Emergency help', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: AppColors.error)),
                const SizedBox(height: 3),
                const Text(
                  'In danger or unsafe right now? Get help immediately.',
                  style: TextStyle(fontSize: 11, color: AppColors.textSecondary, height: 1.5),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: onCall,
            child: const Padding(
              padding: EdgeInsets.symmetric(vertical: 4),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Call', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppColors.error)),
                  Icon(Icons.chevron_right, size: 16, color: AppColors.error),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label.toUpperCase(),
      style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.5, color: AppColors.textSecondary),
    );
  }
}

class _HelpRow extends StatelessWidget {
  const _HelpRow({required this.icon, required this.iconBg, required this.title, this.subtitle, required this.onTap});

  final String icon;
  final Color iconBg;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
              alignment: Alignment.center,
              child: Text(icon, style: const TextStyle(fontSize: 15)),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(subtitle!, style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right, size: 16, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }
}
