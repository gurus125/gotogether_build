package com.gotogether.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Standard cursor-pagination request shape, used everywhere a list endpoint
 * is unbounded (API Specification: "cursor pagination everywhere a list is
 * unbounded"). {@code cursor} is an opaque, module-defined encoding of the
 * last-seen sort key — never a raw offset, so results stay stable while the
 * underlying table is being written to concurrently.
 */
public record CursorPageRequest(String cursor, @Min(1) @Max(100) Integer limit) {

    private static final int DEFAULT_LIMIT = 20;

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
