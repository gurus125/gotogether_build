import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../data/trip_models.dart';
import '../state/trip_providers.dart';

/// "Manage Trip" — the screen the Create Trip wizard's Review step always
/// promised ("Group size, meeting point, approval settings and more can be
/// added right after publishing") but that had no endpoint or screen wired to
/// it until now — see backend `UpdateTripRequest`'s class doc for exactly
/// which fields are/aren't in scope and why.
///
/// Deliberately does NOT include `visibility` (only `PUBLIC` is a documented
/// Phase 2 flow — see backend `TripVisibility`'s class doc) or an itinerary
/// editor (no such column exists anywhere in the approved schema). Adding
/// either without a design doc behind it would be scope invention, not a
/// fix — flagged here rather than silently built. Locked (not shown at all,
/// via `_OrganizerActionBar`/`my_trips_screen.dart`'s entry points) once a
/// trip reaches `IN_PROGRESS` or a terminal status, mirroring backend
/// `TripService.requireEditable`'s own rule.
class EditTripScreen extends ConsumerWidget {
  const EditTripScreen({super.key, required this.tripId});

  final String tripId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detailsAsync = ref.watch(tripDetailsProvider(tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Edit trip')),
      body: detailsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Could not load this trip.\n$e', textAlign: TextAlign.center)),
        data: (details) => _EditTripForm(tripId: tripId, initial: details.trip),
      ),
    );
  }
}

class _EditTripForm extends ConsumerStatefulWidget {
  const _EditTripForm({required this.tripId, required this.initial});

  final String tripId;
  final TripDetails initial;

  @override
  ConsumerState<_EditTripForm> createState() => _EditTripFormState();
}

class _EditTripFormState extends ConsumerState<_EditTripForm> {
  late final _titleController = TextEditingController(text: widget.initial.title);
  late final _descriptionController = TextEditingController(text: widget.initial.description ?? '');
  late final _budgetMinController = TextEditingController(text: widget.initial.budgetMin?.toString() ?? '');
  late final _budgetMaxController = TextEditingController(text: widget.initial.budgetMax?.toString() ?? '');
  late final _meetingPointController = TextEditingController(text: widget.initial.meetingPoint ?? '');

  late DateTime _startDate = DateTime.parse(widget.initial.startDate);
  late DateTime _endDate = DateTime.parse(widget.initial.endDate);
  late int _minGroupSize = widget.initial.minGroupSize;
  late int _maxGroupSize = widget.initial.maxGroupSize;
  late bool _approvalRequired = widget.initial.isApprovalRequired;
  late bool _waitlistAllowed = widget.initial.isWaitlistAllowed;

  bool _saving = false;
  String? _errorMessage;

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _budgetMinController.dispose();
    _budgetMaxController.dispose();
    _meetingPointController.dispose();
    super.dispose();
  }

  Future<void> _pickDate({required bool isStart}) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: isStart ? _startDate : _endDate,
      firstDate: now.add(const Duration(days: 1)),
      lastDate: now.add(const Duration(days: 365)),
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        _startDate = picked;
        if (_endDate.isBefore(_startDate)) _endDate = _startDate;
      } else {
        _endDate = picked;
      }
    });
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _errorMessage = null;
    });
    try {
      final request = UpdateTripRequest(
        title: _titleController.text.trim(),
        description: _descriptionController.text.trim(),
        startDate: DateFormat('yyyy-MM-dd').format(_startDate),
        endDate: DateFormat('yyyy-MM-dd').format(_endDate),
        budgetMin: int.tryParse(_budgetMinController.text),
        budgetMax: int.tryParse(_budgetMaxController.text),
        minGroupSize: _minGroupSize,
        maxGroupSize: _maxGroupSize,
        meetingPoint: _meetingPointController.text.trim().isEmpty ? null : _meetingPointController.text.trim(),
        isApprovalRequired: _approvalRequired,
        isWaitlistAllowed: _waitlistAllowed,
      );
      await ref.read(tripApiProvider).update(widget.tripId, request);
      ref.invalidate(tripDetailsProvider(widget.tripId));
      ref.invalidate(myTripsProvider('created'));
      if (!mounted) return;
      context.pop();
    } catch (e) {
      setState(() => _errorMessage = '$e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (_errorMessage != null) ...[
                  Text(_errorMessage!, style: const TextStyle(color: AppColors.error, fontSize: 12)),
                  const SizedBox(height: 10),
                ],
                _SectionCard(
                  title: 'Basics',
                  children: [
                    _FieldLabel('Title'),
                    TextField(controller: _titleController, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500)),
                    const SizedBox(height: 14),
                    _FieldLabel('Description'),
                    TextField(
                      controller: _descriptionController,
                      maxLength: 300,
                      maxLines: 4,
                      decoration: const InputDecoration(counterText: ''),
                      style: const TextStyle(fontSize: 12.5),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                _SectionCard(
                  title: 'Dates',
                  children: [
                    Row(
                      children: [
                        Expanded(child: _DatePickerField(label: 'Start', date: _startDate, onTap: () => _pickDate(isStart: true))),
                        const SizedBox(width: 10),
                        Expanded(child: _DatePickerField(label: 'End', date: _endDate, onTap: () => _pickDate(isStart: false))),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                _SectionCard(
                  title: 'Budget (per person, ₹)',
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _budgetMinController,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(hintText: 'Min'),
                            style: const TextStyle(fontSize: 12.5),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _budgetMaxController,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(hintText: 'Max'),
                            style: const TextStyle(fontSize: 12.5),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                _SectionCard(
                  title: 'Group & logistics',
                  children: [
                    _StepperRow(
                      label: 'Min group size',
                      value: _minGroupSize,
                      onDecrement: _minGroupSize > 1 ? () => setState(() => _minGroupSize--) : null,
                      onIncrement: _minGroupSize < _maxGroupSize ? () => setState(() => _minGroupSize++) : null,
                    ),
                    const SizedBox(height: 10),
                    _StepperRow(
                      label: 'Max group size',
                      value: _maxGroupSize,
                      onDecrement: _maxGroupSize > _minGroupSize ? () => setState(() => _maxGroupSize--) : null,
                      onIncrement: _maxGroupSize < 50 ? () => setState(() => _maxGroupSize++) : null,
                    ),
                    const SizedBox(height: 14),
                    _FieldLabel('Meeting point'),
                    TextField(
                      controller: _meetingPointController,
                      decoration: const InputDecoration(hintText: 'e.g. Kashmere Gate ISBT, Gate 3'),
                      style: const TextStyle(fontSize: 12.5),
                    ),
                    const SizedBox(height: 6),
                    // tileColor pinned to _SectionCard's own surface color —
                    // without it, SwitchListTile logs "ListTile background
                    // color or ink splashes may be invisible" since the
                    // enclosing Container doesn't provide a resolvable
                    // ambient Material color (same fix as edit_profile_screen's
                    // Smoking/Drinking toggles).
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      tileColor: AppColors.surface,
                      title: const Text('Approval required to join', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
                      activeColor: AppColors.primary,
                      value: _approvalRequired,
                      onChanged: (v) => setState(() => _approvalRequired = v),
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      tileColor: AppColors.surface,
                      title: const Text('Allow waitlist once full', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
                      activeColor: AppColors.primary,
                      value: _waitlistAllowed,
                      onChanged: (v) => setState(() => _waitlistAllowed = v),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Save changes'),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.children});

  final String title;
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title.toUpperCase(), style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.textSecondary)),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text(text, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textTertiary)),
    );
  }
}

class _DatePickerField extends StatelessWidget {
  const _DatePickerField({required this.label, required this.date, required this.onTap});

  final String label;
  final DateTime date;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.background,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
            const SizedBox(height: 2),
            Text(DateFormat('d MMM yyyy').format(date), style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }
}

class _StepperRow extends StatelessWidget {
  const _StepperRow({required this.label, required this.value, required this.onDecrement, required this.onIncrement});

  final String label;
  final int value;
  final VoidCallback? onDecrement;
  final VoidCallback? onIncrement;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
        Row(
          children: [
            _RoundStepButton(icon: Icons.remove, onTap: onDecrement),
            SizedBox(width: 40, child: Text('$value', textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
            _RoundStepButton(icon: Icons.add, onTap: onIncrement),
          ],
        ),
      ],
    );
  }
}

class _RoundStepButton extends StatelessWidget {
  const _RoundStepButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 26,
        height: 26,
        decoration: BoxDecoration(color: enabled ? AppColors.background : AppColors.border, shape: BoxShape.circle),
        child: Icon(icon, size: 14, color: enabled ? AppColors.textPrimary : AppColors.textTertiary),
      ),
    );
  }
}
