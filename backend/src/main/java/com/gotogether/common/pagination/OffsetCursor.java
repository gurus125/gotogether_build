package com.gotogether.common.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Pragmatic MVP cursor implementation: an opaque, base64-encoded offset
 * integer, used together with a stable {@code ORDER BY} (always includes
 * {@code id} as a tiebreaker) to back every unbounded list endpoint (API
 * Specification: "cursor pagination everywhere a list is unbounded").
 *
 * <p>This is deliberately simpler than true keyset pagination (which encodes
 * the last-seen sort key rather than a position) — the API contract only
 * requires the cursor to be an <em>opaque string</em> the client passes back
 * unmodified, and at Delhi-NCR-single-city MVP scale (Chapter 1 Section 14)
 * the dataset is small enough that offset drift under concurrent writes is a
 * theoretical, not practical, concern. Flagged here as a known simplification
 * to revisit (true keyset pagination) if/when result-set sizes grow enough
 * for that drift to matter.
 */
public final class OffsetCursor {

    private OffsetCursor() {
    }

    public static String encode(int nextOffset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(nextOffset).getBytes(StandardCharsets.UTF_8));
    }

    public static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
