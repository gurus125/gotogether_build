import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../data/help_content.dart';

/// A single FAQ/safety-tip/guideline article — generic body renderer shared
/// by every `HelpArticle`, reached from `HelpSupportScreen` (list rows and
/// search results alike) via `extra`.
class HelpArticleScreen extends StatelessWidget {
  const HelpArticleScreen({super.key, required this.article});

  final HelpArticle article;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text(article.title, style: const TextStyle(fontSize: 15))),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(color: article.iconBg, shape: BoxShape.circle),
                  alignment: Alignment.center,
                  child: Text(article.icon, style: const TextStyle(fontSize: 18)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(article.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Text(article.body, style: const TextStyle(fontSize: 13, color: AppColors.textPrimary, height: 1.7)),
          ],
        ),
      ),
    );
  }
}
