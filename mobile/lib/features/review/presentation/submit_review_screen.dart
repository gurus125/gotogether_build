import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../data/review_models.dart';
import '../state/review_providers.dart';

/// The actual review submission form (6 sub-ratings + overall + comment).
///
/// **No design mockup exists for this specific screen** — the approved
/// design set documents the Profile screen's Reviews *card* (how published
/// reviews are displayed) but never the submission form itself. Per the
/// project's "flag gaps, don't invent a design silently" convention (same
/// as the missing OTP screen in Phase 1 and the empty-chat-state gap in
/// Phase 4), this is a plain, clearly-labelled form built from existing app
/// tokens rather than a freehand redesign — flagged here for the user to
/// confirm or hand off an actual mockup for later.
class SubmitReviewScreen extends ConsumerStatefulWidget {
  const SubmitReviewScreen({super.key, required this.tripId, required this.revieweeId, required this.revieweeName});

  final String tripId;
  final String revieweeId;
  final String revieweeName;

  @override
  ConsumerState<SubmitReviewScreen> createState() => _SubmitReviewScreenState();
}

class _SubmitReviewScreenState extends ConsumerState<SubmitReviewScreen> {
  final _comment = TextEditingController();
  int _behaviour = 0, _punctuality = 0, _communication = 0, _cooperation = 0, _safety = 0, _reliability = 0, _overall = 0;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  bool get _canSubmit =>
      _behaviour > 0 && _punctuality > 0 && _communication > 0 && _cooperation > 0 && _safety > 0 && _reliability > 0 && _overall > 0 && !_submitting;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text('Review ${widget.revieweeName}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Your review stays hidden until both of you have submitted — this keeps feedback honest.',
            style: TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.5),
          ),
          const SizedBox(height: 16),
          _RatingRow(label: 'Behaviour', value: _behaviour, onChanged: (v) => setState(() => _behaviour = v)),
          _RatingRow(label: 'Punctuality', value: _punctuality, onChanged: (v) => setState(() => _punctuality = v)),
          _RatingRow(label: 'Communication', value: _communication, onChanged: (v) => setState(() => _communication = v)),
          _RatingRow(label: 'Cooperation', value: _cooperation, onChanged: (v) => setState(() => _cooperation = v)),
          _RatingRow(label: 'Safety', value: _safety, onChanged: (v) => setState(() => _safety = v)),
          _RatingRow(label: 'Reliability', value: _reliability, onChanged: (v) => setState(() => _reliability = v)),
          const Divider(height: 24),
          _RatingRow(label: 'Overall', value: _overall, onChanged: (v) => setState(() => _overall = v)),
          const SizedBox(height: 16),
          TextField(
            controller: _comment,
            maxLength: 280,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Comment (optional)',
              hintText: 'Share anything that would help other travellers.',
              border: OutlineInputBorder(),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(fontSize: 12.5, color: AppColors.error)),
          ],
          const SizedBox(height: 8),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.accent, minimumSize: const Size.fromHeight(48)),
            onPressed: _canSubmit ? _submit : null,
            child: _submitting
                ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('Submit review'),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(reviewApiProvider).submit(
            widget.tripId,
            SubmitReviewRequest(
              revieweeId: widget.revieweeId,
              ratingBehaviour: _behaviour,
              ratingPunctuality: _punctuality,
              ratingCommunication: _communication,
              ratingCooperation: _cooperation,
              ratingSafety: _safety,
              ratingReliability: _reliability,
              overallRating: _overall,
              comment: _comment.text.trim().isEmpty ? null : _comment.text.trim(),
            ),
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Review submitted.')));
        Navigator.of(context).pop(true);
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

class _RatingRow extends StatelessWidget {
  const _RatingRow({required this.label, required this.value, required this.onChanged});

  final String label;
  final int value;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          SizedBox(width: 116, child: Text(label, style: const TextStyle(fontSize: 12.5))),
          ...List.generate(5, (i) {
            final starValue = i + 1;
            return GestureDetector(
              onTap: () => onChanged(starValue),
              child: Padding(
                padding: const EdgeInsets.only(right: 2),
                child: Icon(
                  starValue <= value ? Icons.star_rounded : Icons.star_border_rounded,
                  color: AppColors.accent,
                  size: 24,
                ),
              ),
            );
          }),
        ],
      ),
    );
  }
}
