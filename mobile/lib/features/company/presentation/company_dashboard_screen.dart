import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../trip/data/trip_models.dart';
import '../../trip/presentation/widgets/trip_card.dart';
import '../data/company_models.dart';
import '../state/company_providers.dart';

/// "My Company" — a functional-only dashboard (no design mockup exists for
/// this anywhere in the approved design set, same gap as Admin Panel/company
/// registration): verification status, staff, and this Company's own trips.
///
/// Deliberately does NOT include a "create trip" action — building a second,
/// company-specific trip-creation flow with no mockup to match, on top of an
/// already-flagged gap, would compound an unapproved guess with another one.
/// `POST /trips` already supports Verified Partner Trips (a `companyId` +
/// `fixedPrice` pair) end-to-end on the backend; wiring a Flutter entry point
/// for it is left for when this screen's design is actually approved.
class CompanyDashboardScreen extends ConsumerWidget {
  const CompanyDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync = ref.watch(myCompanyStatusProvider);
    final staffAsync = ref.watch(myCompanyStaffProvider);
    final tripsAsync = ref.watch(myCompanyTripsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('My Company')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myCompanyStatusProvider);
          ref.invalidate(myCompanyStaffProvider);
          ref.invalidate(myCompanyTripsProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _StatusCard(statusAsync: statusAsync),
            const SizedBox(height: 12),
            _StaffCard(staffAsync: staffAsync),
            const SizedBox(height: 12),
            _TripsCard(tripsAsync: tripsAsync),
          ],
        ),
      ),
    );
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

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.statusAsync});

  final AsyncValue<CompanyVerificationStatus?> statusAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: statusAsync.when(
        loading: () => const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: LinearProgressIndicator()),
        error: (e, _) => const Text('Could not load your company status.', style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary)),
        data: (status) {
          if (status == null) {
            return const Text('You are not staff of any Travel Company.', style: TextStyle(fontSize: 12.5, color: AppColors.textSecondary));
          }
          final (label, tint, text) = switch (status.status) {
            'APPROVED' => ('Verified Partner', AppColors.successTint, AppColors.successTextOnTint),
            'REJECTED' => ('Application rejected', AppColors.errorTint, AppColors.error),
            _ => ('Under review', AppColors.primaryTint, AppColors.primary),
          };
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Verification status', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(color: tint, borderRadius: BorderRadius.circular(100)),
                child: Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: text)),
              ),
              if (status.decisionNotes != null) ...[
                const SizedBox(height: 8),
                Text(status.decisionNotes!, style: const TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.5)),
              ],
              if (status.status != 'APPROVED') ...[
                const SizedBox(height: 8),
                const Text(
                  'A Moderator reviews every application manually — there\'s no automatic approval.',
                  style: TextStyle(fontSize: 10.5, color: AppColors.textTertiary, height: 1.5),
                ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _StaffCard extends ConsumerWidget {
  const _StaffCard({required this.staffAsync});

  final AsyncValue<List<CompanyStaff>> staffAsync;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Staff', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
              GestureDetector(
                onTap: () => _showInviteDialog(context, ref),
                child: const Text('+ Invite', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: AppColors.primary)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          staffAsync.when(
            loading: () => const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2))),
            error: (e, _) => const Text('Could not load staff.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
            data: (staff) => staff.isEmpty
                ? const Text('No staff yet.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary))
                : Column(
                    children: staff
                        .map((s) => Padding(
                              padding: const EdgeInsets.symmetric(vertical: 4),
                              child: Row(
                                children: [
                                  Expanded(child: Text(s.userId, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11.5))),
                                  Text(s.role, style: const TextStyle(fontSize: 10.5, color: AppColors.textSecondary)),
                                ],
                              ),
                            ))
                        .toList(),
                  ),
          ),
          // Invite-only-owner-role is rejected server-side (409
          // MULTI_ADMIN_NOT_ENABLED) — the dialog below only offers
          // manager/support, so that path is never reachable from this UI.
        ],
      ),
    );
  }

  Future<void> _showInviteDialog(BuildContext context, WidgetRef ref) async {
    final userIdController = TextEditingController();
    String role = 'manager';
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Invite staff'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: userIdController, decoration: const InputDecoration(hintText: 'User ID')),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: RadioListTile<String>(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: const Text('Manager', style: TextStyle(fontSize: 12)),
                      value: 'manager',
                      groupValue: role,
                      onChanged: (v) => setState(() => role = v!),
                    ),
                  ),
                  Expanded(
                    child: RadioListTile<String>(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: const Text('Support', style: TextStyle(fontSize: 12)),
                      value: 'support',
                      groupValue: role,
                      onChanged: (v) => setState(() => role = v!),
                    ),
                  ),
                ],
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Invite')),
          ],
        ),
      ),
    );
    if (result != true || userIdController.text.trim().isEmpty) return;
    try {
      await ref.read(companyApiProvider).inviteStaff(userIdController.text.trim(), role);
      ref.invalidate(myCompanyStaffProvider);
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }
}

class _TripsCard extends StatelessWidget {
  const _TripsCard({required this.tripsAsync});

  final AsyncValue<List<TripSummary>> tripsAsync;

  @override
  Widget build(BuildContext context) {
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Our trips', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),
          tripsAsync.when(
            loading: () => const Center(child: Padding(padding: EdgeInsets.all(8), child: CircularProgressIndicator(strokeWidth: 2))),
            error: (e, _) => const Text('Could not load trips.', style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary)),
            data: (trips) => trips.isEmpty
                ? const Text(
                    'No trips yet — Verified Partner Trip creation isn\'t wired into the app yet (backend supports it; see this screen\'s class doc).',
                    style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.5),
                  )
                : SizedBox(
                    height: 190,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: trips.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 10),
                      itemBuilder: (context, i) => TripCard(trip: trips[i], partner: true, width: 180),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
