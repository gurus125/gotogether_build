import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../data/company_models.dart';
import '../state/company_providers.dart';

/// Travel Company registration (`POST /companies/apply`, Operations Module A
/// "Company Registration": eligibility, required documents, manual
/// Moderator/Admin review). No design mockup exists anywhere in the approved
/// design set for this flow (same class of gap as the Admin Panel and the
/// Notifications bell icon) — this is a functional-only screen built to the
/// existing design system's tokens (colors, spacing, form patterns from
/// `EditProfileScreen`), not matched against any approved visual.
///
/// There is no document-upload endpoint in this app yet (same gap as trip
/// images / profile photos) — the "document reference" field is a manual
/// stand-in for an already-uploaded storage key, not a real upload flow.
///
/// Submitting always leaves the new company `APPLICATION_SUBMITTED` /
/// `UNDER_REVIEW` — reaching `VERIFIED` requires a real Moderator decision
/// (Phase 8's `admin` module, not built yet), so this screen's success state
/// is deliberately just "application received," not "you're live."
class CompanyApplyScreen extends ConsumerStatefulWidget {
  const CompanyApplyScreen({super.key});

  @override
  ConsumerState<CompanyApplyScreen> createState() => _CompanyApplyScreenState();
}

class _CompanyApplyScreenState extends ConsumerState<CompanyApplyScreen> {
  final _displayNameController = TextEditingController();
  final _legalNameController = TextEditingController();
  final _registrationNumberController = TextEditingController();
  final _gstNumberController = TextEditingController();
  final _supportEmailController = TextEditingController();
  final _supportPhoneController = TextEditingController();
  final _cancellationPolicyController = TextEditingController();
  final _documentReferenceController = TextEditingController();

  bool _submitting = false;

  @override
  void dispose() {
    _displayNameController.dispose();
    _legalNameController.dispose();
    _registrationNumberController.dispose();
    _gstNumberController.dispose();
    _supportEmailController.dispose();
    _supportPhoneController.dispose();
    _cancellationPolicyController.dispose();
    _documentReferenceController.dispose();
    super.dispose();
  }

  bool get _canSubmit =>
      _displayNameController.text.trim().isNotEmpty &&
      _legalNameController.text.trim().isNotEmpty &&
      _registrationNumberController.text.trim().isNotEmpty &&
      _supportEmailController.text.trim().isNotEmpty &&
      _supportPhoneController.text.trim().isNotEmpty &&
      _cancellationPolicyController.text.trim().isNotEmpty &&
      _documentReferenceController.text.trim().isNotEmpty;

  Future<void> _submit() async {
    setState(() => _submitting = true);
    try {
      final request = ApplyCompanyRequest(
        displayName: _displayNameController.text.trim(),
        legalName: _legalNameController.text.trim(),
        registrationNumber: _registrationNumberController.text.trim(),
        gstNumber: _gstNumberController.text.trim().isEmpty ? null : _gstNumberController.text.trim(),
        supportEmail: _supportEmailController.text.trim(),
        supportPhone: _supportPhoneController.text.trim(),
        cancellationPolicy: _cancellationPolicyController.text.trim(),
        documents: [CompanyDocumentRef(documentType: 'business_registration', storageKey: _documentReferenceController.text.trim())],
      );
      await ref.read(companyApiProvider).apply(request);
      ref.invalidate(myCompanyStatusProvider);
      if (!mounted) return;
      context.pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Application submitted — we\'ll review it shortly.')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: const BoxDecoration(color: AppColors.surface, border: Border(bottom: BorderSide(color: AppColors.border))),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => context.pop(),
                    child: Container(
                      width: 30,
                      height: 30,
                      decoration: const BoxDecoration(color: AppColors.background, shape: BoxShape.circle),
                      child: const Icon(Icons.arrow_back, size: 14),
                    ),
                  ),
                  const SizedBox(width: 10),
                  const Expanded(child: Text('Become a Travel Partner', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700))),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  const Text(
                    'Verified Travel Companies run fixed-price, fixed-itinerary trips alongside the peer-created community — a business account, never an individual traveller profile.',
                    style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.6),
                  ),
                  const SizedBox(height: 16),
                  const _SectionLabel('BUSINESS DETAILS'),
                  _Card(children: [
                    _Field(label: 'Business name (shown to travellers)', controller: _displayNameController, onChanged: () => setState(() {})),
                    const SizedBox(height: 12),
                    _Field(label: 'Legal name', controller: _legalNameController, onChanged: () => setState(() {})),
                    const SizedBox(height: 12),
                    _Field(label: 'Business registration number', controller: _registrationNumberController, onChanged: () => setState(() {})),
                    const SizedBox(height: 12),
                    _Field(label: 'GST number (optional)', controller: _gstNumberController, onChanged: () => setState(() {})),
                  ]),
                  const SizedBox(height: 16),
                  const _SectionLabel('CONTACT & POLICY'),
                  _Card(children: [
                    _Field(label: 'Support email', controller: _supportEmailController, onChanged: () => setState(() {})),
                    const SizedBox(height: 12),
                    _Field(label: 'Support phone', controller: _supportPhoneController, onChanged: () => setState(() {})),
                    const SizedBox(height: 12),
                    _Field(
                      label: 'Cancellation policy (shown publicly on your profile)',
                      controller: _cancellationPolicyController,
                      onChanged: () => setState(() {}),
                      maxLines: 4,
                    ),
                  ]),
                  const SizedBox(height: 16),
                  const _SectionLabel('VERIFICATION DOCUMENTS'),
                  _Card(children: [
                    _Field(
                      label: 'Business registration certificate (reference)',
                      controller: _documentReferenceController,
                      onChanged: () => setState(() {}),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Document upload isn\'t wired in this build yet — enter a reference for now. A Moderator reviews every application manually before it can go live.',
                      style: TextStyle(fontSize: 9.5, color: AppColors.textTertiary, height: 1.5),
                    ),
                  ]),
                  const SizedBox(height: 24),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: const BoxDecoration(color: AppColors.surface, border: Border(top: BorderSide(color: AppColors.border))),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: (_canSubmit && !_submitting) ? _submit : null,
                  child: _submitting
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('Submit application'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.5, color: AppColors.textSecondary)),
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(16)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({required this.label, required this.controller, required this.onChanged, this.maxLines = 1});

  final String label;
  final TextEditingController controller;
  final VoidCallback onChanged;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 5),
          child: Text(label, style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
        ),
        TextField(controller: controller, maxLines: maxLines, onChanged: (_) => onChanged()),
      ],
    );
  }
}
