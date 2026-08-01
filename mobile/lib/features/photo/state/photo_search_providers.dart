import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/providers.dart';
import '../data/photo_search_api.dart';

final photoSearchApiProvider = Provider<PhotoSearchApi>((ref) => PhotoSearchApi(ref.watch(apiClientProvider)));
