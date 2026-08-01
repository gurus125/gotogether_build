# GoTogether — mobile (Flutter)

Single Flutter codebase, both Android and iOS. This folder has the Dart
source but **not** the generated native platform folders (`android/`,
`ios/`) — those must be generated once, locally, with the Flutter SDK you
already have installed (this environment doesn't have Flutter, so it can't
generate them for you).

## One-time setup (run these in order, from inside `mobile/`)

1. **Generate the native platform projects:**
   ```sh
   flutter create --org com.gotogether --project-name gotogether .
   ```
   This adds `android/`, `ios/`, and a few other generated folders around
   the `lib/` source that's already here. Answer "yes" if it asks to
   overwrite `.gitignore` — the one at the repo root already covers Flutter
   build artifacts, and Flutter's own `mobile/.gitignore` is redundant but
   harmless.

2. **Get packages:**
   ```sh
   flutter pub get
   ```

3. **Connect Firebase** (needed before `firebase_messaging` compiles against
   real config — not required just to see the navigation shell run):
   ```sh
   dart pub global activate flutterfire_cli
   flutterfire configure
   ```
   This walks you through picking/creating a Firebase project and writes
   `lib/firebase_options.dart` plus the platform config files
   (`android/app/google-services.json`, `ios/Runner/GoogleService-Info.plist`)
   — both are gitignored at the repo root since they're environment-specific
   and sometimes contain values you don't want in source control.

4. **Configure Google Sign-In natively** (needed before the "Continue with
   Google" button on the Sign-in screen works — not required to see the
   navigation shell or Phone OTP sign-in run):
   - Android: add your app's SHA-1 to the Firebase/Google Cloud project and
     make sure `android/app/google-services.json` (from step 3) is present.
   - iOS: add the `CFBundleURLTypes` entry with your reversed client ID to
     `ios/Runner/Info.plist`, per the `google_sign_in` package's setup docs.
   - No `serverClientId`/`clientId` is hardcoded in Dart — `AuthRepository`
     (`lib/features/auth/data/auth_repository.dart`) constructs a plain
     `GoogleSignIn(scopes: ['email'])` and relies entirely on this native
     configuration.

5. **Run on your emulator:**
   ```sh
   flutter run
   ```
   You should land on the branded splash screen, then Welcome → Sign-in if
   you're not already signed in (Phase 1's real Auth module), or straight
   into the bottom-nav shell if a session is already stored. The shell's five
   tabs (Home / Explore / Create / Chats / Profile) mostly still show
   "coming in Phase X" placeholders — Profile now shows real signed-in-user
   data and a working Edit Profile screen, wired to the backend.

## What's here vs. what's next

- `lib/core/theme/` — design tokens transcribed from the approved design
  system (colors, typography). The color values are best-effort sRGB
  approximations of the source OKLCH values (see the comment in
  `app_colors.dart`) — worth re-verifying against the original design tool
  before final visual polish, not before.
- `lib/core/network/` — the shared `Dio`-based `ApiClient` (attaches the
  access token to every request, transparently refreshes once on a 401) plus
  `TokenStorage` (Keychain/Keystore-backed via `flutter_secure_storage`).
  `AppConfig.apiBaseUrl` picks `10.0.2.2` vs `localhost` automatically for
  Android emulator vs iOS simulator — override with
  `flutter run --dart-define=API_BASE_URL=http://<lan-ip>:8080` for a
  physical device.
- `lib/core/router/` — navigation shell (go_router). Now auth-gated: the
  `redirect` in `app_router.dart` watches `authControllerProvider` and moves
  unauthenticated users to `/welcome`, authenticated users away from the auth
  stack to `/home`.
- `lib/features/auth/` — Phase 1's Auth module: Welcome → Sign-in →
  Google/Phone, wired to `POST /auth/google`, `/auth/phone/otp/request`,
  `/auth/phone/otp/verify`, `/auth/refresh`, `/auth/logout`. Note: the OTP
  code-entry screen (`phone_otp_verify_screen.dart`) isn't in the approved
  "GoTogether Auth Flow" design doc, which ends at "Send code" — it was added
  to make the OTP flow functional at all, reusing that same doc's established
  visual pattern rather than inventing new UI. Flagged for design review.
- `lib/features/profile/` and `lib/features/user/` — Phase 1's Profile/User
  modules: `GET/PATCH /profile/me` and `GET /users/me`, backing the real
  Profile tab and the wired Edit Profile screen. Two assumptions flagged in
  `edit_profile_screen.dart`'s doc comment: the smoking/drinking toggle maps
  to literal `"yes"`/`"no"` strings (the DB column is free TEXT with no
  documented value set), and the "Verification status" card shows the
  aggregate `verificationLevel` only, since the approved design's per-step
  breakdown needs data that belongs to the Verification module (Phase 8).
- `lib/features/*` (home/explore/trip/chat) — still placeholders, built out
  module-by-module per the kickoff roadmap, in lockstep with the matching
  backend module.
- `lib/core/widgets/` — shared widgets (bottom nav shell, placeholder body).

## Backend connection

Point `flutter run` at a locally running backend (`../backend`, see the repo
root README) — `AppConfig.apiBaseUrl` (`lib/core/config/app_config.dart`)
resolves this automatically for the emulator/simulator, see above.
