package com.gotogether.common.dto;

import java.util.List;

/** Standard cursor-pagination response shape, paired with {@link CursorPageRequest}. */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor) {
        return new CursorPageResponse<>(items, nextCursor, nextCursor != null);
    }

    public static <T> CursorPageResponse<T> empty() {
        return new CursorPageResponse<>(List.of(), null, false);
    }
}
