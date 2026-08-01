import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../data/membership_models.dart';
import '../state/membership_providers.dart';

/// New screen, no approved design mockup backs it (same flagged-gap pattern
/// as `TripPhotosScreen`) — organizer-only, reached once a trip is Completed
/// (from Trip Details' organizer action bar, or the `ATTENDANCE_REMINDER`
/// notification). Exists because "joined" was never the same thing as
/// "actually went," and until this screen there was no way to record the
/// difference: `AttendanceStatus` (ATTENDED/NO_SHOW) and its endpoint already
/// existed in the schema/API, but nothing in the app ever called it, and the
/// Trust Score completion component ignored it even when it was set by hand
/// via a raw API call — see `MembershipCompletionStats`' backend class doc.
///
/// Reuses `rosterProvider`/`GET /trips/{id}/members`, which now includes
/// COMPLETED members (previously JOINED-only, which meant this endpoint
/// returned nothing at all for a trip that had already concluded — see that
/// endpoint's backend doc).
class AttendanceScreen extends ConsumerStatefulWidget {
  const AttendanceScreen({super.key, required this.tripId});

  final String tripId;

  @override
  ConsumerState<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends ConsumerState<AttendanceScreen> {
  String? _updatingUserId;

  Future<void> _mark(String userId, String status) async {
    setState(() => _updatingUserId = userId);
    try {
      await ref.read(membershipApiProvider).markAttendance(widget.tripId, userId, status);
      ref.invalidate(rosterProvider(widget.tripId));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _updatingUserId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final rosterAsync = ref.watch(rosterProvider(widget.tripId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Mark attendance')),
      body: rosterAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load the roster.', style: TextStyle(fontSize: 13, color: AppColors.textSecondary))),
        data: (members) {
          final attendees = members.where((m) => !m.isOrganizer).toList();
          if (attendees.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text('No other travellers on this trip to mark.', textAlign: TextAlign.center, style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: attendees.length + 1,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (context, i) {
              if (i == 0) {
                return const Padding(
                  padding: EdgeInsets.only(bottom: 4),
                  child: Text(
                    'Who actually made it? This feeds Trust Scores — a NO_SHOW no longer counts as a completed trip.',
                    style: TextStyle(fontSize: 11.5, color: AppColors.textSecondary, height: 1.5),
                  ),
                );
              }
              final member = attendees[i - 1];
              return _AttendanceRow(
                member: member,
                updating: _updatingUserId == member.userId,
                onMark: (status) => _mark(member.userId, status),
              );
            },
          );
        },
      ),
    );
  }
}

class _AttendanceRow extends StatelessWidget {
  const _AttendanceRow({required this.member, required this.updating, required this.onMark});

  final RosterMember member;
  final bool updating;
  final void Function(String status) onMark;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.surface, border: Border.all(color: AppColors.border), borderRadius: BorderRadius.circular(14)),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: AppColors.primaryLight,
            backgroundImage: member.photoUrl != null ? NetworkImage(member.photoUrl!) : null,
            child: member.photoUrl == null
                ? Text(member.displayName.isNotEmpty ? member.displayName[0].toUpperCase() : '?',
                    style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w600))
                : null,
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(member.displayName, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500))),
          if (updating)
            const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
          else
            _StatusToggle(current: member.attendanceStatus, onMark: onMark),
        ],
      ),
    );
  }
}

class _StatusToggle extends StatelessWidget {
  const _StatusToggle({required this.current, required this.onMark});

  final String? current;
  final void Function(String status) onMark;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _StatusChip(label: 'Attended', selected: current == 'ATTENDED', color: AppColors.success, onTap: () => onMark('ATTENDED')),
        const SizedBox(width: 6),
        _StatusChip(label: 'No-show', selected: current == 'NO_SHOW', color: AppColors.error, onTap: () => onMark('NO_SHOW')),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.selected, required this.color, required this.onTap});

  final String label;
  final bool selected;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? color.withOpacity(0.12) : Colors.transparent,
          border: Border.all(color: selected ? color : AppColors.border),
          borderRadius: BorderRadius.circular(100),
        ),
        child: Text(label, style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: selected ? color : AppColors.textSecondary)),
      ),
    );
  }
}
