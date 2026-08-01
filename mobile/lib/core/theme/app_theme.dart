import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

/// The single theme shared by Android and iOS. The approved design was
/// authored against Material widgets (see the Android device frames in the
/// design set) — this renders the same Material look on both platforms
/// rather than switching to Cupertino styling per platform, to stay faithful
/// to the approved UI without a separate design pass. Revisit only after an
/// explicit product decision to diverge iOS visuals from Android.
class AppTheme {
  AppTheme._();

  static ThemeData get light {
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.primary,
        primary: AppColors.primary,
        secondary: AppColors.accent,
        error: AppColors.error,
        surface: AppColors.surface,
        brightness: Brightness.light,
        // Explicit onSurface/onSurfaceVariant/outline overrides — without
        // these, ColorScheme.fromSeed derives its own tonal-palette greys
        // from the (blue) seed color, which came out visibly lighter/more
        // blue-tinted than the design's neutral near-black text. Any widget
        // that reads colorScheme.onSurface/onSurfaceVariant directly (or a
        // TextTheme variant we don't explicitly override below) now falls
        // back to our own measured-contrast tokens instead of M3's guess.
        onSurface: AppColors.textPrimary,
        onSurfaceVariant: AppColors.textSecondary,
        outline: AppColors.border,
      ),
      scaffoldBackgroundColor: AppColors.background,
      fontFamily: GoogleFonts.roboto().fontFamily,
    );

    // Every Material 3 TextTheme variant is explicitly set here — previously
    // only 5 of the 15 variants were overridden, so anything using
    // titleSmall/bodyLarge/labelLarge/labelMedium/labelSmall/displayLarge etc.
    // silently fell back to M3's auto-generated (visibly lighter/more muted)
    // defaults. That mismatch was very likely a real contributor to the
    // "words are very light" report — this pass makes every variant resolve
    // to one of our own measured-contrast AppColors tokens instead.
    final textTheme = base.textTheme.copyWith(
      // TEMPORARY SUBSTITUTION — the design system specifies "Google Sans
      // Text" for headings/titles, but that's not achievable right now:
      // `GoogleFonts.googleSans(...)` doesn't exist in this package
      // version, and `GoogleFonts.getFont('Google Sans Text', ...)`
      // throws "No font family by name 'Google Sans Text' was found" at
      // runtime — confirmed the family itself IS real and servable
      // (fonts.googleapis.com/css2?family=Google+Sans+Text returns a
      // valid @font-face), but the pinned `google_fonts: ^6.2.1` package
      // doesn't have it in its own bundled catalog of known families yet.
      // Falling back to bold/medium Roboto (same family as body text)
      // rather than swapping in some other, never-approved typeface —
      // headings just look less visually distinct than the approved
      // design until this is resolved one of two ways:
      //   1. Bump `google_fonts` in pubspec.yaml to a newer version
      //      (8.2.0+ was available as of this writing) and swap back to
      //      `GoogleFonts.getFont('Google Sans Text', ...)`, or
      //   2. Download the real font file from the URL above and bundle
      //      it as a local asset font in pubspec.yaml instead of relying
      //      on google_fonts at all.
      displayLarge: GoogleFonts.roboto(fontWeight: FontWeight.w700, color: AppColors.textPrimary),
      displayMedium: GoogleFonts.roboto(fontWeight: FontWeight.w700, color: AppColors.textPrimary),
      displaySmall: GoogleFonts.roboto(fontWeight: FontWeight.w700, color: AppColors.textPrimary),
      headlineLarge: GoogleFonts.roboto(fontWeight: FontWeight.w700, color: AppColors.textPrimary, letterSpacing: -0.02),
      headlineMedium: GoogleFonts.roboto(
        fontWeight: FontWeight.w700,
        color: AppColors.textPrimary,
        letterSpacing: -0.02,
      ),
      headlineSmall: GoogleFonts.roboto(fontWeight: FontWeight.w700, color: AppColors.textPrimary),
      titleLarge: GoogleFonts.roboto(
        fontWeight: FontWeight.w700,
        color: AppColors.textPrimary,
      ),
      titleMedium: GoogleFonts.roboto(
        fontWeight: FontWeight.w500,
        color: AppColors.textPrimary,
      ),
      titleSmall: GoogleFonts.roboto(fontWeight: FontWeight.w500, color: AppColors.textPrimary),
      // fontWeight.w500 (Medium), not the implicit w400 (Regular) default —
      // color contrast is already near-max (textPrimary is ~12:1 on white),
      // yet the "words are very light" report persisted after that pass.
      // Regular-weight Roboto at the 10-13px sizes used throughout this app
      // reads as visually thin on a phone screen no matter how dark the hex
      // value is; Medium weight is the remaining lever. Any Text widget with
      // no explicit style falls back to this, so this is the single highest-
      // leverage change available without a per-widget sweep.
      bodyLarge: GoogleFonts.roboto(color: AppColors.textPrimary, fontWeight: FontWeight.w500),
      bodyMedium: GoogleFonts.roboto(color: AppColors.textPrimary, fontWeight: FontWeight.w500),
      bodySmall: GoogleFonts.roboto(color: AppColors.textSecondary, fontWeight: FontWeight.w500),
      labelLarge: GoogleFonts.roboto(fontWeight: FontWeight.w500, color: AppColors.textPrimary),
      labelMedium: GoogleFonts.roboto(fontWeight: FontWeight.w500, color: AppColors.textSecondary),
      labelSmall: GoogleFonts.roboto(fontWeight: FontWeight.w500, color: AppColors.textSecondary),
    );

    return base.copyWith(
      textTheme: textTheme,
      primaryTextTheme: textTheme,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.surface,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        centerTitle: false,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
          textStyle: GoogleFonts.roboto(fontWeight: FontWeight.w500, fontSize: 13.5),
        ),
      ),
      cardTheme: CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.border),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.border),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        // Explicit hint/counter/label/error styles — Flutter's TextField
        // otherwise derives these from Theme.hintColor / a hardcoded
        // bodySmall+onSurfaceVariant combination that isn't guaranteed to
        // route through the AppColors tokens above just because textTheme is
        // set. Every field the user actually types into (Name, Bio, Edit
        // Profile's whole form) was part of the "words are very light"
        // report, so these are pinned directly rather than trusted to inherit.
        hintStyle: GoogleFonts.roboto(color: AppColors.textTertiary),
        counterStyle: GoogleFonts.roboto(color: AppColors.textSecondary, fontSize: 11),
        labelStyle: GoogleFonts.roboto(color: AppColors.textSecondary),
        floatingLabelStyle: GoogleFonts.roboto(color: AppColors.primary),
      ),
    );
  }
}
