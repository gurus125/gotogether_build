import 'package:flutter/material.dart';

/// Color tokens transcribed from the approved design system, which specifies
/// colors in OKLCH (e.g. `oklch(52% 0.18 255)` for primary blue). Flutter has
/// no native OKLCH type, so each token below is an approximate sRGB
/// equivalent — close enough to build and review screens against, but NOT
/// pixel-verified against the original OKLCH values. Before final visual
/// polish, re-derive these from the source OKLCH values with a proper
/// converter (e.g. Chrome DevTools' color picker, which reads/writes OKLCH
/// directly) rather than trusting this approximation.
class AppColors {
  AppColors._();

  // Primary — links, active nav, focus states, trust-related accents.
  static const primary = Color(0xFF3D5FD9); // oklch(52% 0.18 255)
  static const primaryLight = Color(0xFFE3E8FB); // oklch(90% 0.05 255)
  static const primaryTint = Color(0xFFF1F0FC); // oklch(94% 0.05 255)

  // Accent — the ONE accent color, reserved for "Create a trip" CTAs only
  // (design system: "the single most important action").
  static const accent = Color(0xFFE8873D); // oklch(68% 0.17 45)
  static const accentTint = Color(0xFFFBE9D6); // oklch(94% 0.04 45)
  static const accentTextOnTint = Color(0xFF6B4A1E); // oklch(38% 0.12 45)

  // Success / verified — trust badges, completed states.
  static const success = Color(0xFF4CAF7D); // oklch(58% 0.15 145)
  static const successTint = Color(0xFFE4F5EC); // oklch(96% 0.03 145)
  static const successTextOnTint = Color(0xFF2E6E4E); // oklch(35% 0.1 145)

  // Error / danger — log out, emergency help, destructive actions.
  static const error = Color(0xFFE0524A); // oklch(55% 0.19 25)
  static const errorTint = Color(0xFFFBE4E2); // oklch(95% 0.06 25)

  // "Community" tag teal.
  static const communityTint = Color(0xFFE1F1F1); // oklch(93% 0.03 185)
  static const communityText = Color(0xFF2D6B6B); // oklch(35% 0.08 185)

  // Neutrals.
  static const background = Color(0xFFF7F8FA); // oklch(97% 0.004 255)
  static const surface = Color(0xFFFFFFFF);
  static const border = Color(0xFFE4E6EA); // oklch(91% 0.005 255)
  static const textPrimary = Color(0xFF2B2D33); // oklch(20% 0.01 255)

  // textSecondary/textTertiary were darkened from the original transcription
  // (oklch(45%.../55%...) → sRGB gave #6B6E76/#8A8D94) after a user report
  // that body text read as "very light" throughout the app. Measured
  // contrast against white: the original textTertiary was only 3.32:1 —
  // below WCAG AA's 4.5:1 minimum for normal text — despite being used for
  // genuinely-readable content (field labels, timestamps, meta rows) in ~46
  // call sites, not just decorative/disabled text. textSecondary passed AA
  // (5.1:1) but was pushed darker too, to actually match the design mockups'
  // visibly darker medium-grey meta text (destination/date/joined-count
  // rows under trip titles). Verify contrast before darkening further —
  // don't casually push these back toward the original lighter values.
  static const textSecondary = Color(0xFF565A63); // was #6B6E76 (5.1:1) → now ~6.6:1 on white
  static const textTertiary = Color(0xFF6F7278); // was #8A8D94 (3.32:1, failed AA) → now ~4.8:1 on white
}
