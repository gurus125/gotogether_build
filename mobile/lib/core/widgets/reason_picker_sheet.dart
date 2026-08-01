import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Shared reason-collection UI for the app's two destructive, backend-
/// mandatory-reason actions: cancelling a trip (`TripApi.cancel`,
/// `CancelTripRequest.reason` is `@NotBlank`) and removing a traveller
/// (`MembershipApi.removeMember`, `RemoveMemberRequest.reason` likewise).
/// Both backend fields are plain free text — this sheet just gives the
/// organizer a faster way to fill that text than typing from scratch: tap
/// one of a few common, pre-written reasons, or pick "Other" to write their
/// own. Either path produces the same free-text string the backend already
/// expects, so no DTO changes were needed on either side.
///
/// Returns the chosen reason, or `null` if the sheet was dismissed without
/// confirming (the caller should treat `null` as "user backed out," not
/// attempt the action).
Future<String?> showReasonPicker(
  BuildContext context, {
  required String title,
  required String subtitle,
  required List<String> presetReasons,
  required String confirmLabel,
}) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => _ReasonPickerSheet(
      title: title,
      subtitle: subtitle,
      presetReasons: presetReasons,
      confirmLabel: confirmLabel,
    ),
  );
}

class _ReasonPickerSheet extends StatefulWidget {
  const _ReasonPickerSheet({
    required this.title,
    required this.subtitle,
    required this.presetReasons,
    required this.confirmLabel,
  });

  final String title;
  final String subtitle;
  final List<String> presetReasons;
  final String confirmLabel;

  @override
  State<_ReasonPickerSheet> createState() => _ReasonPickerSheetState();
}

class _ReasonPickerSheetState extends State<_ReasonPickerSheet> {
  static const _otherKey = '__other__';

  String? _selected;
  final _customController = TextEditingController();

  @override
  void dispose() {
    _customController.dispose();
    super.dispose();
  }

  String? get _resolvedReason {
    if (_selected == null) return null;
    if (_selected == _otherKey) {
      final custom = _customController.text.trim();
      return custom.isEmpty ? null : custom;
    }
    return _selected;
  }

  @override
  Widget build(BuildContext context) {
    final canConfirm = _resolvedReason != null;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SafeArea(
        child: Container(
          decoration: const BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(color: AppColors.border, borderRadius: BorderRadius.circular(100)),
                ),
              ),
              const SizedBox(height: 16),
              Text(widget.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.textPrimary)),
              const SizedBox(height: 4),
              Text(widget.subtitle, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textSecondary, height: 1.5)),
              const SizedBox(height: 16),
              for (final reason in widget.presetReasons) ...[
                _ReasonOption(
                  label: reason,
                  selected: _selected == reason,
                  onTap: () => setState(() => _selected = reason),
                ),
                const SizedBox(height: 8),
              ],
              _ReasonOption(
                label: 'Other — write your own',
                selected: _selected == _otherKey,
                onTap: () => setState(() => _selected = _otherKey),
              ),
              if (_selected == _otherKey) ...[
                const SizedBox(height: 10),
                TextField(
                  controller: _customController,
                  autofocus: true,
                  maxLength: 200,
                  maxLines: 3,
                  onChanged: (_) => setState(() {}),
                  style: const TextStyle(fontSize: 13, color: AppColors.textPrimary),
                  decoration: const InputDecoration(
                    hintText: 'Type your reason…',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(backgroundColor: AppColors.error),
                  onPressed: canConfirm ? () => Navigator.of(context).pop(_resolvedReason) : null,
                  child: Text(widget.confirmLabel),
                ),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Back out', style: TextStyle(color: AppColors.textSecondary)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ReasonOption extends StatelessWidget {
  const _ReasonOption({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          color: selected ? AppColors.errorTint : AppColors.background,
          border: Border.all(color: selected ? AppColors.error : AppColors.border),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(
              selected ? Icons.radio_button_checked : Icons.radio_button_off,
              size: 18,
              color: selected ? AppColors.error : AppColors.textTertiary,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                label,
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: selected ? AppColors.error : AppColors.textPrimary),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
