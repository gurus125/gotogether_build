import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/network/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../user/state/user_providers.dart';
import '../data/profile_models.dart';
import '../state/profile_providers.dart';

/// Wired implementation of the approved "Edit Profile" screen design:
/// Photo · Basic info · Travel preferences · Emergency contact ·
/// Verification status, five cards, Save disabled until something changes.
///
/// Photo upload is now wired against the backend's presigned-URL flow
/// (`storage.StorageService` + `ProfileController.createPhotoUploadUrl`) —
/// previously a "Coming soon" stub, see `ImageUploadService`'s class doc for
/// the shared pick/upload logic also used by trip photos.
///
/// Two gaps versus the approved design, flagged rather than silently
/// resolved: (1) `smokingPreference`/`drinkingPreference` are free-form TEXT
/// columns with no documented value set (DB Schema Part 1 has no CHECK
/// constraint on them) — the design's on/off toggle is mapped to the two
/// literal strings `"yes"`/`"no"` here, a naming convention that should be
/// confirmed, not assumed as final. (2) The design's "VERIFICATION STATUS"
/// card shows a per-step breakdown ("3 of 4 verified") that the Profile
/// module's API doesn't expose (only the aggregate `verificationLevel` from
/// `GET /users/me`) — that per-step data belongs to the Verification module
/// (Phase 8), so this card shows the aggregate level only, and "Manage"
/// doesn't navigate anywhere yet.
class EditProfileScreen extends ConsumerWidget {
  const EditProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: profileAsync.when(
          data: (profile) => _EditProfileForm(initial: profile),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Could not load your profile.\n$error', textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: () => ref.invalidate(profileProvider),
                    child: const Text('Retry'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

const _languageOptions = ['English', 'Hindi', 'Punjabi', 'Bengali', 'Tamil'];
const _styleOptions = ['Backpacking', 'Trekking', 'Luxury', 'Road Trip', 'Camping'];
const _foodOptions = ['Vegetarian', 'Non-vegetarian', 'Vegan', 'No preference'];
const _budgetOptions = ['₹5k–10k', '₹10k–15k', '₹15k+'];

class _EditProfileForm extends ConsumerStatefulWidget {
  const _EditProfileForm({required this.initial});

  final ProfileResponse initial;

  @override
  ConsumerState<_EditProfileForm> createState() => _EditProfileFormState();
}

class _EditProfileFormState extends ConsumerState<_EditProfileForm> {
  late final TextEditingController _nameController;
  late final TextEditingController _bioController;
  late final TextEditingController _emergencyNameController;
  late final TextEditingController _emergencyPhoneController;

  late Set<String> _languages;
  late Set<String> _styles;
  String? _food;
  String? _budget;
  late bool _smoking;
  late bool _drinking;
  String? _photoUrl;

  bool _dirty = false;
  bool _saving = false;
  bool _uploadingPhoto = false;

  @override
  void initState() {
    super.initState();
    final p = widget.initial;
    _nameController = TextEditingController(text: p.displayName)..addListener(_markDirty);
    _bioController = TextEditingController(text: p.bio ?? '')..addListener(_markDirty);
    _emergencyNameController = TextEditingController(text: p.emergencyContactName ?? '')..addListener(_markDirty);
    _emergencyPhoneController = TextEditingController(text: p.emergencyContactPhone ?? '')..addListener(_markDirty);
    _languages = p.languages.toSet();
    _styles = p.travelStyle == null ? {} : {p.travelStyle!};
    _food = p.foodPreference;
    _budget = p.preferredBudgetStyle;
    _smoking = p.smokingPreference == 'yes';
    _drinking = p.drinkingPreference == 'yes';
    _photoUrl = p.photoUrl;
  }

  @override
  void dispose() {
    _nameController.dispose();
    _bioController.dispose();
    _emergencyNameController.dispose();
    _emergencyPhoneController.dispose();
    super.dispose();
  }

  void _markDirty() {
    if (!_dirty) setState(() => _dirty = true);
  }

  /// Bottom sheet choice (Camera/Gallery) then the shared upload flow —
  /// see `ImageUploadService`'s class doc. Sets `_photoUrl` + marks the form
  /// dirty rather than saving immediately, so it goes out with the rest of
  /// the form on "Save changes" / can still be discarded, consistent with
  /// every other field on this screen.
  Future<void> _pickPhoto() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              tileColor: AppColors.surface,
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Take a photo'),
              onTap: () => Navigator.of(context).pop(ImageSource.camera),
            ),
            ListTile(
              tileColor: AppColors.surface,
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Choose from gallery'),
              onTap: () => Navigator.of(context).pop(ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null || !mounted) return;

    setState(() => _uploadingPhoto = true);
    try {
      final url = await ref.read(imageUploadServiceProvider).pickAndUpload(
            source: source,
            requestUploadUrl: (contentType) => ref.read(profileApiProvider).getPhotoUploadUrl(contentType),
          );
      if (url == null) return; // user backed out of the picker
      setState(() {
        _photoUrl = url;
        _dirty = true;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _uploadingPhoto = false);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final request = UpdateProfileRequest(
        displayName: _nameController.text.trim(),
        photoUrl: _photoUrl,
        bio: _bioController.text,
        languages: _languages.toList(),
        travelStyle: _styles.isEmpty ? null : _styles.first,
        foodPreference: _food,
        smokingPreference: _smoking ? 'yes' : 'no',
        drinkingPreference: _drinking ? 'yes' : 'no',
        preferredBudgetStyle: _budget,
        emergencyContactName: _emergencyNameController.text.trim(),
        emergencyContactPhone: _emergencyPhoneController.text.trim(),
      );
      await ref.read(profileApiProvider).updateProfile(request);
      ref.invalidate(profileProvider);
      ref.invalidate(currentUserProvider);
      if (!mounted) return;
      context.pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _discard() => context.pop();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: const BoxDecoration(
            color: AppColors.surface,
            border: Border(bottom: BorderSide(color: AppColors.border)),
          ),
          child: Row(
            children: [
              GestureDetector(
                onTap: _discard,
                child: Container(
                  width: 30,
                  height: 30,
                  decoration: const BoxDecoration(color: AppColors.background, shape: BoxShape.circle),
                  child: const Icon(Icons.arrow_back, size: 14),
                ),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Text('Edit profile', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
              ),
              GestureDetector(
                onTap: (_dirty && !_saving) ? _save : null,
                child: Text(
                  'Save',
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                    color: _dirty ? AppColors.primary : AppColors.textTertiary,
                  ),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Center(
                child: Column(
                  children: [
                    GestureDetector(
                      onTap: _uploadingPhoto ? null : _pickPhoto,
                      child: Stack(
                        children: [
                          CircleAvatar(
                            radius: 44,
                            backgroundColor: AppColors.primaryLight,
                            backgroundImage: _photoUrl != null ? NetworkImage(_photoUrl!) : null,
                          ),
                          if (_uploadingPhoto)
                            const Positioned.fill(
                              child: CircleAvatar(
                                backgroundColor: Color(0x99000000),
                                child: SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                ),
                              ),
                            ),
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: Container(
                              width: 28,
                              height: 28,
                              decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
                              child: const Icon(Icons.camera_alt, size: 14, color: Colors.white),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 8),
                    GestureDetector(
                      onTap: _uploadingPhoto ? null : _pickPhoto,
                      child: const Text('Change photo', style: TextStyle(fontSize: 11, color: AppColors.primary, fontWeight: FontWeight.w500)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              const _SectionLabel('BASIC INFO'),
              _Card(
                children: [
                  _FieldLabel('Name'),
                  TextField(controller: _nameController),
                  const SizedBox(height: 12),
                  _FieldLabel('Bio'),
                  TextField(
                    controller: _bioController,
                    maxLength: 250,
                    maxLines: 3,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const _SectionLabel('TRAVEL PREFERENCES'),
              _Card(
                children: [
                  _FieldLabel('Languages'),
                  _ChipGroup(
                    options: _languageOptions,
                    isSelected: (o) => _languages.contains(o),
                    onTap: (o) => setState(() {
                      _languages.contains(o) ? _languages.remove(o) : _languages.add(o);
                      _dirty = true;
                    }),
                  ),
                  const SizedBox(height: 14),
                  _FieldLabel('Travel style'),
                  _ChipGroup(
                    options: _styleOptions,
                    isSelected: (o) => _styles.contains(o),
                    onTap: (o) => setState(() {
                      _styles.contains(o) ? _styles.remove(o) : _styles.add(o);
                      _dirty = true;
                    }),
                  ),
                  const SizedBox(height: 14),
                  _FieldLabel('Food preference'),
                  _ChipGroup(
                    options: _foodOptions,
                    isSelected: (o) => _food == o,
                    onTap: (o) => setState(() {
                      _food = o;
                      _dirty = true;
                    }),
                  ),
                  const SizedBox(height: 14),
                  _FieldLabel('Budget preference'),
                  _ChipGroup(
                    options: _budgetOptions,
                    isSelected: (o) => _budget == o,
                    onTap: (o) => setState(() {
                      _budget = o;
                      _dirty = true;
                    }),
                  ),
                  const SizedBox(height: 4),
                  // tileColor pinned to the enclosing _Card's own surface
                  // color — without it, SwitchListTile logs "ListTile
                  // background color or ink splashes may be invisible"
                  // (only detectable by actually running the app; Flutter's
                  // ListTile asserts its ambient Material has a resolvable
                  // color, and a plain Container/Column like _Card doesn't
                  // provide one).
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    tileColor: AppColors.surface,
                    title: const Text('Smoking', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textPrimary)),
                    value: _smoking,
                    onChanged: (v) => setState(() {
                      _smoking = v;
                      _dirty = true;
                    }),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    tileColor: AppColors.surface,
                    title: const Text('Drinking', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.textPrimary)),
                    value: _drinking,
                    onChanged: (v) => setState(() {
                      _drinking = v;
                      _dirty = true;
                    }),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const _SectionLabel('EMERGENCY CONTACT'),
              _Card(
                children: [
                  _FieldLabel('Contact name'),
                  TextField(controller: _emergencyNameController, decoration: const InputDecoration(hintText: 'e.g. Anita R.')),
                  const SizedBox(height: 12),
                  _FieldLabel('Phone number'),
                  TextField(controller: _emergencyPhoneController, decoration: const InputDecoration(hintText: '+91 98765 43210')),
                  const SizedBox(height: 8),
                  const Text(
                    'Only shared with GoTogether safety support in an emergency — never visible to other travellers.',
                    style: TextStyle(fontSize: 9.5, color: AppColors.textSecondary, height: 1.5),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const _SectionLabel('VERIFICATION STATUS'),
              Consumer(builder: (context, ref, _) {
                final userAsync = ref.watch(currentUserProvider);
                return Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(color: AppColors.successTint, borderRadius: BorderRadius.circular(16)),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              userAsync.maybeWhen(
                                data: (u) => 'Verification level: ${u.verificationLevel}',
                                orElse: () => 'Verification level',
                              ),
                              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.successTextOnTint),
                            ),
                          ],
                        ),
                      ),
                      GestureDetector(
                        onTap: () => ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Full verification flow arrives in a later phase.')),
                        ),
                        child: const Text('Manage →', style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w500, color: AppColors.successTextOnTint)),
                      ),
                    ],
                  ),
                );
              }),
              const SizedBox(height: 24),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: const BoxDecoration(
            color: AppColors.surface,
            border: Border(top: BorderSide(color: AppColors.border)),
          ),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _saving ? null : _discard,
                  child: const Text('Discard'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton(
                  onPressed: (_dirty && !_saving) ? _save : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _dirty ? AppColors.primary : AppColors.border,
                    foregroundColor: _dirty ? Colors.white : AppColors.textTertiary,
                  ),
                  child: _saving
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : Text(_dirty ? 'Save changes' : 'Saved'),
                ),
              ),
            ],
          ),
        ),
      ],
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
      // textPrimary, not textSecondary — a bold small-caps section header is
      // meant to read as clearly as the card content below it, not recede
      // behind it. Reported as "very light" against a build predating this
      // screen's color pass; bumped to the darkest token available rather
      // than relying on the medium-grey token still being legible enough.
      child: Text(
        label,
        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.5, color: AppColors.textPrimary),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: Text(label, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
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
      decoration: BoxDecoration(
        color: AppColors.surface,
        border: Border.all(color: AppColors.border),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );
  }
}

class _ChipGroup extends StatelessWidget {
  const _ChipGroup({required this.options, required this.isSelected, required this.onTap});

  final List<String> options;
  final bool Function(String) isSelected;
  final void Function(String) onTap;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: options.map((o) {
        final active = isSelected(o);
        return GestureDetector(
          onTap: () => onTap(o),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: active ? AppColors.primaryLight : Colors.transparent,
              border: active ? null : Border.all(color: AppColors.border),
              borderRadius: BorderRadius.circular(100),
            ),
            child: Text(
              o,
              style: TextStyle(
                fontSize: 10.5,
                fontWeight: FontWeight.w500,
                color: active ? AppColors.primary : AppColors.textSecondary,
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}
