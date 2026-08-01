import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/report_api.dart';

final reportApiProvider = Provider<ReportApi>((ref) => ReportApi(ref.watch(apiClientProvider)));
