import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../../auth/data/auth_models.dart';
import '../../trip/data/trip_models.dart';
import '../data/company_api.dart';
import '../data/company_models.dart';

final companyApiProvider = Provider<CompanyApi>((ref) => CompanyApi(ref.watch(apiClientProvider)));

/// Whether the caller is staff of any Travel Company, and if so its
/// verification status — `null` specifically means "not staff of any
/// company" (a 403 from `GET /companies/me/verification-status`), which is
/// the expected, common case for almost every user, not an error to surface.
/// Any other failure (network, 5xx) rethrows normally so the UI can show a
/// real error state instead of silently treating it as "not a partner."
final myCompanyStatusProvider = FutureProvider.autoDispose<CompanyVerificationStatus?>((ref) async {
  try {
    return await ref.watch(companyApiProvider).getMyVerificationStatus();
  } on ApiException catch (e) {
    if (e.statusCode == 403) return null;
    rethrow;
  }
});

final companyProfileProvider = FutureProvider.autoDispose.family<CompanyProfile, String>((ref, companyId) async {
  return ref.watch(companyApiProvider).getProfile(companyId);
});

final myCompanyStaffProvider = FutureProvider.autoDispose<List<CompanyStaff>>((ref) async {
  return ref.watch(companyApiProvider).listStaff();
});

final myCompanyTripsProvider = FutureProvider.autoDispose<List<TripSummary>>((ref) async {
  final page = await ref.watch(companyApiProvider).getMyTrips();
  return page.items;
});
