import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/splash/splash_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/explore/explore_screen.dart';
import '../../features/trip/create_trip_screen.dart';
import '../../features/chat/chat_list_screen.dart';
import '../../features/profile/profile_screen.dart';
import '../../features/profile/presentation/edit_profile_screen.dart';
import '../../features/trip/presentation/trip_details_screen.dart';
import '../../features/photo/presentation/photo_search_screen.dart';
import '../../features/trip/presentation/trip_photos_screen.dart';
import '../../features/trip/presentation/edit_trip_screen.dart';
import '../../features/help/data/help_content.dart';
import '../../features/help/presentation/help_support_screen.dart';
import '../../features/help/presentation/help_article_screen.dart';
import '../../features/trip/presentation/my_trips_screen.dart';
import '../../features/membership/presentation/roster_screen.dart';
import '../../features/membership/presentation/attendance_screen.dart';
import '../../features/joinrequest/presentation/organizer_requests_screen.dart';
import '../../features/chat/presentation/trip_chat_screen.dart';
import '../../features/review/presentation/submit_review_screen.dart';
import '../../features/review/presentation/trip_reviewees_screen.dart';
import '../../features/trust/presentation/trust_reviews_screen.dart';
import '../../features/trust/presentation/trust_tips_screen.dart';
import '../../features/notification/presentation/notifications_screen.dart';
import '../../features/company/presentation/company_apply_screen.dart';
import '../../features/company/presentation/company_dashboard_screen.dart';
import '../../features/company/presentation/company_profile_screen.dart';
import '../../features/auth/presentation/welcome_screen.dart';
import '../../features/auth/presentation/sign_in_screen.dart';
import '../../features/auth/presentation/phone_number_screen.dart';
import '../../features/auth/presentation/phone_otp_verify_screen.dart';
import '../../features/auth/state/auth_controller.dart';
import '../../features/auth/state/auth_state.dart';
import '../widgets/bottom_nav_shell.dart';

/// Every route that exists before/instead of sign-in — used by the redirect
/// logic below to decide whether the user is "in the auth stack".
const _authRoutePrefixes = ['/splash', '/welcome', '/auth'];

/// Bridges `authControllerProvider`'s Riverpod state into a `Listenable`, so
/// GoRouter's `refreshListenable` re-evaluates `redirect` on every auth
/// transition without this provider having to rebuild (and reset) the whole
/// `GoRouter` instance itself.
class _AuthRefreshNotifier extends ChangeNotifier {
  _AuthRefreshNotifier(Ref ref) {
    ref.listen<AuthState>(authControllerProvider, (previous, next) {
      if (previous?.status != next.status) notifyListeners();
    });
  }
}

/// Root navigation graph.
///
/// Phase 1 adds the real Auth stack (Splash → Welcome → Sign-in →
/// Google/Phone) and the Edit Profile screen, both wired to the backend via
/// `authControllerProvider`. Auth gating: unauthenticated users are redirected
/// to `/welcome` if they land anywhere in the tab shell; authenticated users
/// are redirected to `/home` if they land anywhere in the auth stack.
final appRouterProvider = Provider<GoRouter>((ref) {
  final refreshNotifier = _AuthRefreshNotifier(ref);
  ref.onDispose(refreshNotifier.dispose);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: refreshNotifier,
    redirect: (context, state) {
      final status = ref.read(authControllerProvider).status;
      final location = state.matchedLocation;

      if (status == AuthStatus.unknown) {
        // Still checking secure storage for a stored session — hold on
        // splash rather than flashing Welcome/Home first.
        return location == '/splash' ? null : '/splash';
      }

      // Splash's only job is to hold the screen during the `unknown` state
      // above — once resolved, it must always move on, whichever way.
      if (location == '/splash') {
        return status == AuthStatus.authenticated ? '/home' : '/welcome';
      }

      final onAuthRoute = _authRoutePrefixes.any((prefix) => location.startsWith(prefix));
      if (status != AuthStatus.authenticated) {
        return onAuthRoute ? null : '/welcome';
      }
      // Authenticated: keep the user out of the auth stack once a session
      // is confirmed (e.g. backgrounding mid-sign-in then resuming).
      return onAuthRoute ? '/home' : null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
      GoRoute(path: '/welcome', builder: (context, state) => const WelcomeScreen()),
      GoRoute(path: '/auth/sign-in', builder: (context, state) => const SignInScreen()),
      GoRoute(path: '/auth/phone', builder: (context, state) => const PhoneNumberScreen()),
      GoRoute(
        path: '/auth/phone/verify',
        builder: (context, state) => PhoneOtpVerifyScreen(phoneNumber: state.extra as String),
      ),
      GoRoute(path: '/profile/edit', builder: (context, state) => const EditProfileScreen()),
      GoRoute(path: '/profile/trust-tips', builder: (context, state) => const TrustTipsScreen()),
      GoRoute(
        path: '/trip/:id',
        builder: (context, state) => TripDetailsScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/members',
        builder: (context, state) => RosterScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/requests',
        builder: (context, state) => OrganizerRequestsScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/chat',
        builder: (context, state) => TripChatScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/photos',
        builder: (context, state) => TripPhotosScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/photos/search',
        builder: (context, state) =>
            PhotoSearchScreen(tripId: state.pathParameters['id']!, isFirstPhoto: state.extra as bool? ?? false),
      ),
      GoRoute(
        path: '/trip/:id/edit',
        builder: (context, state) => EditTripScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(path: '/help', builder: (context, state) => const HelpSupportScreen()),
      GoRoute(
        path: '/help/article',
        builder: (context, state) => HelpArticleScreen(article: state.extra as HelpArticle),
      ),
      GoRoute(
        path: '/trip/:id/attendance',
        builder: (context, state) => AttendanceScreen(tripId: state.pathParameters['id']!),
      ),
      GoRoute(
        path: '/trip/:id/reviewees',
        builder: (context, state) => TripRevieweesScreen(tripId: state.pathParameters['id']!, tripTitle: state.extra as String? ?? 'this trip'),
      ),
      GoRoute(
        path: '/trip/:id/reviews/:revieweeId',
        builder: (context, state) => SubmitReviewScreen(
          tripId: state.pathParameters['id']!,
          revieweeId: state.pathParameters['revieweeId']!,
          revieweeName: state.extra as String? ?? 'this traveller',
        ),
      ),
      GoRoute(
        path: '/users/:id/trust-reviews',
        builder: (context, state) => TrustReviewsScreen(userId: state.pathParameters['id']!, displayName: state.extra as String? ?? 'Traveller'),
      ),
      GoRoute(path: '/my-trips', builder: (context, state) => const MyTripsScreen()),
      GoRoute(path: '/notifications', builder: (context, state) => const NotificationsScreen()),
      GoRoute(path: '/companies/apply', builder: (context, state) => const CompanyApplyScreen()),
      GoRoute(path: '/companies/me', builder: (context, state) => const CompanyDashboardScreen()),
      GoRoute(
        path: '/companies/:id',
        builder: (context, state) => CompanyProfileScreen(companyId: state.pathParameters['id']!, fallbackName: state.extra as String?),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => BottomNavShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/home', builder: (context, state) => const HomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/explore',
              builder: (context, state) => ExploreScreen(initialArgs: state.extra as ExploreSearchArgs?),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/create', builder: (context, state) => const CreateTripScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/chats', builder: (context, state) => const ChatListScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/profile', builder: (context, state) => const ProfileScreen()),
          ]),
        ],
      ),
    ],
  );
});
