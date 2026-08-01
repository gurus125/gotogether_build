import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../state/auth_controller.dart';

/// Code-entry step for the Phone OTP flow.
///
/// NOT in the approved "GoTogether Auth Flow" design doc — that doc ends at
/// "Enter your number" / "Send code" with no follow-up screen shown, which
/// is a gap: Business Rules Module 1 Section 2 requires a 6-digit code
/// confirmation, and the OTP flow cannot function without a place to enter
/// it. Built here reusing the exact visual idiom already established by the
/// Phone-number screen in that same design doc (back arrow, centered
/// title/subtitle, single input, full-width primary pill button) rather than
/// inventing new UI — flagged for the next design review rather than treated
/// as a silent decision.
class PhoneOtpVerifyScreen extends ConsumerStatefulWidget {
  const PhoneOtpVerifyScreen({super.key, required this.phoneNumber});

  final String phoneNumber;

  @override
  ConsumerState<PhoneOtpVerifyScreen> createState() => _PhoneOtpVerifyScreenState();
}

class _PhoneOtpVerifyScreenState extends ConsumerState<PhoneOtpVerifyScreen> {
  final _controller = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final code = _controller.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      setState(() => _error = 'Enter the 6-digit code.');
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    final ok = await ref.read(authControllerProvider.notifier).verifyPhoneOtp(widget.phoneNumber, code);

    if (!mounted) return;
    setState(() => _submitting = false);
    if (!ok) {
      setState(() => _error = ref.read(authControllerProvider).errorMessage ?? 'Invalid or expired code.');
    }
    // On success, the router's auth-state redirect takes over navigation.
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
              child: GestureDetector(
                onTap: () => context.pop(),
                child: Container(
                  width: 32,
                  height: 32,
                  decoration: const BoxDecoration(color: AppColors.background, shape: BoxShape.circle),
                  child: const Icon(Icons.arrow_back, size: 16),
                ),
              ),
            ),
            Expanded(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        'Enter the code',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 22),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'We sent a 6-digit code to ${widget.phoneNumber}.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.6),
                      ),
                      const SizedBox(height: 20),
                      TextField(
                        controller: _controller,
                        keyboardType: TextInputType.number,
                        textAlign: TextAlign.center,
                        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                        decoration: const InputDecoration(hintText: '••••••'),
                        style: const TextStyle(fontSize: 18, letterSpacing: 6, fontWeight: FontWeight.w600),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 8),
                        Text(_error!, style: const TextStyle(color: AppColors.error, fontSize: 11.5)),
                      ],
                      const SizedBox(height: 20),
                      ElevatedButton(
                        onPressed: _submitting ? null : _verify,
                        child: _submitting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text('Verify'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
