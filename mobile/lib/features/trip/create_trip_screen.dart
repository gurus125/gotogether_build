import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_colors.dart';
import '../destination/data/destination_models.dart';
import '../destination/state/destination_providers.dart';
import 'state/create_trip_controller.dart';
import 'state/create_trip_state.dart';

/// The approved "Create Trip Flow" design's 7-step Quick Publish wizard
/// (Destination, Departure-locked, Dates, Budget, Title, Description,
/// Review & Publish), wired to `POST /trips` + `POST /trips/{id}/publish`.
class CreateTripScreen extends ConsumerWidget {
  const CreateTripScreen({super.key});

  static const _stepTitles = ['', 'Destination', 'Departure', 'Dates', 'Budget', 'Title', 'Description', 'Review'];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);

    if (state.submitStatus == CreateTripSubmitStatus.success) {
      return _SuccessView(tripId: state.createdTripId!, onCreateAnother: controller.restart);
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      GestureDetector(
                        onTap: state.step > 1 ? controller.goBack : () => context.go('/home'),
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: const BoxDecoration(color: AppColors.surface, shape: BoxShape.circle),
                          child: const Icon(Icons.arrow_back, size: 16),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Step ${state.step} of 7 · ${_stepTitles[state.step]}',
                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textSecondary),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: List.generate(7, (i) {
                      final active = i < state.step;
                      return Expanded(
                        child: Container(
                          height: 4,
                          margin: EdgeInsets.only(right: i < 6 ? 4 : 0),
                          decoration: BoxDecoration(
                            color: active ? AppColors.primary : AppColors.border,
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      );
                    }),
                  ),
                ],
              ),
            ),
            if (state.errorMessage != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(state.errorMessage!, style: const TextStyle(color: AppColors.error, fontSize: 12)),
              ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                child: switch (state.step) {
                  1 => const _DestinationStep(),
                  2 => const _DepartureStep(),
                  3 => const _DatesStep(),
                  4 => const _BudgetStep(),
                  5 => const _TitleStep(),
                  6 => const _DescriptionStep(),
                  _ => const _ReviewStep(),
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: state.requiredMetForCurrentStep ? AppColors.accent : AppColors.border,
                    foregroundColor: state.requiredMetForCurrentStep ? Colors.white : AppColors.textTertiary,
                  ),
                  onPressed: state.submitStatus == CreateTripSubmitStatus.submitting
                      ? null
                      : (state.requiredMetForCurrentStep ? controller.goNextOrSubmit : null),
                  child: state.submitStatus == CreateTripSubmitStatus.submitting
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : Text(state.step == 7 ? 'Publish trip' : 'Continue'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StepHeading extends StatelessWidget {
  const _StepHeading({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 20)),
          const SizedBox(height: 4),
          Text(subtitle, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
        decoration: BoxDecoration(
          color: selected ? AppColors.primaryLight : Colors.transparent,
          border: selected ? null : Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(100),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12.5,
            fontWeight: FontWeight.w500,
            color: selected ? AppColors.primary : AppColors.textPrimary,
          ),
        ),
      ),
    );
  }
}

// --- Step 1: Destination ----------------------------------------------------

class _DestinationStep extends ConsumerStatefulWidget {
  const _DestinationStep();

  @override
  ConsumerState<_DestinationStep> createState() => _DestinationStepState();
}

class _DestinationStepState extends ConsumerState<_DestinationStep> {
  late final _controller = TextEditingController(text: ref.read(createTripControllerProvider).destinationQuery);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);
    final query = state.destinationQuery;
    final isSearching = query.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: "Where's your next adventure?", subtitle: "Pick a destination and we'll find your people."),
        Container(
          height: 46,
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
                  controller: _controller,
                  onChanged: controller.setDestinationQuery,
                  decoration: const InputDecoration(
                    hintText: 'Search Manali, Goa, Spiti...',
                    border: InputBorder.none,
                    filled: false,
                    contentPadding: EdgeInsets.zero,
                  ),
                  style: const TextStyle(fontSize: 13),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (isSearching)
          _DestinationSearchResults(query: query)
        else
          const _DestinationBrowse(),
      ],
    );
  }
}

class _DestinationSearchResults extends ConsumerWidget {
  const _DestinationSearchResults({required this.query});

  final String query;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final allDestinations = ref.watch(allDestinationsProvider);
    final controller = ref.read(createTripControllerProvider.notifier);

    return allDestinations.when(
      loading: () => const Padding(padding: EdgeInsets.only(top: 16), child: Center(child: CircularProgressIndicator())),
      error: (e, _) => Text('Could not load destinations.', style: const TextStyle(color: AppColors.error, fontSize: 12)),
      data: (destinations) {
        final matches = destinations.where((d) => d.name.toLowerCase().contains(query.toLowerCase())).toList();
        if (matches.isEmpty) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Text('No matches — try Manali, Goa, Spiti...', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
          );
        }
        return Column(
          children: matches
              .map((d) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(d.name, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                    trailing: Text(_categoryLabel(d.category), style: const TextStyle(fontSize: 10.5, color: AppColors.textTertiary)),
                    onTap: () => controller.selectDestination(d),
                  ))
              .toList(),
        );
      },
    );
  }
}

class _DestinationBrowse extends ConsumerWidget {
  const _DestinationBrowse();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final popular = ref.watch(popularDestinationsProvider);
    final all = ref.watch(allDestinationsProvider);
    final controller = ref.read(createTripControllerProvider.notifier);
    final selectedId = ref.watch(createTripControllerProvider).destination?.id;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('🔥 POPULAR FROM DELHI NCR', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
        const SizedBox(height: 8),
        popular.when(
          loading: () => const SizedBox(height: 36, child: Center(child: CircularProgressIndicator(strokeWidth: 2))),
          error: (e, _) => const SizedBox.shrink(),
          data: (destinations) => SizedBox(
            height: 40,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: destinations.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, i) {
                final d = destinations[i];
                return _Chip(label: d.name, selected: d.id == selectedId, onTap: () => controller.selectDestination(d));
              },
            ),
          ),
        ),
        const SizedBox(height: 18),
        all.when(
          loading: () => const Padding(padding: EdgeInsets.only(top: 16), child: Center(child: CircularProgressIndicator())),
          error: (e, _) => const Text('Could not load destinations.', style: TextStyle(color: AppColors.error, fontSize: 12)),
          data: (destinations) {
            final byCategory = <String, List<DestinationSummary>>{};
            for (final d in destinations) {
              byCategory.putIfAbsent(d.category, () => []).add(d);
            }
            const order = ['MOUNTAINS', 'BEACHES', 'WEEKEND_ESCAPES', 'ADVENTURE'];
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: order.where(byCategory.containsKey).map((category) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${_categoryEmoji(category)} ${_categoryLabel(category)}',
                          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: byCategory[category]!
                            .map((d) => _Chip(label: d.name, selected: d.id == selectedId, onTap: () => controller.selectDestination(d)))
                            .toList(),
                      ),
                    ],
                  ),
                );
              }).toList(),
            );
          },
        ),
        Container(
          margin: const EdgeInsets.only(top: 4),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(color: AppColors.primaryTint, borderRadius: BorderRadius.circular(14)),
          child: const Row(
            children: [
              Icon(Icons.location_on_outlined, size: 16, color: AppColors.primary),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Trips currently start from Delhi NCR. More departure cities coming soon.',
                  style: TextStyle(fontSize: 11, color: AppColors.primary, height: 1.5),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

String _categoryLabel(String category) => switch (category) {
      'MOUNTAINS' => 'Mountains',
      'BEACHES' => 'Beaches',
      'WEEKEND_ESCAPES' => 'Weekend Escapes',
      'ADVENTURE' => 'Adventure',
      _ => category,
    };

String _categoryEmoji(String category) => switch (category) {
      'MOUNTAINS' => '🏔️',
      'BEACHES' => '🏖️',
      'WEEKEND_ESCAPES' => '🌿',
      'ADVENTURE' => '🏕️',
      _ => '',
    };

// --- Step 2: Departure (locked) ---------------------------------------------

class _DepartureStep extends StatelessWidget {
  const _DepartureStep();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(
          title: 'Where are you starting from?',
          subtitle: 'GoTogether is currently available for trips starting from Delhi NCR.',
        ),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            border: Border.all(color: AppColors.border),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(color: AppColors.communityTint, shape: BoxShape.circle),
                child: const Icon(Icons.location_on, size: 18, color: AppColors.communityText),
              ),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Delhi NCR', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                    Text('Departure city', style: TextStyle(fontSize: 10.5, color: AppColors.textTertiary)),
                  ],
                ),
              ),
              const Icon(Icons.lock_outline, size: 18, color: AppColors.textTertiary),
            ],
          ),
        ),
        const SizedBox(height: 10),
        const Text(
          'Currently available for trips starting from Delhi NCR.',
          style: TextStyle(fontSize: 10.5, color: AppColors.textTertiary),
        ),
      ],
    );
  }
}

// --- Step 3: Dates -----------------------------------------------------------

class _DatesStep extends ConsumerWidget {
  const _DatesStep();

  static const _months = ['Sep', 'Oct', 'Nov', 'Dec'];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: 'When are you thinking?', subtitle: 'Flexible dates get 3x more join requests.'),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: AppColors.surface,
            border: Border.all(color: AppColors.border),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Trip length', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
              Row(
                children: [
                  _RoundIconButton(icon: Icons.remove, onTap: controller.decrementDays),
                  SizedBox(width: 56, child: Text('${state.days} days', textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
                  _RoundIconButton(icon: Icons.add, onTap: controller.incrementDays),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(3),
          decoration: BoxDecoration(color: AppColors.background, borderRadius: BorderRadius.circular(100)),
          child: Row(
            children: [
              Expanded(child: _SegmentButton(label: 'Flexible', selected: state.isFlexible, onTap: () => controller.setDateMode('flexible'))),
              Expanded(child: _SegmentButton(label: 'Fixed dates', selected: state.isFixed, onTap: () => controller.setDateMode('fixed'))),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (state.isFlexible) ...[
          const Text('WHICH MONTHS?', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.textSecondary)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _months.map((m) => _Chip(label: m, selected: state.months.contains(m), onTap: () => controller.toggleMonth(m))).toList(),
          ),
        ] else
          _FixedDatePicker(selected: state.fixedStartDate, onPicked: controller.setFixedStartDate),
      ],
    );
  }
}

class _RoundIconButton extends StatelessWidget {
  const _RoundIconButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 26,
        height: 26,
        decoration: const BoxDecoration(color: AppColors.background, shape: BoxShape.circle),
        child: Icon(icon, size: 14),
      ),
    );
  }
}

class _SegmentButton extends StatelessWidget {
  const _SegmentButton({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: BoxDecoration(
          color: selected ? AppColors.surface : Colors.transparent,
          borderRadius: BorderRadius.circular(100),
        ),
        alignment: Alignment.center,
        child: Text(label, style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: selected ? AppColors.textPrimary : AppColors.textSecondary)),
      ),
    );
  }
}

/// The approved design uses a static, non-navigable single-month calendar
/// grid here. That's fine for a mockup but would trap a real user unable to
/// pick a date beyond one hardcoded month — this uses Flutter's native date
/// picker instead (still presented inside the same card chrome as the rest
/// of the wizard), a deliberate, flagged deviation for usability/correctness.
class _FixedDatePicker extends StatelessWidget {
  const _FixedDatePicker({required this.selected, required this.onPicked});

  final DateTime? selected;
  final ValueChanged<DateTime> onPicked;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () async {
        final now = DateTime.now();
        final picked = await showDatePicker(
          context: context,
          initialDate: selected ?? now.add(const Duration(days: 14)),
          firstDate: now.add(const Duration(days: 1)),
          lastDate: now.add(const Duration(days: 365)),
        );
        if (picked != null) onPicked(picked);
      },
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              selected != null ? DateFormat('EEE, d MMM yyyy').format(selected!) : 'Pick a start date',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: selected != null ? AppColors.textPrimary : AppColors.textTertiary),
            ),
            const Icon(Icons.calendar_today_outlined, size: 16, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }
}

// --- Step 4: Budget ----------------------------------------------------------

class _BudgetStep extends ConsumerStatefulWidget {
  const _BudgetStep();

  @override
  ConsumerState<_BudgetStep> createState() => _BudgetStepState();
}

class _BudgetStepState extends ConsumerState<_BudgetStep> {
  late final _minController = TextEditingController(text: ref.read(createTripControllerProvider).customMin);
  late final _maxController = TextEditingController(text: ref.read(createTripControllerProvider).customMax);

  @override
  void dispose() {
    _minController.dispose();
    _maxController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);
    const options = [
      ('5-10', '₹5k–10k'),
      ('10-15', '₹10k–15k'),
      ('15-20', '₹15k–20k'),
      ('20-plus', '₹20k+'),
      ('custom', 'Custom'),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: 'Estimated budget', subtitle: 'Per person, all-in estimate.'),
        ...options.map((o) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: GestureDetector(
                onTap: () => controller.selectBudget(o.$1),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: state.budgetKey == o.$1 ? AppColors.primaryLight : AppColors.surface,
                    border: state.budgetKey == o.$1 ? null : Border.all(color: AppColors.border),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Text(o.$2,
                      style: TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.w500,
                          color: state.budgetKey == o.$1 ? AppColors.primary : AppColors.textPrimary)),
                ),
              ),
            )),
        if (state.budgetKey == 'custom')
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _minController,
                    keyboardType: TextInputType.number,
                    onChanged: controller.setCustomMin,
                    decoration: const InputDecoration(hintText: 'Min ₹'),
                    style: const TextStyle(fontSize: 12.5),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: _maxController,
                    keyboardType: TextInputType.number,
                    onChanged: controller.setCustomMax,
                    decoration: const InputDecoration(hintText: 'Max ₹'),
                    style: const TextStyle(fontSize: 12.5),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

// --- Step 5: Title ------------------------------------------------------------

class _TitleStep extends ConsumerStatefulWidget {
  const _TitleStep();

  @override
  ConsumerState<_TitleStep> createState() => _TitleStepState();
}

class _TitleStepState extends ConsumerState<_TitleStep> {
  late final _controller = TextEditingController(text: ref.read(createTripControllerProvider).title);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: 'Give it a name', subtitle: 'A short, exciting title works best.'),
        TextField(
          controller: _controller,
          onChanged: controller.setTitle,
          decoration: InputDecoration(hintText: state.suggestedTitle),
          style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: 10),
        GestureDetector(
          onTap: () {
            controller.useSuggestedTitle();
            _controller.text = state.suggestedTitle;
          },
          child: Text('Use "${state.suggestedTitle}"',
              style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.primary)),
        ),
      ],
    );
  }
}

// --- Step 6: Description -------------------------------------------------------

class _DescriptionStep extends ConsumerStatefulWidget {
  const _DescriptionStep();

  @override
  ConsumerState<_DescriptionStep> createState() => _DescriptionStepState();
}

class _DescriptionStepState extends ConsumerState<_DescriptionStep> {
  late final _controller = TextEditingController(text: ref.read(createTripControllerProvider).description);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: 'Short description', subtitle: 'What are you planning, and who should join?'),
        TextField(
          controller: _controller,
          onChanged: controller.setDescription,
          maxLength: 300,
          maxLines: 5,
          decoration: InputDecoration(
            hintText: 'e.g. Planning a relaxed 5-day trip to Manali with cafe-hopping and short hikes. '
                'Looking for 3-4 chill travellers, budget-conscious, okay with shared stays.',
            counterText: '',
          ),
          style: const TextStyle(fontSize: 12.5),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            GestureDetector(
              onTap: () {
                controller.useSuggestedDescription();
                _controller.text = state.suggestedDescription;
              },
              child: const Text('✨ Use suggested description',
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w500, color: AppColors.primary)),
            ),
            Text('${state.description.length}/300', style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
          ],
        ),
      ],
    );
  }
}

// --- Step 7: Review & Publish ---------------------------------------------------

class _ReviewStep extends ConsumerWidget {
  const _ReviewStep();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(createTripControllerProvider);
    final controller = ref.read(createTripControllerProvider.notifier);
    const budgetLabels = {'5-10': '₹5k–10k', '10-15': '₹10k–15k', '15-20': '₹15k–20k', '20-plus': '₹20k+'};
    final budgetDisplay = state.budgetKey == 'custom'
        ? '₹${state.customMin ?? '?'}–${state.customMax ?? '?'}'
        : (budgetLabels[state.budgetKey] ?? 'Not set');
    final datesDisplay = state.isFlexible
        ? '${state.months.isEmpty ? 'Flexible' : state.months.join(', ')} · ${state.days} days'
        : 'Fixed dates · ${state.days} days';

    final rows = [
      ('DESTINATION', state.destination?.name ?? '—', () => controller.goToStep(1)),
      ('DEPARTURE', 'Delhi NCR', () => controller.goToStep(2)),
      ('DATES', datesDisplay, () => controller.goToStep(3)),
      ('BUDGET', budgetDisplay, () => controller.goToStep(4)),
      ('TITLE', state.effectiveTitle, () => controller.goToStep(5)),
      ('DESCRIPTION', state.description.isEmpty ? 'Not added' : state.description, () => controller.goToStep(6)),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _StepHeading(title: 'Review & publish', subtitle: "Everything looks good? Let's get you travellers."),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surface,
            border: Border.all(color: AppColors.border),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            children: rows.asMap().entries.map((entry) {
              final (label, value, onEdit) = entry.value;
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                decoration: BoxDecoration(
                  border: entry.key < rows.length - 1 ? const Border(bottom: BorderSide(color: AppColors.background)) : null,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(label, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                          const SizedBox(height: 2),
                          Text(value, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500), maxLines: 2, overflow: TextOverflow.ellipsis),
                        ],
                      ),
                    ),
                    GestureDetector(
                      onTap: onEdit,
                      child: const Text('Edit', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: AppColors.primary)),
                    ),
                  ],
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: AppColors.primaryTint, borderRadius: BorderRadius.circular(14)),
          child: const Text(
            'Group size, meeting point, approval settings and photos can be added right after publishing — no need to fill everything now.',
            style: TextStyle(fontSize: 11, color: AppColors.primary, height: 1.6),
          ),
        ),
      ],
    );
  }
}

// --- Success ---------------------------------------------------------------

class _SuccessView extends StatelessWidget {
  const _SuccessView({required this.tripId, required this.onCreateAnother});

  final String tripId;
  final VoidCallback onCreateAnother;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: const BoxDecoration(color: AppColors.successTint, shape: BoxShape.circle),
                child: const Icon(Icons.check, color: AppColors.success, size: 28),
              ),
              const SizedBox(height: 18),
              Text('Your trip is now live!', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 20)),
              const SizedBox(height: 8),
              const Text(
                'Travellers can discover your trip · Join requests will appear here · A group chat unlocks once members are approved.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.7),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => context.push('/trip/$tripId'),
                  child: const Text('View trip'),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: () => context.go('/home'),
                  child: const Text('Back to Home'),
                ),
              ),
              const SizedBox(height: 12),
              GestureDetector(
                onTap: onCreateAnother,
                child: const Text('Create another trip', style: TextStyle(fontSize: 11, color: AppColors.textTertiary)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
