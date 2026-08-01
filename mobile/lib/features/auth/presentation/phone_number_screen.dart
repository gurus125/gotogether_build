import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../state/auth_controller.dart';

/// "Phone number" state of the approved Auth Flow design. The design fixes
/// the country code to +91 (no picker) — kept as-is rather than adding a
/// country selector that isn't in the approved screen.
class PhoneNumberScreen extends ConsumerStatefulWidget {
  const PhoneNumberScreen({super.key});

  @override
  ConsumerState<PhoneNumberScreen> createState() => _PhoneNumberScreenState();
}

class _PhoneNumberScreenState extends ConsumerState<PhoneNumberScreen> {
  final _controller = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final digits = _controller.text.replaceAll(RegExp(r'\s'), '');
    // Matches the backend's E.164 validation (`PhoneOtpVerifyRequest`):
    // 7–15 digits after the country code.
    if (!RegExp(r'^\d{7,15}$').hasMatch(digits)) {
      setState(() => _error = 'Enter a valid phone number.');
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    final phoneNumber = '+91$digits';
    final ok = await ref.read(authControllerProvider.notifier).requestPhoneOtp(phoneNumber);

    if (!mounted) return;
    setState(() => _submitting = false);

    if (ok) {
      context.push('/auth/phone/verify', extra: phoneNumber);
    } else {
      setState(() => _error = ref.read(authControllerProvider).errorMessage ?? 'Could not send the code. Try again.');
    }
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
                        'Enter your number',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 22),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        "We'll text you a code to confirm it's really you.",
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.6),
                      ),
                      const SizedBox(height: 20),
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
                            decoration: BoxDecoration(
                              border: Border.all(color: AppColors.border),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: const Text('+91', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500)),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: TextField(
                              controller: _controller,
                              keyboardType: TextInputType.phone,
                              inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(15)],
                              decoration: const InputDecoration(hintText: '98765 43210'),
                              style: const TextStyle(fontSize: 12.5),
                            ),
                          ),
                        ],
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 8),
                        Text(_error!, style: const TextStyle(color: AppColors.error, fontSize: 11.5)),
                      ],
                      const SizedBox(height: 20),
                      ElevatedButton(
                        onPressed: _submitting ? null : _sendCode,
                        child: _submitting
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text('Send code'),
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
